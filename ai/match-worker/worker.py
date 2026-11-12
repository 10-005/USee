"""AI 매칭 워커: MatchRequested 를 받아 사진을 벡터로 바꿔 ai.reid_embeddings 에 저장하고,
요청에 실린 후보의 벡터하고만 코사인 비교해 AiMatchFound 를 발행한다(API 명세 17.8·21.6, Q432·Q522).

한 프로세스에 한 종만 올린다(dog/·cat/ 이 같은 이름의 모듈을 쓴다). SPECIES=dog | cat.
"""
import io
import json
import logging
import os
import sys
import time
import urllib.request
from pathlib import Path

import pika
import psycopg

# 실행 중 외부에서 모델·설정을 받지 않는다(Ultralytics 이벤트 전송·자동 설치, Hugging Face)
os.environ['YOLO_OFFLINE'] = 'true'
os.environ['HF_HUB_OFFLINE'] = '1'

SPECIES = os.environ.get('SPECIES', '')
if SPECIES not in ('dog', 'cat'):
    sys.exit('SPECIES 는 dog 또는 cat 이어야 한다')
sys.path.insert(0, str(Path(__file__).resolve().parent / SPECIES))
from common import ImageRejected  # noqa: E402  (AI 팀 모듈, 위 경로 추가 후에만 import 가능)
from contract import MODEL_IDS  # noqa: E402
from pipeline import ReIDPipeline  # noqa: E402

MODEL_ID = MODEL_IDS[SPECIES]
EXCHANGE = 'animal.events'
QUEUE = f'match-worker.{SPECIES}.MatchRequested'
RESULT_TYPE_ID = 'com.ssafy.animal.common.event.AiMatchFound'
IMAGE_URL_PREFIX = os.environ.get('IMAGE_URL_PREFIX', '')
MATCH_MIN_SCORE = float(os.environ.get('MATCH_MIN_SCORE', '0.6'))
MATCH_MAX_RESULTS = int(os.environ.get('MATCH_MAX_RESULTS', '3'))
CPU_THREADS = int(os.environ.get('CPU_THREADS', '4'))
MAX_IMAGE_BYTES = 20 * 1024 * 1024  # AI 팀 pipeline.extract 상한과 같다

log = logging.getLogger('match-worker')

DDL = """
CREATE TABLE IF NOT EXISTS ai.reid_embeddings (
    target_type varchar(20)  NOT NULL,
    target_seq  bigint       NOT NULL,
    image_key   varchar(500) NOT NULL,
    species     varchar(10)  NOT NULL,
    model_id    varchar(100) NOT NULL,
    route       varchar(10)  NOT NULL,
    embedding   vector(512)  NOT NULL,
    reg_dttm    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (target_type, target_seq, image_key, route)
)
"""

# 원본 사진의 route: 고양이는 얼굴 벡터가 있으면 face, 없으면 body. 개는 body 뿐이다.
# 그 route 끼리만, 같은 종·model_id 의 후보 벡터와 비교하고 사진 쌍 중 최고 점수를 후보 점수로 쓴다(AI 팀 CosineIndex 규칙).
SCORE_SQL = """
WITH q AS (
    SELECT s.route, s.embedding FROM ai.reid_embeddings s
    WHERE s.target_type = %(source_type)s AND s.target_seq = %(source_seq)s AND s.model_id = %(model_id)s
      AND (s.route = 'face' OR NOT EXISTS (
            SELECT 1 FROM ai.reid_embeddings f
            WHERE f.target_type = s.target_type AND f.target_seq = s.target_seq
              AND f.image_key = s.image_key AND f.model_id = s.model_id AND f.route = 'face'))
), c AS (
    SELECT * FROM unnest(%(cand_types)s::text[], %(cand_seqs)s::bigint[]) AS c(target_type, target_seq)
)
SELECT e.target_type, e.target_seq, max(1 - (e.embedding <=> q.embedding)) AS score
FROM q
JOIN ai.reid_embeddings e ON e.route = q.route AND e.species = %(species)s AND e.model_id = %(model_id)s
JOIN c ON c.target_type = e.target_type AND c.target_seq = e.target_seq
GROUP BY e.target_type, e.target_seq
ORDER BY score DESC, e.target_type, e.target_seq
"""


