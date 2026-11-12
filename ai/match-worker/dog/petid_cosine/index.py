"""CPU exact cosine retrieval; no server, database, model or GPU dependency."""
from __future__ import annotations
import time
import numpy as np
from .contract import validate_embedding


def normalize(vector):
    v=np.asarray(vector,dtype=np.float64)
    if v.shape!=(512,) or not np.isfinite(v).all():
        raise ValueError('Expected 512 finite values')
    norm=float(np.linalg.norm(v))
    if norm<1e-12:raise ValueError('Zero-length vector')
    return np.asarray(v/norm,dtype=np.float32)


class CosineIndex:
    """Immutable snapshot built from the caller's stored registration records.

    Create once for repeated queries. Rebuild when registered photos change.
    A record contains pet_id, photo_id, embedding, optional image_uri and gallery_id.
    """
    def __init__(self, records):
        spaces={};seen=set();species_by_pet={}
        for record in records:
            if not isinstance(record,dict):raise ValueError('Each record must be a dictionary')
            for name in ('pet_id','photo_id'):
                if not isinstance(record.get(name),str) or not record[name]:
                    raise ValueError(f'{name} must be a non-empty string')
            gallery=record.get('gallery_id','default')
            if not isinstance(gallery,str) or not gallery:raise ValueError('Invalid gallery_id')
            key=(gallery,record['photo_id'])
            if key in seen:raise ValueError('Duplicate photo_id in the same gallery')
            seen.add(key)
            e=validate_embedding(record.get('embedding'))
            pet_key=(gallery,record['pet_id'])
            if pet_key in species_by_pet and species_by_pet[pet_key]!=e['species']:
                raise ValueError('pet_id belongs to multiple species')
            species_by_pet[pet_key]=e['species']
            metadata={k:record[k] for k in ('pet_id','photo_id')}
            metadata['image_uri']=record.get('image_uri')
            if metadata['image_uri'] is not None and not isinstance(metadata['image_uri'],str):
                raise ValueError('image_uri must be a string or null')
            for route,vector in e['vectors'].items():
                space=(gallery,e['species'],e['model_id'],route)
                spaces.setdefault(space,[]).append((metadata.copy(),normalize(vector)))
        self.spaces={}
        for space,values in spaces.items():
            values.sort(key=lambda pair:(pair[0]['pet_id'],pair[0]['photo_id']))
            matrix=np.stack([v for _,v in values]);matrix.flags.writeable=False
            self.spaces[space]=(matrix,tuple(row for row,_ in values))

    def search(self, query, *, gallery_id='default', top_k=5, min_score=None,
               candidate_pet_ids=None, exclude_photo_ids=(), group_by='pet'):
        started=time.perf_counter()
        e=validate_embedding(query)
        if not isinstance(top_k,int) or isinstance(top_k,bool) or not 1<=top_k<=500:
            raise ValueError('top_k must be an integer in 1..500')
        if group_by not in ('pet','photo'):raise ValueError('group_by must be pet or photo')
        if min_score is not None and (not np.isfinite(min_score) or not -1<=min_score<=1):
            raise ValueError('min_score must be finite and within [-1,1]')
        if not isinstance(gallery_id,str) or not gallery_id:raise ValueError('Invalid gallery_id')
        def id_set(values, name):
            if isinstance(values,str):raise ValueError(f'{name} must contain string IDs')
            try:
                items=tuple(values)
            except TypeError as exc:
                raise ValueError(f'{name} must contain string IDs') from exc
            if not all(isinstance(x,str) for x in items):
                raise ValueError(f'{name} must contain string IDs')
            return set(items)
        include=None if candidate_pet_ids is None else id_set(candidate_pet_ids,'candidate_pet_ids')
        exclude=id_set(() if exclude_photo_ids is None else exclude_photo_ids,'exclude_photo_ids')
        space=(gallery_id,e['species'],e['model_id'],e['route'])
        matrix,rows=self.spaces.get(space,(np.empty((0,512),dtype=np.float32),()))
        selected=[i for i,row in enumerate(rows) if row['photo_id'] not in exclude and
                  (include is None or row['pet_id'] in include)]
        scores=np.clip(matrix[selected] @ normalize(e['vectors'][e['route']]),-1.,1.) if selected else []
        best={};pets=set()
        for i,score in zip(selected,scores):
            row=rows[i];pets.add(row['pet_id'])
            if min_score is not None and float(score)<min_score:continue
            key=row['pet_id'] if group_by=='pet' else row['photo_id']
            if key not in best or float(score)>best[key]['score']:
                best[key]={**row,'score':float(score)}
        matches=sorted(best.values(),key=lambda row:(-row['score'],row['pet_id'],row['photo_id']))[:top_k]
        return {'schema_version':1,'species':e['species'],'model_id':e['model_id'],'route':e['route'],
                'gallery_id':gallery_id,'group_by':group_by,'candidate_photos':len(selected),
                'candidate_pets':len(pets),'matches':matches,
                'status':'ok' if matches else ('no_candidates' if not selected else 'below_threshold'),
                'elapsed_ms':(time.perf_counter()-started)*1000}


def compare(query, candidates, **kwargs):
    """One-shot helper. Includes rebuilding/normalizing the candidate index every call."""
    return CosineIndex(candidates).search(query,**kwargs)
