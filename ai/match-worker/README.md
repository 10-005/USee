# match-worker

실종 신고와 발견 신고·보호동물의 사진을 비교해, 같은 동물로 보이면 core-api에 `AiMatchFound`를 보내는 AI 워커(API 명세 17.8·21.6, Q432·Q522). HTTP가 없고 RabbitMQ 메시지만 주고받는다. 서비스 DB(core)는 읽지 않고 자기 스키마 `ai`만 쓴다.

## 구성

| 폴더 | 내용 |
|---|---|
| `dog/` | AI 팀 `04_dog_reid` 그대로(YOLO11m-seg 마스킹 + BowwowID 몸 512차원) |
| `cat/` | AI 팀 `03_cat_reid` 그대로(ECPose 얼굴 정렬 + MeowID-Base 얼굴/몸 512차원) |
| `worker.py` | 메시지 소비, 벡터 저장(pgvector), 비교, 결과 발행 |
| `test_scores.py` | SQL 점수가 AI 팀 `CosineIndex.search` 점수와 같은지 확인 |

`dog/`·`cat/`은 전달본(`Pet_AI_Pipelines_Handoff_20261110`)에서 코드·라이선스·검사 자료를 수정 없이 가져왔다(가중치 `models/`, 실행 산출물 `.runtime/`, 검증 사진 `validation/` 제외). 두 폴더가 같은 이름의 모듈(`pipeline`·`adapter`·`common`·`contract`·`cat_recognition`)을 쓰므로 **한 프로세스에 한 종만** 올린다. 같은 이미지로 `SPECIES=dog`, `SPECIES=cat` 워커를 따로 띄운다. 줄바꿈도 바꾸지 않도록 `.gitattributes`에서 `-text`로 둔다(`vendor/`는 SHA256으로 검사된다).

## 모델

빌드 전에 가중치를 넣는다(git 제외). CI는 S3에서 받는다.

```
dog/models/  ← s3://s15p31d106-media-887324111755/models/reid/20261110/dog/  (전달본 04_dog_reid/models 내용)
    bowwow_letterbox_body.pth, yolo11m-seg.pt, provenance.json
cat/models/  ← s3://s15p31d106-media-887324111755/models/reid/20261110/cat/  (전달본 03_cat_reid/models 내용)
    MeowID-Base/{meowid_base.onnx, ecpose.onnx, deployment.json, SHA256SUMS}, provenance.json
```

AI 팀 코드가 기동 때 SHA256을 검사하고, 다르면 시작하지 않는다. 실행 중에는 외부에서 아무것도 받지 않는다(`YOLO_OFFLINE=true`, `HF_HUB_OFFLINE=1`).

## 메시지 계약

topic 교환기 `animal.events`(durable). 큐 `match-worker.{species}.MatchRequested`(durable)를 라우팅 키 `MatchRequested`로 묶는다. prefetch 1, 처리 후 ack.

요청(core-api → 워커)

```json
{
  "eventType": "MISSING_REPORT_REGISTERED | FOUND_REPORT_REGISTERED | SHELTER_ANIMAL_REGISTERED | IMAGES_CHANGED",
  "sourceType": "MISSING | FOUND | SHELTER_ANIMAL",
  "sourceSeq": 101,
  "imageKeys": ["images/2026/11/a.jpg"],
  "speciesCode": "DOG | CAT | ETC",
  "occurredAt": "2026-11-02T17:50:00+09:00",
  "candidates": [ { "type": "FOUND", "seq": 210 }, { "type": "SHELTER_ANIMAL", "seq": 35 } ]
}
```

결과(워커 → core-api), 라우팅 키 `AiMatchFound`, `content_type: application/json`, 헤더 `__TypeId__: com.ssafy.animal.common.event.AiMatchFound`, 지속 메시지

```json
{ "missingReportSeq": 101, "targetType": "FOUND", "targetSeq": 210, "score": 0.83, "modelVersion": "bowwow-body-b4aab62423f7-mask-letterbox256-v1" }
```

`sourceType`이 `MISSING`이면 `missingReportSeq = sourceSeq`, 대상은 후보. 그 밖이면 `missingReportSeq = 후보 seq`, 대상은 source. `modelVersion`은 AI 팀 `model_id`.

## 처리 순서(메시지 한 건)