def init_db(conn):
    if conn.execute("SELECT 1 FROM pg_extension WHERE extname = 'vector'").fetchone() is None:
        sys.exit('pgvector 확장이 없다. 관리자 계정으로 CREATE EXTENSION vector 를 먼저 실행해야 한다'
                 '(infra/scripts/create-ai-schema.sh)')
    conn.execute(DDL)


def to_vector(values):
    return '[' + ','.join(repr(float(v)) for v in values) + ']'


def store_feature(conn, target_type, target_seq, image_key, feature):
    """사진 한 장의 벡터를 route 별로 저장한다. 그 사진의 예전 행(이전 모델 등)은 지우고 새로 넣는다."""
    conn.execute('DELETE FROM ai.reid_embeddings WHERE target_type = %s AND target_seq = %s AND image_key = %s',
                 (target_type, target_seq, image_key))
    for route, vector in feature['vectors'].items():
        conn.execute('INSERT INTO ai.reid_embeddings (target_type, target_seq, image_key, species, model_id, route, embedding)'
                     ' VALUES (%s, %s, %s, %s, %s, %s, %s::vector)',
                     (target_type, target_seq, image_key, feature['species'], feature['model_id'], route,
                      to_vector(vector)))


def scores(conn, source_type, source_seq, candidates, species=SPECIES, model_id=MODEL_ID):
    """[(target_type, target_seq, score)] 점수 높은 순. 후보 벡터가 없거나 원본 벡터가 없으면 빈 목록."""
    if not candidates:
        return []
    rows = conn.execute(SCORE_SQL, {
        'source_type': source_type, 'source_seq': source_seq, 'species': species, 'model_id': model_id,
        'cand_types': [c[0] for c in candidates], 'cand_seqs': [c[1] for c in candidates],
    }).fetchall()
    return [(t, s, max(-1.0, min(1.0, float(score)))) for t, s, score in rows]  # CosineIndex 처럼 [-1,1]로 자른다


def download(key):
    with urllib.request.urlopen(IMAGE_URL_PREFIX + key, timeout=10) as response:
        data = response.read(MAX_IMAGE_BYTES + 1)
    if len(data) > MAX_IMAGE_BYTES:
        raise ValueError('20 MiB 초과')
    return data


def valid_candidates(source_type, raw):
    """방향에 맞는 후보만 남긴다. MISSING 원본의 후보는 FOUND·SHELTER_ANIMAL, 그 밖의 원본은 MISSING."""
    allowed = ('FOUND', 'SHELTER_ANIMAL') if source_type == 'MISSING' else ('MISSING',)
    result = []
    for c in raw or []:
        if c.get('type') in allowed and isinstance(c.get('seq'), int):
            result.append((c['type'], c['seq']))
        else:
            log.warning('방향에 맞지 않는 후보는 뺀다: source=%s candidate=%s', source_type, c)
    return list(dict.fromkeys(result))


def handle(conn, pipe, msg):
    """메시지 한 건을 처리하고 발행할 AiMatchFound 본문 목록을 돌려준다."""
    if str(msg.get('speciesCode', '')).lower() != SPECIES:
        return []
    source_type, source_seq = msg['sourceType'], int(msg['sourceSeq'])
    keys = list(dict.fromkeys(msg.get('imageKeys') or []))
    candidates = valid_candidates(source_type, msg.get('candidates'))

    with conn.transaction():
        conn.execute('DELETE FROM ai.reid_embeddings WHERE target_type = %s AND target_seq = %s'
                     ' AND NOT (image_key = ANY(%s))', (source_type, source_seq, keys))
        done = {r[0] for r in conn.execute(
            'SELECT DISTINCT image_key FROM ai.reid_embeddings WHERE target_type = %s AND target_seq = %s'
            ' AND model_id = %s', (source_type, source_seq, MODEL_ID))}
    for key in keys:
        if key in done:
            continue
        try:
            data = download(key)
        except Exception as exc:
            log.warning('사진을 내려받지 못해 건너뛴다: %s/%s %s (%s)', source_type, source_seq, key, exc)
            continue
        started = time.perf_counter()
        try:
            feature = pipe.extract(data)
        except ImageRejected as exc:
            log.info('사진이 거절돼 건너뛴다: %s/%s %s (%s)', source_type, source_seq, key, exc)
            continue
        with conn.transaction():
            store_feature(conn, source_type, source_seq, key, feature)
        log.info('벡터 저장: %s/%s %s route=%s %.2fs', source_type, source_seq, key, feature['route'],
                 time.perf_counter() - started)

    results = [s for s in scores(conn, source_type, source_seq, candidates) if s[2] >= MATCH_MIN_SCORE]
    log.info('비교: %s/%s 후보 %d개, 기준 이상 %d개 %s', source_type, source_seq, len(candidates), len(results),
             [(t, s, round(v, 4)) for t, s, v in results[:10]])
    out = []
    for target_type, target_seq, score in results[:MATCH_MAX_RESULTS]:
        if source_type == 'MISSING':
            out.append({'missingReportSeq': source_seq, 'targetType': target_type, 'targetSeq': target_seq,
                        'score': score, 'modelVersion': MODEL_ID})
        else:
            out.append({'missingReportSeq': target_seq, 'targetType': source_type, 'targetSeq': source_seq,
                        'score': score, 'modelVersion': MODEL_ID})
    return out


