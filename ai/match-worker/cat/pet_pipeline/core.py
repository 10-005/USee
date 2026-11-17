"""Evaluated original + Letterbox pipeline; features stay in memory."""
from __future__ import annotations

from dataclasses import dataclass, field
import json
from pathlib import Path
import time

import numpy as np
from PIL import Image, ImageOps

from .assets import ROOT, verify
from .runtime import CudaSession, DEVICE, initialize_gpu, install_vendor_path

@dataclass(frozen=True)
class PipelineConfig:
    """Frozen benchmark settings; original image and aspect-preserving ECPose."""
    mode: str = field(default="original", init=False)
    pose_resize: str = field(default="letterbox", init=False)
    face_threshold: float = field(default=0.4, init=False)
    duplicate_face_distance: float = field(default=0.05, init=False)


@dataclass
class PipelineResult:
    metadata: dict
    images: dict[str, Image.Image] = field(default_factory=dict, repr=False)
    body_embedding: np.ndarray | None = field(default=None, repr=False)
    face_embedding: np.ndarray | None = field(default=None, repr=False)

    @property
    def embedding(self) -> np.ndarray | None:
        route = self.metadata.get("route")
        return self.face_embedding if route == "face" else self.body_embedding if route == "body" else None


def load_rgb(source: str | Path | Image.Image) -> Image.Image:
    if isinstance(source, Image.Image):
        return ImageOps.exif_transpose(source).convert("RGB")
    with Image.open(source) as image:
        return ImageOps.exif_transpose(image).convert("RGB")


def validate_embedding(value) -> np.ndarray:
    value = np.asarray(value, dtype=np.float32).reshape(-1)
    norm = float(np.linalg.norm(value))
    if value.shape != (512,) or not np.isfinite(value).all() or abs(norm - 1) > 1e-4:
        raise RuntimeError(f"Invalid recognition output: shape={value.shape}, norm={norm}")
    return value / norm


def group_faces(candidates: list[dict], threshold: float = 0.05) -> list[list[dict]]:
    groups: list[list[dict]] = []
    for candidate in sorted(candidates, key=lambda c: -c["score"]):
        points = np.asarray(candidate["keypoints"], dtype=float)[:3]
        for group in groups:
            leader = np.asarray(group[0]["keypoints"], dtype=float)[:3]
            scale = max(np.linalg.norm(np.ptp(points, axis=0)), np.linalg.norm(np.ptp(leader, axis=0)), 1.0)
            if np.sqrt(np.mean(np.sum((points - leader) ** 2, axis=1))) / scale <= threshold:
                group.append(candidate)
                break
        else:
            groups.append([candidate])
    return groups