1. `speciesCode`가 자기 종이 아니면(다른 종·`ETC`) ack만 한다.
2. 그 대상의 행 중 `imageKeys`에 없는 사진은 지운다. 현재 `model_id`의 벡터가 없는 사진만 `IMAGE_URL_PREFIX + 키`에서 내려받아 벡터로 바꿔 넣는다. 같은 사진은 다시 바꾸지 않는다.
3. 거절된 사진(개 없음·여러 마리·너무 작음 등), 내려받기 실패는 로그만 남기고 건너뛴다. 거절된 사진은 저장하지 않으므로 같은 대상의 요청이 다시 오면 다시 시도한다.
4. `candidates`의 벡터하고만 비교한다. 같은 종·같은 `model_id`·같은 route끼리. 고양이 사진에 얼굴 벡터가 있으면 얼굴끼리, 없으면 몸끼리. 개는 몸끼리. 사진 쌍 중 최고 점수가 그 후보의 점수(AI 팀 `CosineIndex` 규칙).
5. `MATCH_MIN_SCORE` 이상을 높은 순으로 `MATCH_MAX_RESULTS`개까지 발행한다. 후보 방향이 맞지 않는 항목(예: 실종 원본에 실종 후보)은 로그를 남기고 뺀다.
6. 예상하지 못한 예외(잘못된 JSON, 필드 누락 등)는 로그를 남기고 메시지를 버린다(nack, requeue 없음). DB 연결 오류는 메시지 탓이 아니므로 프로세스를 끝낸다(ack 전이라 재시작 후 다시 받는다).

## 저장소(pgvector)

```sql
CREATE TABLE IF NOT EXISTS ai.reid_embeddings (
    target_type varchar(20)  NOT NULL,   -- MISSING | FOUND | SHELTER_ANIMAL
    target_seq  bigint       NOT NULL,
    image_key   varchar(500) NOT NULL,
    species     varchar(10)  NOT NULL,   -- dog | cat
    model_id    varchar(100) NOT NULL,
    route       varchar(10)  NOT NULL,   -- body | face
    embedding   vector(512)  NOT NULL,
    reg_dttm    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (target_type, target_seq, image_key, route)
)
```

- 워커가 기동 때 멱등으로 만든다. 스키마 `ai`(소유자 `ai_app`)와 확장 `vector`는 관리자가 먼저 만든다(`infra/scripts/create-ai-schema.sh`). 확장이 없으면 그 내용을 알리고 종료한다.
- 고양이 얼굴 사진은 `face`·`body` 두 행, 그 밖은 `body` 한 행. 신고 정보(주소·날짜·작성자)는 저장하지 않는다.
- 유사도는 SQL 코사인(`1 - (a <=> b)`). 후보가 최대 500개라 근사 검색 인덱스는 두지 않는다.

## 환경 변수

| 이름 | 기본값 | 뜻 |
|---|---|---|
| `SPECIES` | (필수) | `dog` 또는 `cat` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | - / 5672 / - / - | RabbitMQ |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | - / 5432 / - / - / - | Postgres(`ai_app`) |
| `IMAGE_URL_PREFIX` | 빈 값 | 사진 공개 주소 앞부분. 이 값 + `imageKeys` 항목으로 내려받는다 |
| `MATCH_MIN_SCORE` | 0.6 | 발행 기준 코사인(가안, 사진 3쌍에서 정함). 같은 개체일 확률이 아니다 |
| `MATCH_MAX_RESULTS` | 3 | 한 요청에서 보내는 결과 수(가안) |
| `CPU_THREADS` | 4 | torch 스레드 수. 고양이 ONNX 세션은 AI 팀 코드가 4로 고정 |

프로브: HTTP가 없다. 프로세스 생존만 본다(모델 로딩·DB·RabbitMQ 연결이 실패하면 프로세스가 끝난다).

## 실측(2026-11-12, 개발 PC Docker, 22코어)

| | 개 | 고양이 |
|---|---|---|
| 모델 준비(기동) | 5.3초 | 7.2초 |
| 사진 1장 벡터 변환(중앙값, 최소~최대) | 0.79초 (0.43~1.04) | 1.51초 (0.97~1.60) |
| 최대 메모리(cgroup `memory.peak`) | 약 1.04GB | 약 1.98GB |

이미지 층 합계 약 3.3GB(의존성 1.9GB, 고양이 가중치·코드 910MB, 개 394MB).

## 로컬 실행

```bash
uv venv --python 3.12 .venv
uv pip install --python .venv torch==2.14.1 torchvision==0.29.1 --index-url https://download.pytorch.org/whl/cpu
uv pip install --python .venv -r requirements.txt
# dog/models, cat/models 에 가중치를 넣고, pgvector Postgres(pgvector/pgvector:pg17)·RabbitMQ 를 띄운 뒤
SPECIES=dog DB_HOST=... RABBITMQ_HOST=... IMAGE_URL_PREFIX=http://localhost:8000/ .venv/Scripts/python worker.py

# 점수 일치 테스트(가중치 불필요, 스키마 ai 와 확장 vector 가 있어야 한다)
SPECIES=cat DB_HOST=... DB_NAME=... DB_USERNAME=... DB_PASSWORD=... .venv/Scripts/python -m unittest test_scores -v
```

이미지: `docker build -t match-worker ai/match-worker`(가중치를 넣은 뒤).

## 라이선스

Ultralytics YOLO는 AGPL-3.0, MeowID는 GPL-2.0, DINOv3는 Meta의 별도 라이선스다. `dog/licenses/`, `cat/licenses/`, `*/vendor_manifest.json`, `models/provenance.json`을 함께 보존한다.
