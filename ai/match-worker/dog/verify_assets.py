"""Validate every included weight and immutable source file without model inference."""
from pathlib import Path
import hashlib, json
ROOT=Path(__file__).resolve().parent

def check(path, expected):
    with path.open('rb') as stream:
        actual=hashlib.file_digest(stream,'sha256').hexdigest()
    if actual != expected:
        raise RuntimeError('SHA256 mismatch: '+str(path.relative_to(ROOT)))

if __name__ == '__main__':
    vendor=json.loads((ROOT/'vendor_manifest.json').read_text('utf-8'))
    for name, expected in vendor['files'].items():
        check(ROOT/'vendor/meowid'/name, expected)
    provenance=json.loads((ROOT/'models/provenance.json').read_text('utf-8'))
    items=provenance.get('files',provenance)
    for name, item in items.items():
        check(ROOT/'models'/name, item['sha256'])
    print(json.dumps({'status':'ok','weight_and_metadata_files':len(items),'vendor_files':len(vendor['files'])}))
