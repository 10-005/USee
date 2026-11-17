"""Independent image -> 512-D features -> cosine candidate retrieval API."""
from pathlib import Path
import hashlib
import threading
import time
from common import decode_image, ImageRejected
from contract import Embedding, validate_embedding
from petid_cosine import CosineIndex

SPECIES = 'dog'

class ReIDPipeline:
    """Load once and reuse. Explicit device only; CPU is the portable default."""
    def __init__(self, device='cpu'):
        if device not in ('cpu', 'cuda:1'):
            raise ValueError('device must be cpu or physical cuda:1')
        self.device = device
        self._model = None
        self._lock = threading.RLock()

    def extract(self, image, *, artifacts=None):
        data = bytes(image) if isinstance(image, (bytes, bytearray)) else Path(image).read_bytes()
        if len(data) > 20 * 1024 * 1024:
            raise ImageRejected('Maximum image file size is 20 MiB')
        rgb = decode_image(data)
        started = time.perf_counter()
        with self._lock:
            if self._model is None:
                from adapter import DogEncoder
                self._model = DogEncoder(device=self.device)
            if self.device == 'cuda:1':
                from common import validate_device
                validate_device(self.device)
            feature, images = self._model.extract(rgb)
        feature['metadata'].update(device=self.device,
            image_sha256=hashlib.sha256(data).hexdigest(), elapsed_seconds=time.perf_counter()-started)
        validate_embedding(feature)
        if artifacts is not None:
            dest = Path(artifacts); dest.mkdir(parents=True, exist_ok=True)
            for name in ('aligned_face', 'body_crop', 'body_input'):
                if name in images:
                    images[name].save(dest / (name + '.png'))
        return feature

    def registration(self, image, *, pet_id, photo_id, gallery_id='default', image_uri=None):
        row = {'pet_id': pet_id, 'photo_id': photo_id, 'gallery_id': gallery_id,
               'image_uri': image_uri, 'embedding': self.extract(image)}
        CosineIndex([row])
        return row

    @staticmethod
    def search(query, records, **kwargs):
        validate_embedding(query)
        if query['species'] != SPECIES:
            raise ValueError('Query species does not match this pipeline')
        return CosineIndex(records).search(query, **kwargs)

    @staticmethod
    def compare(query, reference):
        validate_embedding(query); validate_embedding(reference)
        if query['species'] != SPECIES or reference['species'] != SPECIES:
            raise ValueError('Both embeddings must match this pipeline species')
        records = [{'pet_id':'reference','photo_id':'reference','embedding':reference}]
        result = ReIDPipeline.search(query, records, top_k=1)
        return {'species':SPECIES, 'model_id':query['model_id'], 'route':query['route'],
                'status':result['status'],
                'cosine_similarity':result['matches'][0]['score'] if result['matches'] else None,
                'is_probability':False}
