"""Plain Python JSON contract shared with the CPU-only comparison module."""
from dataclasses import dataclass, field, asdict
import math

CAT_MODEL = 'meowid-base-16bc4ab78aba-original-letterbox640-v1'
DOG_MODEL = 'bowwow-body-b4aab62423f7-mask-letterbox256-v1'
MODEL_IDS = {'cat': CAT_MODEL, 'dog': DOG_MODEL}


def validate_embedding(value):
    if not isinstance(value,dict):raise ValueError('Embedding must be a dictionary')
    if value.get('schema_version',1)!=1 or value.get('dimension',512)!=512:
        raise ValueError('Unsupported schema version or embedding dimension')
    species=value.get('species');route=value.get('route');vectors=value.get('vectors')
    if species not in MODEL_IDS or value.get('model_id')!=MODEL_IDS[species]:
        raise ValueError('Unknown species/model/preprocessing version')
    if route not in ('body','face') or not isinstance(vectors,dict) or 'body' not in vectors:
        raise ValueError('body vector and a valid route are required')
    if set(vectors)-{'body','face'} or route not in vectors:
        raise ValueError('Invalid vector routes')
    if species=='dog' and (route!='body' or set(vectors)!={'body'}):
        raise ValueError('Bowwow supports body vectors only')
    if species=='cat' and (('face' in vectors)!=(route=='face')):
        raise ValueError('Cat route must select face when a fused face vector exists')
    for vector in vectors.values():
        if not isinstance(vector,(list,tuple)) or len(vector)!=512:
            raise ValueError('Each vector must contain 512 numbers')
        try:
            values=[float(x) for x in vector]
        except (ValueError,TypeError,OverflowError) as exc:
            raise ValueError('Vectors must contain numeric values') from exc
        if not all(math.isfinite(x) and abs(x)<=1e10 for x in values):
            raise ValueError('Vectors must be finite and bounded')
        if sum(x*x for x in values)<1e-24:
            raise ValueError('Zero-length vectors are invalid')
    return value


@dataclass
class Embedding:
    species: str
    model_id: str
    route: str
    vectors: dict
    metadata: dict = field(default_factory=dict)
    schema_version: int = 1
    dimension: int = 512

    def __post_init__(self):
        validate_embedding(self.to_dict())

    def to_dict(self):
        return asdict(self)