def warm_up(pipe):
    """모델을 기동 때 올려(가중치 SHA256 검사 포함) 첫 메시지가 느리거나 늦게 실패하지 않게 한다."""
    from PIL import Image
    buffer = io.BytesIO()
    Image.new('RGB', (256, 256), (128, 128, 128)).save(buffer, 'PNG')
    try:
        pipe.extract(buffer.getvalue())
    except ImageRejected:
        pass  # 회색 빈 사진이라 개를 못 찾는 것이 정상


def main():
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s %(message)s')
    logging.getLogger('pika').setLevel(logging.WARNING)
    conn = psycopg.connect(host=os.environ['DB_HOST'], port=os.environ.get('DB_PORT', '5432'),
                           dbname=os.environ['DB_NAME'], user=os.environ['DB_USERNAME'],
                           password=os.environ['DB_PASSWORD'], autocommit=True)
    init_db(conn)

    started = time.perf_counter()
    pipe = ReIDPipeline(device='cpu')
    warm_up(pipe)
    import torch
    torch.set_num_threads(CPU_THREADS)  # AI 팀 코드가 4로 고정한다. 고양이 ONNX 세션은 4 고정 그대로
    log.info('모델 준비: species=%s model_id=%s %.1fs', SPECIES, MODEL_ID, time.perf_counter() - started)

    credentials = pika.PlainCredentials(os.environ['RABBITMQ_USERNAME'], os.environ['RABBITMQ_PASSWORD'])
    # 사진 여러 장 처리 중에는 하트비트를 못 보내므로 넉넉히 둔다
    params = pika.ConnectionParameters(os.environ['RABBITMQ_HOST'], int(os.environ.get('RABBITMQ_PORT', '5672')),
                                       credentials=credentials, heartbeat=600)
    channel = pika.BlockingConnection(params).channel()
    channel.exchange_declare(EXCHANGE, 'topic', durable=True)
    channel.queue_declare(QUEUE, durable=True)
    channel.queue_bind(QUEUE, EXCHANGE, routing_key='MatchRequested')
    channel.basic_qos(prefetch_count=1)

    def on_message(ch, method, _properties, body):
        try:
            msg = json.loads(body)
            results = handle(conn, pipe, msg)
        except psycopg.OperationalError:
            raise  # DB 연결 문제는 메시지 탓이 아니다. 프로세스를 끝내 재시작 후 다시 받는다(ack 전이라 남아 있다)
        except Exception:
            # 같은 메시지가 무한히 재전달되지 않게 버린다
            log.exception('처리 실패, 메시지를 버린다: %s', body[:1000])
            ch.basic_nack(method.delivery_tag, requeue=False)
            return
        for result in results:
            ch.basic_publish(EXCHANGE, 'AiMatchFound', json.dumps(result).encode(), pika.BasicProperties(
                content_type='application/json', content_encoding='UTF-8', delivery_mode=2,
                headers={'__TypeId__': RESULT_TYPE_ID}))
            log.info('AiMatchFound 발행: %s', result)
        ch.basic_ack(method.delivery_tag)

    channel.basic_consume(QUEUE, on_message)
    log.info('소비 시작: %s', QUEUE)
    channel.start_consuming()


if __name__ == '__main__':
    main()
