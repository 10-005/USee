"""SQL(pgvector) 점수가 AI 팀 CosineIndex.search 점수와 같은지 확인한다.
pgvector 가 있는 Postgres 가 필요하다: DB_HOST/DB_PORT/DB_NAME/DB_USERNAME/DB_PASSWORD (확장 vector·스키마 ai 가 이미 있어야 한다. ai_app 계정이면 된다).
실행: SPECIES=cat python -m unittest test_scores -v   (모델 가중치는 필요 없다)
"""
import os
import unittest

import numpy as np

os.environ.setdefault('SPECIES', 'cat')
import worker  # noqa: E402
from petid_cosine import CosineIndex  # noqa: E402

BASE = 990_000_000  # 실제 데이터와 겹치지 않는 번호


@unittest.skipUnless(os.environ.get('DB_HOST'), 'DB_HOST 가 없어 건너뛴다')
class ScoreParityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import psycopg
        cls.conn = psycopg.connect(host=os.environ['DB_HOST'], port=os.environ.get('DB_PORT', '5432'),
                                   dbname=os.environ['DB_NAME'], user=os.environ['DB_USERNAME'],
                                   password=os.environ['DB_PASSWORD'], autocommit=True)
        worker.init_db(cls.conn)

    def setUp(self):
        self.clean()

    def tearDown(self):
        self.clean()

    def clean(self):
        self.conn.execute('DELETE FROM ai.reid_embeddings WHERE target_seq >= %s', (BASE,))

    def check(self, species, with_faces):
        rng = np.random.default_rng(7 if with_faces else 8)
        model_id = worker.MODEL_IDS[species]
        anchor = rng.normal(size=512)

        def feature(face):
            # 서로 다른 정도로 anchor 와 닮은 벡터를 만들어 점수가 고르게 퍼지게 한다
            def vec():
                v = anchor * rng.uniform(0, 2) + rng.normal(size=512)
                return (v / np.linalg.norm(v)).tolist()
            vectors = {'body': vec()}
            if face:
                vectors['face'] = vec()
            return {'schema_version': 1, 'dimension': 512, 'species': species, 'model_id': model_id,
                    'route': 'face' if face else 'body', 'vectors': vectors}

        source = ('MISSING', BASE)
        source_features = {f'src-{i}': feature(with_faces and i == 0) for i in range(2)}
        targets = {('FOUND', BASE + n): {f'f{n}-{i}': feature(with_faces and rng.random() < .5)
                                         for i in range(int(rng.integers(1, 4)))} for n in range(1, 9)}
        candidates = [t for t in targets if t[1] != BASE + 8]  # BASE+8 은 후보가 아니다

        for key, f in source_features.items():
            worker.store_feature(self.conn, *source, key, f)
        for target, photos in targets.items():
            for key, f in photos.items():
                worker.store_feature(self.conn, *target, key, f)

        got = {(t, s): v for t, s, v in worker.scores(self.conn, *source, candidates, species, model_id)}

        records = [{'pet_id': f'{t}:{s}', 'photo_id': key, 'embedding': f}
                   for (t, s), photos in targets.items() for key, f in photos.items()]
        index = CosineIndex(records)
        expected = {}
        for query in source_features.values():
            result = index.search(query, top_k=500, candidate_pet_ids=[f'{t}:{s}' for t, s in candidates])
            for m in result['matches']:
                t, s = m['pet_id'].split(':')
                key = (t, int(s))
                expected[key] = max(expected.get(key, -2), m['score'])

        self.assertTrue(expected)
        self.assertEqual(set(got), set(expected))
        for key in expected:
            self.assertAlmostEqual(got[key], expected[key], places=5, msg=str(key))
        self.assertEqual([k for k, _ in sorted(got.items(), key=lambda kv: -kv[1])],
                         [k for k, _ in sorted(expected.items(), key=lambda kv: -kv[1])])

    def test_dog_body_only(self):
        self.check('dog', with_faces=False)

    def test_cat_face_and_body_routes(self):
        self.check('cat', with_faces=True)

    def test_no_candidates(self):
        self.assertEqual(worker.scores(self.conn, 'MISSING', BASE, []), [])


class CandidateDirectionTest(unittest.TestCase):
    def test_filters_by_direction(self):
        raw = [{'type': 'FOUND', 'seq': 1}, {'type': 'MISSING', 'seq': 2}, {'type': 'SHELTER_ANIMAL', 'seq': 3},
               {'type': 'FOUND', 'seq': 1}, {'type': 'FOUND', 'seq': '4'}]
        self.assertEqual(worker.valid_candidates('MISSING', raw), [('FOUND', 1), ('SHELTER_ANIMAL', 3)])
        self.assertEqual(worker.valid_candidates('FOUND', raw), [('MISSING', 2)])
        self.assertEqual(worker.valid_candidates('SHELTER_ANIMAL', None), [])


if __name__ == '__main__':
    unittest.main()
