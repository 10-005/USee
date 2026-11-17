"""Portable handoff utilities, exported 2026-11-10. No network model downloads."""
import hashlib, io, os, warnings
from pathlib import Path
from PIL import Image, ImageOps, UnidentifiedImageError
ROOT = Path(__file__).resolve().parent
os.environ['HF_HUB_OFFLINE'] = '1'
os.environ['TRANSFORMERS_OFFLINE'] = '1'
os.environ['YOLO_AUTOINSTALL'] = 'false'
os.environ.setdefault('YOLO_CONFIG_DIR', str(ROOT / '.runtime' / 'ultralytics'))

def validate_device(device):
    if device not in ('cpu', 'cuda:1'):
        raise ValueError('device must explicitly be cpu or physical cuda:1')
    import torch
    if device == 'cuda:1':
        if 'CUDA_VISIBLE_DEVICES' in os.environ:
            raise RuntimeError('GPU remapping is forbidden; unset CUDA_VISIBLE_DEVICES')
        if not torch.cuda.is_available() or torch.cuda.device_count() < 2:
            raise RuntimeError('physical cuda:1 is unavailable; no automatic fallback')
        torch.cuda.set_device(1)
        torch.backends.cuda.matmul.allow_tf32 = False
        torch.backends.cudnn.allow_tf32 = False
        torch.backends.cudnn.benchmark = False
        torch.backends.cudnn.deterministic = True
        torch.set_float32_matmul_precision('highest')
    torch.set_num_threads(4)
    return device


class ImageRejected(ValueError):
    pass

def check_hash(path, expected):
    with Path(path).open('rb') as stream:
        actual = hashlib.file_digest(stream, 'sha256').hexdigest()
    if actual != expected:
        raise RuntimeError(f'Model integrity mismatch: {path.name}')

def decode_image(data: bytes):
    try:
        with warnings.catch_warnings():
            warnings.simplefilter('error', Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(data)) as image:
                if image.format not in {'JPEG','PNG','WEBP'}:
                    raise ImageRejected('Only JPEG, PNG and WEBP images are supported')
                if image.width*image.height > 25_000_000:
                    raise ImageRejected('Maximum image size is 25 million pixels')
                image.load()
                rgb = ImageOps.exif_transpose(image).convert('RGB')
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise ImageRejected('Invalid or oversized image') from exc
    if min(rgb.size) < 128:
        raise ImageRejected('Image short side must be at least 128 pixels')
    return rgb
