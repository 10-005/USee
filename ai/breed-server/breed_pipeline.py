"""AI 팀 전달본(Pet_AI_Pipelines_Handoff_20261110, 01_dog_breed·02_cat_breed pipeline.py)을 종만 인자로 바꾼 것. 전처리·추론은 그대로."""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import threading
import time

from PIL import Image, ImageOps
import timm
import torch
from torchvision import transforms

SUPPORTED_FORMATS = {"JPEG", "PNG", "WEBP"}
MAX_PIXELS = 25_000_000


class PipelineError(Exception):
    """Stable machine-readable error code with a human-readable message."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code

    def as_dict(self):
        return {"ok": False, "error": {"code": self.code, "message": str(self)}}


def decode_image(source):
    """Accept a path, encoded bytes, or PIL image; do not resize at decoding."""
    try:
        if isinstance(source, Image.Image):
            image = source.copy()
        else:
            if isinstance(source, (str, Path)):
                path = Path(source)
                if not path.is_file():
                    raise PipelineError("IMAGE_NOT_FOUND", "Input image file does not exist.")
                payload = path.read_bytes()
            elif isinstance(source, (bytes, bytearray)):
                payload = bytes(source)
            else:
                raise PipelineError("INVALID_IMAGE", "Input must be a path, encoded bytes, or PIL image.")
            with Image.open(io.BytesIO(payload)) as opened:
                if opened.format not in SUPPORTED_FORMATS:
                    raise PipelineError("INVALID_IMAGE", "Supported formats: JPEG, PNG, WEBP.")
                _validate_dimensions(opened)
                opened.load()
                image = opened.copy()
        _validate_dimensions(image)
        image.load()
        return image
    except PipelineError:
        raise
    except Exception as exc:
        raise PipelineError("INVALID_IMAGE", "Cannot decode the image safely.") from exc


def _validate_dimensions(image):
    if min(image.size) < 32 or image.width * image.height > MAX_PIXELS:
        raise PipelineError("INVALID_IMAGE", "Image needs at least 32 pixels per side and at most 25,000,000 pixels.")


class ImageTransform:
    """Exact evaluation preprocessing from the deployed breed_training.data module."""

    def __init__(self, config):
        self.size = config["image_size"]
        self.fill = tuple(round(value * 255) for value in config["mean"])
        self.tensor = transforms.Compose([
            transforms.ToTensor(),
            transforms.Normalize(config["mean"], config["std"]),
        ])

    def __call__(self, image):
        image = ImageOps.exif_transpose(image)
        if image.mode in ("RGBA", "LA") or "transparency" in image.info:
            rgba = image.convert("RGBA")
            background = Image.new("RGBA", rgba.size, (*self.fill, 255))
            image = Image.alpha_composite(background, rgba).convert("RGB")
        else:
            image = image.convert("RGB")
        scale = self.size / max(image.size)
        width, height = [max(1, min(self.size, round(value * scale))) for value in image.size]
        resized = image.resize((width, height), Image.Resampling.BICUBIC)
        canvas = Image.new("RGB", (self.size, self.size), self.fill)
        canvas.paste(resized, ((self.size - width) // 2, (self.size - height) // 2))
        return self.tensor(canvas)


def _sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _read_json(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        raise PipelineError("MODEL_INVALID", f"Cannot read model metadata: {path.name}") from exc


class BreedClassifier:
    """Keep one instance alive and reuse predict() for successive images.

    Defaults to CPU FP32. CUDA is never selected or substituted automatically.
    This is a closed-set classifier, not an animal detector or unknown-breed filter.
    """

    def __init__(self, species, model_dir, device="cpu", cpu_threads=4):
        if device not in ("cpu", "cuda:1"):
            raise PipelineError("INVALID_DEVICE", "Choose explicitly cpu or cuda:1.")
        if device == "cuda:1" and (not torch.cuda.is_available() or torch.cuda.device_count() < 2):
            raise PipelineError("DEVICE_UNAVAILABLE", "Requested cuda:1 is unavailable. No CPU fallback is performed.")
        if not isinstance(cpu_threads, int) or isinstance(cpu_threads, bool) or cpu_threads < 1:
            raise PipelineError("INVALID_ARGUMENT", "cpu_threads must be a positive integer.")
        torch.set_num_threads(cpu_threads)
        self.device = torch.device(device)
        self.species = species
        self.model_dir = Path(model_dir)
        self.metadata = _read_json(self.model_dir / "model_manifest.json")
        self.config = _read_json(self.model_dir / "resolved_config.json")
        self.taxonomy = _read_json(self.model_dir / "taxonomy.json")
        self.names = _read_json(self.model_dir / "display_names.json")
        self.lock = threading.Lock()
        self.model = None
        self._validate_metadata()
        self.classes = sorted(self.taxonomy[self.species]["class_to_idx"], key=self.taxonomy[self.species]["class_to_idx"].get)
        self.transform = ImageTransform(self.config)

    def _validate_metadata(self):
        try:
            mapping = self.taxonomy[self.species]["class_to_idx"]
            valid = (self.metadata["species"] == self.species
                     and self.config["model_name"] == "convnext_tiny.fb_in1k"
                     and self.config["num_classes"] == 15
                     and self.config["image_size"] == 384
                     and sorted(mapping.values()) == list(range(15))
                     and set(mapping) <= set(self.names))
            if not valid:
                raise ValueError("Unexpected configuration")
            for name, expected in self.metadata["files_sha256"].items():
                path = self.model_dir / name
                if not path.is_file() or _sha256(path) != expected:
                    raise PipelineError("MODEL_INVALID", f"Missing or changed model file: {name}")
        except PipelineError:
            raise
        except Exception as exc:
            raise PipelineError("MODEL_INVALID", "Model species, configuration or class order is invalid.") from exc

    def _load(self):
        if self.model is not None:
            return
        try:
            state = torch.load(self.model_dir / "inference.pt", map_location="cpu", weights_only=True)
            if (state["species"] != self.species or state["config"] != self.config
                    or state["epoch"] != self.metadata["checkpoint_epoch_zero_based"]
                    or state["class_to_idx"] != self.taxonomy[self.species]["class_to_idx"]):
                raise ValueError("Checkpoint metadata mismatch")
            # The bundled checkpoint contains all parameters. Never download initial weights.
            model = timm.create_model(self.config["model_name"], pretrained=False, num_classes=15)
            model.load_state_dict(state["model"], strict=True)
            self.model = model.to(device=self.device, dtype=torch.float32).eval()
        except Exception as exc:
            raise PipelineError("MODEL_LOAD_FAILED", "Could not load the bundled model on the requested device.") from exc

    def predict(self, image, top_k=5):
        if not isinstance(top_k, int) or isinstance(top_k, bool) or not 1 <= top_k <= 15:
            raise PipelineError("INVALID_ARGUMENT", "top_k must be an integer between 1 and 15.")
        started = time.perf_counter()
        decoded = decode_image(image)
        with self.lock:
            self._load()
            try:
                with torch.inference_mode():
                    tensor = self.transform(decoded).unsqueeze(0).to(self.device)
                    probabilities = self.model(tensor).float().softmax(dim=1)[0]
                if not torch.isfinite(probabilities).all():
                    raise ValueError("Nonfinite probabilities")
                scores, indexes = probabilities.topk(top_k)
                scores, indexes = scores.cpu().tolist(), indexes.cpu().tolist()
            except Exception as exc:
                raise PipelineError("INFERENCE_FAILED", "Model inference failed.") from exc
        return {
            "ok": True,
            "pipeline": f"{self.species}_breed",
            "species": self.species,
            "device": str(self.device),
            "elapsed_seconds": time.perf_counter() - started,
            "model": {"backbone": self.config["model_name"], "method_id": self.config["method_id"],
                      "checkpoint_epoch": self.metadata["checkpoint_epoch_zero_based"] + 1},
            "predictions": [{"rank": rank, "breed": self.classes[index],
                             "name": self.names[self.classes[index]], "score": score}
                            for rank, (score, index) in enumerate(zip(scores, indexes), start=1)],
            "note": "학습한 15개 품종 간의 보정되지 않은 softmax 점수입니다.",
        }
