import argparse
import json
import sys
from pathlib import Path
from . import compare


def main():
    parser=argparse.ArgumentParser(description='CPU-only comparison of stored PetID feature JSON')
    parser.add_argument('--query',required=True,help='Embedding JSON path, or - to read stdin')
    parser.add_argument('--gallery',required=True,type=Path,help='JSON array of registration records')
    parser.add_argument('--gallery-id',default='default')
    parser.add_argument('--top-k',type=int,default=5)
    parser.add_argument('--group-by',choices=['pet','photo'],default='pet')
    parser.add_argument('--min-score',type=float)
    parser.add_argument('--exclude-photo-id',action='append',default=[])
    args=parser.parse_args()
    try:
        query=json.load(sys.stdin) if args.query=='-' else json.loads(Path(args.query).read_text(encoding='utf-8'))
        records=json.loads(args.gallery.read_text(encoding='utf-8'))
        if not isinstance(records,list):raise ValueError('Gallery JSON must be an array')
        result=compare(query,records,gallery_id=args.gallery_id,top_k=args.top_k,
                       group_by=args.group_by,min_score=args.min_score,exclude_photo_ids=args.exclude_photo_id)
        print(json.dumps(result,ensure_ascii=False,allow_nan=False))
    except (ValueError,TypeError,OSError) as exc:
        print(json.dumps({'error':str(exc)},ensure_ascii=False),file=sys.stderr)
        raise SystemExit(1)


if __name__=='__main__':main()
