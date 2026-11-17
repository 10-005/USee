"""Aspect-preserving detector inputs and inverse transforms in original pixels."""
from dataclasses import asdict, dataclass

import numpy as np
from PIL import Image


@dataclass(frozen=True)
class LetterboxGeometry:
    original_size: tuple[int, int]
    canvas_size: tuple[int, int]
    resized_size: tuple[int, int]
    padding_left_top_right_bottom: tuple[int, int, int, int]

    @property
    def scales(self):
        # Rounded image dimensions determine the exact inverse on each axis.
        return np.asarray(self.resized_size, dtype=np.float64) / self.original_size

    def inverse_points(self, points):
        values = np.asarray(points, dtype=np.float64)
        if values.shape[-1] != 2:
            raise ValueError('Coordinates must have a final x,y dimension')
        return (values - self.padding_left_top_right_bottom[:2]) / self.scales

    def inverse_boxes(self, boxes):
        values = np.asarray(boxes, dtype=np.float64)
        points = self.inverse_points(values.reshape(*values.shape[:-1], 2, 2))
        points = np.clip(points, [0, 0], self.original_size)
        return points.reshape(values.shape).astype(np.float32)

    def restore_mask_logits(self, logits):
        """Undo padding on floating logits BEFORE thresholding in original space."""
        import torch.nn.functional as F
        if logits.ndim != 3:
            raise ValueError('Expected masks [N,H,W]')
        width, height = self.canvas_size
        restored = F.interpolate(logits.unsqueeze(1), size=(height, width), mode='bilinear', align_corners=False)
        left, top, _, _ = self.padding_left_top_right_bottom
        rw, rh = self.resized_size
        unpadded = restored[:, :, top:top+rh, left:left+rw]
        ow, oh = self.original_size
        return F.interpolate(unpadded, size=(oh, ow), mode='bilinear', align_corners=False)[:, 0]

    def to_dict(self):
        return {**asdict(self), 'scale_xy_after_rounding': self.scales.tolist(),
                'background_rgb': [0, 0, 0], 'interpolation': 'PIL BILINEAR',
                'coordinates': 'canvas predictions -> subtract left/top padding -> divide by exact x/y scales'}


def letterbox_image(image, canvas_size=(640, 640)):
    ow, oh = image.size
    width, height = canvas_size
    if min(ow, oh, width, height) <= 0:
        raise ValueError('Image and canvas sizes must be positive')
    scale = min(width/ow, height/oh)
    rw, rh = max(1, round(ow*scale)), max(1, round(oh*scale))
    left, top = (width-rw)//2, (height-rh)//2
    geometry = LetterboxGeometry((ow, oh), (width, height), (rw, rh),
                                 (left, top, width-rw-left, height-rh-top))
    canvas = Image.new('RGB', (width, height), (0, 0, 0))
    canvas.paste(image.convert('RGB').resize((rw, rh), Image.Resampling.BILINEAR), (left, top))
    return canvas, geometry

