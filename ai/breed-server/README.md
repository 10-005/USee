# breed-server

발견 신고 사진(1~3장)에서 종(개·고양이)과 품종을 추천하는 내부 서버. core-api만 부르고, 서비스 DB는 모른다.
품종은 AI 팀 라벨(`maltese` 등)로 돌려주며 품종 코드 변환은 core-api가 한다.

## 모델

| 역할 | 모델 | 출처 |
|---|---|---|
| 종 판별 | CLIP ViT-B/32 제로샷(개 / 고양이 / 둘 다 아님) | Hugging Face `openai/clip-vit-base-patch32`(빌드 때 내려받음) |
| 개 품종 15종 | ConvNeXt-Tiny | AI 팀 전달본 `01_dog_breed/models` |
| 고양이 품종 15종 | ConvNeXt-Tiny | AI 팀 전달본 `02_cat_breed/models` |

`breed_pipeline.py`는 AI 팀 `pipeline.py`를 종만 인자로 바꾼 것이다(전처리·추론 그대로). GPU는 필요 없다.

품종 모델은 15종 중 하나를 고르는 폐쇄형이라 믹스·미학습 품종도 15종 중 하나로 답한다. 결과는 추천일 뿐이고 사용자가 확인한다.

## 합치기 규칙 (`policy.py`)

1. 사진마다 종 점수(개·고양이)를 구한다. 높은 쪽이 `SPECIES_MIN_SCORE` 미만이면 그 사진은 뺀다.
2. 종은 남은 사진의 다수결, 동률이면 먼저 올린 사진. 남은 사진이 없으면 `species=null`.
3. 그 종으로 판정된 사진만 품종 모델에 넣고 확률을 평균한다.
4. 1등 평균이 `BREED_MIN_SCORE` 미만이면 `breed=null`. 상위 3개 후보는 항상 준다.

두 기준값은 **가안**이다(검증 데이터로 정하지 않음).

## API

`POST /v1/breed-predictions`

```json
{ "imageUrls": ["https://.../images/a.jpg", "https://.../images/b.jpg"] }
```

```json
{
  "species": "dog",
  "breed": "maltese",
  "candidates": [
    { "breed": "maltese", "score": 0.69 },
    { "breed": "maltipoo", "score": 0.29 },
    { "breed": "bichon_frise", "score": 0.005 }
  ],
  "images": [
    { "species": "dog", "speciesScore": 0.99, "error": null },
    { "species": null, "speciesScore": 0.03, "error": null }
  ],
  "modelVersion": "dog-convnext_mix,cat-convnext_weighted,clip-vit-base-patch32"
}
```

- `species`: `dog` / `cat` / `null`(개·고양이를 못 찾음). `breed`: 라벨 또는 `null`(확신 없음).
- 내려받지 못하거나 읽지 못한 사진은 `images[].error`에 코드만 남기고 건너뛴다(`URL_NOT_ALLOWED`, `IMAGE_FETCH_FAILED`, `IMAGE_TOO_LARGE`, `INVALID_IMAGE`). 요청 자체는 200.
- 사진이 0장이거나 4장 이상이면 422.
- `GET /health` → `{"status":"ok"}`

## 환경 변수

| 이름 | 기본값 | 뜻 |
|---|---|---|
| `MODEL_DIR` | `./models` | `dog/`, `cat/` 아래에 AI 팀 `models` 폴더 내용 |
| `IMAGE_URL_PREFIX` | (없음) | 내려받을 수 있는 사진 주소의 앞부분. 배포에서는 이미지 버킷 주소로 꼭 설정 |
| `SPECIES_MIN_SCORE` | `0.5` | 종 판정 기준 [가안] |
| `BREED_MIN_SCORE` | `0.6` | 품종 자동 입력 기준 [가안] |
| `CPU_THREADS` | `2` | torch CPU 스레드 수 |

## 로컬 실행

모델 가중치(약 220MB)는 git에 넣지 않는다. AI 팀 전달 묶음에서 복사한다.

```bash
mkdir -p models/dog models/cat
cp <전달 묶음>/01_dog_breed/models/* models/dog/
cp <전달 묶음>/02_cat_breed/models/* models/cat/

uv venv --python 3.12 .venv
uv pip install --python .venv -r requirements.txt
.venv/Scripts/python test_policy.py          # 합치기 규칙 검사(모델 불필요)
.venv/Scripts/python -m uvicorn app:app --port 8000
```

Docker: `docker build -t breed-server .` (빌드 전에 `models/`가 있어야 한다)

## 속도 (2026-11-11, CPU 스레드 2개 기준)

종 판별 약 0.3초/장, 품종 약 0.5초/장(개발 PC). 서버 노드 1에서는 종 0.75초, 품종 0.21초였다. 사진 3장이면 3초 안쪽.

2,500만 화소가 넘는 사진은 AI 팀 코드가 거부하므로 서버가 먼저 줄여서 넣는다.
