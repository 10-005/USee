"""Strict physical cuda:1 runtimes. CPU preprocessing/shape operations are allowed."""
from __future__ import annotations

import os
from pathlib import Path
import sys

from .assets import VENDOR

DEVICE = "cuda:1"


def require_unmapped_device() -> None:
    if "CUDA_VISIBLE_DEVICES" in os.environ:
        raise RuntimeError("CUDA_VISIBLE_DEVICES is set. Start without GPU remapping; physical cuda:1 is required.")


def initialize_gpu() -> dict:
    require_unmapped_device()
    import torch
    if not torch.cuda.is_available() or torch.cuda.device_count() < 2:
        raise RuntimeError("Physical cuda:1 is unavailable. No CPU or other GPU inference fallback.")
    torch.cuda.set_device(1)
    torch.backends.cuda.matmul.allow_tf32 = False
    torch.backends.cudnn.allow_tf32 = False
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True
    torch.set_float32_matmul_precision("highest")
    torch.set_num_threads(6)
    return {"device": DEVICE, "gpu_name": torch.cuda.get_device_name(1), "torch": torch.__version__, "tf32": False}


def install_vendor_path() -> None:
    source = VENDOR / "src"
    loaded = sys.modules.get("cat_recognition")
    if loaded is not None and not Path(loaded.__file__).resolve().is_relative_to(source):
        raise RuntimeError("Another MeowID source is already imported. Restart this Python process/kernel.")
    if str(source) not in sys.path:
        sys.path.insert(0, str(source))


class CudaSession:
    def __init__(self, model_path: Path, profile_directory: Path | None = None):
        require_unmapped_device()
        import onnxruntime as ort
        if "CUDAExecutionProvider" not in ort.get_available_providers():
            raise RuntimeError("ONNX CUDAExecutionProvider is unavailable.")
        ort.preload_dlls()
        options = ort.SessionOptions()
        options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        options.intra_op_num_threads = 6
        options.log_severity_level = 3
        if profile_directory is not None:
            profile_directory.mkdir(parents=True, exist_ok=True)
            options.enable_profiling = True
            options.profile_file_prefix = str(profile_directory / model_path.stem)
        self.session = ort.InferenceSession(
            str(model_path), sess_options=options,
            providers=[("CUDAExecutionProvider", {"device_id": 1, "use_tf32": 0}), "CPUExecutionProvider"],
        )
        self.session.disable_fallback()
        self.providers = self.session.get_provider_options()
        cuda = self.providers.get("CUDAExecutionProvider", {})
        if cuda.get("device_id") != "1" or cuda.get("use_tf32") != "0":
            raise RuntimeError(f"Unexpected CUDA provider options: {cuda}")
        self.output_names = [item.name for item in self.session.get_outputs()]
        self.input_names = [item.name for item in self.session.get_inputs()]
        self.profiling = profile_directory is not None
        self.version = ort.__version__

    def run(self, **inputs):
        import numpy as np
        if set(inputs) != set(self.input_names):
            raise ValueError(f"ONNX input mismatch: expected {self.input_names}, got {list(inputs)}")
        values = self.session.run(self.output_names, {k: np.ascontiguousarray(v) for k, v in inputs.items()})
        return dict(zip(self.output_names, values))

    def end_profile(self) -> str | None:
        if not self.profiling:
            return None
        self.profiling = False
        return self.session.end_profiling()
