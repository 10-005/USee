"""Replace only the first geometry transform; keep other augmentations fixed."""
from PIL import Image
from torchvision import transforms as T
from cat_recognition.data import transforms as upstream

_train = upstream.build_train_transform
_eval = upstream.build_eval_transform


class Letterbox:
    def __init__(self, size=256):
        self.size = int(size)

    def __call__(self, image):
        w, h = image.size
        scale = self.size/max(w, h)
        width, height = max(1, round(w*scale)), max(1, round(h*scale))
        resized = image.resize((width, height), Image.Resampling.BICUBIC)
        canvas = Image.new('RGB', (self.size, self.size), (0, 0, 0))
        canvas.paste(resized, ((self.size-width)//2, (self.size-height)//2))
        return canvas

    def __repr__(self):
        return f'Letterbox(size={self.size}, fill=0, interpolation=BICUBIC)'


def build_train_transform(cfg):
    transform = _train(cfg)
    if cfg.get('geometry', 'crop') == 'letterbox':
        assert isinstance(transform.transforms[0], T.RandomResizedCrop)
        transform.transforms[0] = Letterbox(cfg.image_size)
    return transform


def build_eval_transform(cfg):
    transform = _eval(cfg)
    if cfg.get('geometry', 'crop') == 'letterbox':
        assert isinstance(transform.transforms[0], T.Resize)
        assert isinstance(transform.transforms[1], T.CenterCrop)
        transform.transforms[:2] = [Letterbox(cfg.image_size)]
    return transform


def install():
    import cat_recognition.data as data
    data.build_train_transform = build_train_transform
    data.build_eval_transform = build_eval_transform