class Pipeline:
    def __init__(self, config: PipelineConfig | None = None, *, profile_directory: Path | None = None):
        self.config = config or PipelineConfig()
        self.runtime = initialize_gpu()
        self.provenance = verify()
        install_vendor_path()
        from cat_recognition.deployment.preprocess import build_pose_transform, build_recognition_transform
        self.manifest = json.loads((ROOT / "models/MeowID-Base/deployment.json").read_text())
        preprocessing = self.manifest["preprocessing"]
        pose, body, face = (preprocessing[k] for k in ("pose", "body", "face"))
        self.pose_transform = build_pose_transform(tuple(pose["image_size"]), tuple(pose["mean"]), tuple(pose["std"]))
        self.body_transform = build_recognition_transform(body["image_size"], body["resize_size"], tuple(body["mean"]), tuple(body["std"]))
        self.face_transform = build_recognition_transform(face["image_size"], face["resize_size"], tuple(face["mean"]), tuple(face["std"]))
        self.pose = CudaSession(ROOT / "models/MeowID-Base/ecpose.onnx", profile_directory)
        self.recognition = CudaSession(ROOT / "models/MeowID-Base/meowid_base.onnx", profile_directory)
        self.runtime["onnxruntime"] = self.pose.version
        self.runtime["providers"] = {"pose": self.pose.providers, "recognition": self.recognition.providers}

    def process(self, source: str | Path | Image.Image) -> list[PipelineResult]:
        """One cat is assumed. Return features in RAM; no registration or storage."""
        original = load_rgb(source)
        source_name = str(Path(source).resolve()) if not isinstance(source, Image.Image) else "memory"
        metadata = {"source": source_name, "original_size": list(original.size), "mode": "original",
                    "device": DEVICE, "vectors_persisted": False,
                    "coordinate_system": "EXIF-corrected RGB pixels", "pose_resize": "letterbox",
                    "instance_index": 0, "crop_box": [0, 0, *original.size], "segmentation": "disabled"}
        return [self._recognize(original, original, metadata,
                               {"original": original, "body_input": original, "pose_input": original})]

    def _recognize(self, body_image: Image.Image, pose_image: Image.Image, metadata: dict, images: dict) -> PipelineResult:
        from cat_recognition.deployment.preprocess import align_face, estimate_similarity, ALIGNMENT_PROFILES
        started = time.perf_counter()
        pose_model_image = pose_image
        pose_geometry = None
        if self.config.pose_resize == 'letterbox':
            from .letterbox import letterbox_image
            height, width = self.manifest['preprocessing']['pose']['image_size']
            pose_model_image, pose_geometry = letterbox_image(pose_image, (width, height))
            metadata['pose_letterbox'] = pose_geometry.to_dict()
        pose = self.pose.run(images=self.pose_transform(pose_model_image).unsqueeze(0).numpy(),
                             orig_target_sizes=np.asarray([pose_model_image.size], dtype=np.float32))
        candidates = []
        for score, label, raw_points in zip(pose["scores"][0], pose["labels"][0], pose["keypoints"][0]):
            points = raw_points.reshape(-1, 2)
            if pose_geometry is not None:
                points = pose_geometry.inverse_points(points)
            if int(label) == self.manifest["face_label"] and np.isfinite(score) and score >= self.config.face_threshold \
                    and len(points) >= 3 and np.isfinite(points[:3]).all():
                candidates.append({"score": float(score), "keypoints": points.tolist()})
        groups = group_faces(candidates, self.config.duplicate_face_distance)
        metadata.update({"face_candidates": len(candidates), "face_groups": len(groups),
                         "face_threshold": self.config.face_threshold, "alignment": self.manifest["alignment"],
                         "body_input_size": list(body_image.size), "pose_input_size": list(pose_image.size)})
        if len(groups) > 1:
            metadata.update(status="ambiguous_faces", route=None, recognition_seconds=time.perf_counter() - started)
            return PipelineResult(metadata, images)
        aligned = None
        if groups:
            detection = groups[0][0]
            points = np.asarray(detection["keypoints"])
            metadata["face_score"] = detection["score"]
            metadata["landmarks_crop"] = points.tolist()
            metadata["landmarks_original"] = (points + np.asarray(metadata["crop_box"][:2])).tolist()
            # Track the official fallback without changing the official alignment implementation.
            try:
                if np.linalg.norm(points[1] - points[0]) < 1:
                    raise ValueError("degenerate eye distance")
                estimate_similarity(points[:3], ALIGNMENT_PROFILES[self.manifest["alignment"]] * 256)
                metadata["alignment_method"] = "similarity_3pt"
            except (ValueError, np.linalg.LinAlgError):
                metadata["alignment_method"] = "landmark_bbox_fallback"
            try:
                aligned = align_face(pose_image, points, output_size=256, profile=self.manifest["alignment"])
                images["aligned_face"] = aligned
            except (ValueError, np.linalg.LinAlgError) as exc:
                metadata["alignment_method"] = "failed_body_route"
                metadata["alignment_error"] = str(exc)
        placeholder = aligned if aligned is not None else Image.new("RGB", (256, 256))
        values = self.recognition.run(body_images=self.body_transform(body_image).unsqueeze(0).numpy(),
                                      face_images=self.face_transform(placeholder).unsqueeze(0).numpy())
        body = validate_embedding(values["body_embeddings"][0])
        face = validate_embedding(values["face_embeddings"][0]) if aligned is not None else None
        metadata.update({"status": "ok", "route": "face" if face is not None else "body",
                         "body_embedding_shape": [512], "body_embedding_norm": float(np.linalg.norm(body)),
                         "face_embedding_shape": [512] if face is not None else None,
                         "face_embedding_norm": float(np.linalg.norm(face)) if face is not None else None,
                         "recognition_seconds": time.perf_counter() - started})
        return PipelineResult(metadata, images, body, face)

    def finish_profiles(self) -> list[str]:
        return [p for p in (self.pose.end_profile(), self.recognition.end_profile()) if p]
