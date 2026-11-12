"""BowwowID device adapter exported from the current PetID CPU runtime."""
import sys
from pathlib import Path
import numpy as np
from PIL import Image
from common import ROOT, validate_device, check_hash, ImageRejected
from contract import Embedding, DOG_MODEL
DOG_SHA = 'b4aab62423f75c66f5716c808c77e41370a89b93527655f4bc6be325238a3de7'
SEG_SHA = 'eb9a06f63e2206c35d68d839b08c362429ebecf933ad54c1ad68b2fd001c17cf'

def install_vendor_path():
    source = ROOT / 'vendor/meowid/src'
    loaded = sys.modules.get('cat_recognition')
    if loaded is not None and not Path(loaded.__file__).resolve().is_relative_to(source):
        raise RuntimeError('Run this standalone pipeline in its own Python process')
    if str(source) not in sys.path:
        sys.path.insert(0, str(source))


class DogEncoder:
    def __init__(self, device='cpu'):
        self.device = validate_device(device)
        import torch
        install_vendor_path()
        base = ROOT / 'models'
        check_hash(base / 'bowwow_letterbox_body.pth', DOG_SHA)
        check_hash(base / 'yolo11m-seg.pt', SEG_SHA)
        from cat_recognition.config import ConfigNode
        from cat_recognition.models.builder import _build_recognizer
        from body_transforms import build_eval_transform
        checkpoint = torch.load(base / 'bowwow_letterbox_body.pth', map_location='cpu', weights_only=True)
        cfg = ConfigNode(checkpoint['config'])
        if cfg.data.transforms.eval.geometry != 'letterbox':
            raise RuntimeError('Wrong body transform configuration')
        cfg.model.backbone.pretrained = False
        cfg.model.backbone.pretrained_ckpt = None
        with torch.device('cpu'):
            self.model = _build_recognizer(cfg.model, 256, head=None)
        self.model.load_state_dict(checkpoint['model'], strict=True)
        self.model.to(self.device).eval().float()
        self.transform = build_eval_transform(cfg.data.transforms.eval)
        del checkpoint
        (ROOT / '.runtime' / 'ultralytics').mkdir(parents=True, exist_ok=True)
        from ultralytics import YOLO
        self.segmenter = YOLO(str(base / 'yolo11m-seg.pt'), task='segment').to(self.device)

    def prepare_body(self, image):
        import torch
        from ultralytics.utils.ops import scale_masks
        with torch.inference_mode():
            result = self.segmenter.predict([np.ascontiguousarray(np.asarray(image)[:, :, ::-1])],
                device=self.device, classes=[15, 16], conf=.25, iou=.5, imgsz=640, batch=1,
                half=False, retina_masks=False, verbose=False, save=False)[0]
            boxes = result.boxes.data.cpu().numpy()
            dogs = np.flatnonzero(boxes[:, -1] == 16)
            if len(dogs) == 0:
                raise ImageRejected('no_dog: no dog detected')
            if len(dogs) > 1:
                raise ImageRejected('multiple_dogs: upload one dog per image')
            if result.masks is None:
                raise ImageRejected('missing_segmentation_mask')
            row = boxes[dogs[0]]
            x1, y1, x2, y2 = row[:4]; w, h = x2-x1, y2-y1
            if min(w, h) < 64:
                raise ImageRejected('dog_too_small')
            box = [max(0, int(np.floor(x1-.06*w))), max(0, int(np.floor(y1-.06*h))),
                   min(image.width, int(np.ceil(x2+.06*w))), min(image.height, int(np.ceil(y2+.06*h)))]
            mask = (scale_masks(result.masks.data[dogs[0]][None, None].float(), (image.height, image.width))[0, 0] > .5).cpu().numpy()
            left, top, right, bottom = box
            mask = mask[top:bottom, left:right]
            if float(mask.mean()) < .10:
                raise ImageRejected('insufficient_segmentation_foreground')
            pixels = np.asarray(image.crop(box)).copy(); pixels[~mask] = 0
            return Image.fromarray(pixels), {'body_box': box, 'body_confidence': float(row[4]),
                                           'mask_foreground_fraction': float(mask.mean())}

    def extract(self, image):
        import torch
        body, metadata = self.prepare_body(image)
        with torch.inference_mode():
            tensor = self.transform(body).unsqueeze(0).to(self.device, dtype=torch.float32)
            vector = torch.nn.functional.normalize(self.model.encode(tensor), dim=1)[0].float().cpu().numpy()
        if vector.shape != (512,) or not np.isfinite(vector).all():
            raise RuntimeError('Invalid body embedding')
        feature = Embedding(species='dog', model_id=DOG_MODEL, route='body', vectors={'body': vector.tolist()},
            metadata={**metadata, 'pipeline': 'yolo11m-seg_mask_letterbox256', 'landmarks_used': False,
                      'device': self.device, 'original_size': list(image.size)}).to_dict()
        return feature, {'body_crop': body}
