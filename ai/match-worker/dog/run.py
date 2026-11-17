"""JSON-only success stdout. Diagnostics and error JSON go to stderr."""
import argparse
from contextlib import redirect_stdout
import json
from pathlib import Path
import sys

for stream in (sys.stdout, sys.stderr):
    if hasattr(stream, "reconfigure"):
        stream.reconfigure(encoding="utf-8")

def read_json(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))

def main():
    parser = argparse.ArgumentParser(description='Standalone dog re-identification pipeline')
    sub = parser.add_subparsers(dest='command', required=True)
    for name in ('extract', 'register'):
        p = sub.add_parser(name)
        p.add_argument('--image', required=True)
        p.add_argument('--device', choices=['cpu','cuda:1'], default='cpu')
        p.add_argument('--output')
        if name == 'extract':
            p.add_argument('--artifacts')
        else:
            p.add_argument('--pet-id', required=True)
            p.add_argument('--photo-id', required=True)
            p.add_argument('--gallery-id', default='default')
            p.add_argument('--image-uri')
    p = sub.add_parser('compare')
    p.add_argument('--query', required=True); p.add_argument('--reference', required=True)
    p.add_argument('--output')
    p = sub.add_parser('search')
    p.add_argument('--query', required=True); p.add_argument('--gallery', required=True)
    p.add_argument('--gallery-id', default='default'); p.add_argument('--top-k', type=int, default=5)
    p.add_argument('--min-score', type=float); p.add_argument('--exclude-photo-id', action='append', default=[])
    p.add_argument('--output')
    args = parser.parse_args()
    try:
        with redirect_stdout(sys.stderr):
            from pipeline import ReIDPipeline
            if args.command == 'extract':
                result = ReIDPipeline(args.device).extract(args.image, artifacts=args.artifacts)
            elif args.command == 'register':
                result = ReIDPipeline(args.device).registration(args.image, pet_id=args.pet_id,
                    photo_id=args.photo_id, gallery_id=args.gallery_id, image_uri=args.image_uri)
            elif args.command == 'compare':
                result = ReIDPipeline.compare(read_json(args.query), read_json(args.reference))
            else:
                result = ReIDPipeline.search(read_json(args.query), read_json(args.gallery),
                    gallery_id=args.gallery_id, top_k=args.top_k, min_score=args.min_score,
                    exclude_photo_ids=args.exclude_photo_id)
        encoded = json.dumps(result, ensure_ascii=False, allow_nan=False)
        if args.output:
            destination = Path(args.output); destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(encoded+'\n', encoding='utf-8')
        print(encoded)
        return 0
    except Exception as exc:
        print(json.dumps({'status':'error','error':type(exc).__name__,'message':str(exc)},
                         ensure_ascii=False), file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
