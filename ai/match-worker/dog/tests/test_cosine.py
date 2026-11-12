import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import numpy as np
from petid_cosine import CosineIndex, compare
from petid_cosine.contract import MODEL_IDS


def feature(species='dog', vector=None, face=None):
    body = [1.0]+[0.0]*511 if vector is None else list(vector)
    vectors = {'body': body}
    if face is not None:
        vectors['face'] = list(face)
    return dict(schema_version=1, dimension=512, species=species,
                model_id=MODEL_IDS[species], route='face' if face is not None else 'body', vectors=vectors)


def record(pet, photo, embedding, gallery='default'):
    return dict(pet_id=pet, photo_id=photo, embedding=embedding, gallery_id=gallery,
                image_uri=f'/photos/{photo}.jpg')


class CosineTests(unittest.TestCase):
    def test_ranking_matches_independent_cosine_and_best_photo(self):
        rng=np.random.default_rng(77)
        query=rng.normal(size=512)
        vectors=rng.normal(size=(12,512))
        vectors[7]=query*3
        rows=[record(f'pet-{i//3}',f'photo-{i}',feature(vector=v)) for i,v in enumerate(vectors)]
        expected=vectors@query/(np.linalg.norm(vectors,axis=1)*np.linalg.norm(query))
        result=compare(feature(vector=query),rows,top_k=12,group_by='photo')
        self.assertEqual([r['photo_id'] for r in result['matches']],
                         [f'photo-{i}' for i in np.argsort(-expected)])
        for item in result['matches']:
            self.assertAlmostEqual(item['score'],expected[int(item['photo_id'].split('-')[1])],places=6)
        grouped=CosineIndex(rows).search(feature(vector=query))
        self.assertEqual(len(grouped['matches']),4)
        self.assertEqual(grouped['matches'][0]['photo_id'],'photo-7')
        self.assertEqual(grouped['matches'][0]['image_uri'],'/photos/photo-7.jpg')

    def test_species_gallery_and_face_body_are_separate(self):
        x=[1.0]+[0.0]*511; y=[0.0,1.0]+[0.0]*510
        cat=feature('cat',x,y)
        rows=[record('cat','c',cat),record('dog','d',feature()),
              record('body-cat','b',feature('cat',x)),record('other','o',cat,'other')]
        index=CosineIndex(rows)
        self.assertEqual([m['pet_id'] for m in index.search(cat)['matches']],['cat'])
        self.assertEqual(index.search(feature())['candidate_photos'],1)
        self.assertEqual(index.search(feature('cat',x))['candidate_photos'],2)
        self.assertEqual(index.search(cat,gallery_id='other')['matches'][0]['pet_id'],'other')

    def test_filters_threshold_and_empty_candidates(self):
        index=CosineIndex([record('a','1',feature()),record('b','2',feature(vector=[-1.0]+[0.0]*511))])
        self.assertEqual(index.search(feature(),candidate_pet_ids=[])['status'],'no_candidates')
        self.assertEqual(index.search(feature(),exclude_photo_ids=['1'],min_score=0)['status'],'below_threshold')
        self.assertEqual(index.search(feature(),candidate_pet_ids=iter(['b']))['matches'][0]['pet_id'],'b')
        self.assertEqual(index.search(feature(),exclude_photo_ids=iter(['1']))['matches'][0]['pet_id'],'b')
        self.assertEqual(index.search(feature(),gallery_id='missing')['candidate_pets'],0)

    def test_invalid_inputs_are_rejected(self):
        good=feature()
        for bad_vector in ([0.0]*512,[float('nan')]*512,[float('inf')]*512,[1.0]*511):
            with self.subTest(vector=bad_vector[0]):
                with self.assertRaises(ValueError):CosineIndex([record('a','1',feature(vector=bad_vector))])
        for update in ({'model_id':'old-version'},{'route':'face'},{'dimension':128}):
            bad=copy.deepcopy(good);bad.update(update)
            with self.assertRaises(ValueError):compare(bad,[])
        with self.assertRaises(ValueError):CosineIndex([record('a','1',good),record('a','1',good)])
        with self.assertRaises(ValueError):CosineIndex([record('a','1',good),record('a','2',feature('cat'))])
        for kwargs in ({'top_k':0},{'top_k':True},{'min_score':float('nan')},
                       {'group_by':'bad'},{'candidate_pet_ids':'a'}):
            with self.assertRaises(ValueError):compare(good,[],**kwargs)

    def test_snapshot_does_not_change_when_caller_mutates_records(self):
        rows=[record('a','1',feature())]
        index=CosineIndex(rows)
        rows[0]['embedding']['vectors']['body'][0]=-1
        rows[0]['pet_id']='changed'
        result=index.search(feature())['matches'][0]
        self.assertEqual(result['pet_id'],'a')
        self.assertAlmostEqual(result['score'],1)

    def test_cli_stdin_success_and_invalid_json(self):
        with tempfile.TemporaryDirectory() as directory:
            gallery=Path(directory)/'gallery.json'
            gallery.write_text(json.dumps([record('a','1',feature())]))
            cmd=[sys.executable,'-m','petid_cosine','--query','-','--gallery',str(gallery)]
            result=subprocess.run(cmd,input=json.dumps(feature()),text=True,capture_output=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertEqual(json.loads(result.stdout)['matches'][0]['pet_id'],'a')
            failed=subprocess.run(cmd,input='invalid',text=True,capture_output=True)
            self.assertEqual(failed.returncode,1)
            self.assertIn('error',json.loads(failed.stderr))


if __name__=='__main__':unittest.main()
