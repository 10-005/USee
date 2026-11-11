# breed-server

발견 신고 사진(1~3장)에서 종(개·고양이)과 품종을 추천하는 내부 서버. core-api만 부르고, 서비스 DB는 모른다.
품종은 AI 팀 한글 표시 이름(`display_names.json`, `말티즈` 등)으로 돌려주고, core-api가 그 이름으로 품종표를 찾는다. 영문 라벨(`maltese` 등)은 `label`로 함께 준다(로그·디버깅용).

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
4. 1등 평균이 `BREED_HIGH_SCORE`(0.8) 이상이면 `confidence=HIGH`(채움), `BREED_MIN_SCORE`(0.5) 이상이면 `LOW`(채우되 앱이 "~인 것 같아요"로 표시), 미만이면 `breed=null`·`confidence=null`. 상위 3개 후보는 항상 준다.

## API

`POST /v1/breed-predictions`

```json
{ "imageUrls": ["https://.../images/a.jpg", "https://.../images/b.jpg"] }
```

```json
{
  "species": "dog",
  "breed": "말티즈",
  "label": "maltese",
  "confidence": "LOW",
  "candidates": [
    { "breed": "말티즈", "label": "maltese", "score": 0.69 },
    { "breed": "말티푸", "label": "maltipoo", "score": 0.29 },
    { "breed": "비숑 프리제", "label": "bichon_frise", "score": 0.005 }
  ],
  "images": [
    { "species": "dog", "speciesScore": 0.99, "error": null },
    { "species": null, "speciesScore": 0.03, "error": null }
  ],
  "modelVersion": "dog-convnext_mix,cat-convnext_weighted,clip-vit-base-patch32"
}
```

- `species`: `dog` / `cat` / `null`(개·고양이를 못 찾음). `breed`: 한글 표시 이름 또는 `null`(확신 없음). `confidence`: `HIGH` / `LOW` / `null`.
- 내려받지 못하거나 읽지 못한 사진은 `images[].error`에 코드만 남기고 건너뛴다(`URL_NOT_ALLOWED`, `IMAGE_FETCH_FAILED`, `IMAGE_TOO_LARGE`, `INVALID_IMAGE`). 요청 자체는 200.
- 사진이 0장이거나 4장 이상이면 422.
- `GET /health` → `{"status":"ok"}`

## 환경 변수

| 이름 | 기본값 | 뜻 |
|---|---|---|
| `MODEL_DIR` | `./models` | `dog/`, `cat/` 아래에 AI 팀 `models` 폴더 내용 |
| `IMAGE_URL_PREFIX` | (없음) | 내려받을 수 있는 사진 주소의 앞부분. 배포에서는 이미지 버킷 주소로 꼭 설정 |
| `SPECIES_MIN_SCORE` | `0.5` | 종 판정 기준 |
| `BREED_HIGH_SCORE` | `0.8` | 품종 `HIGH` 기준 |
| `BREED_MIN_SCORE` | `0.5` | 품종 `LOW` 기준(미만이면 비움) |
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

이미지 크기 약 2.2GB(2026-11-11, `docker history` 층 합계: torch 등 패키지 1.27GB, CLIP 가중치 611MB, 품종 모델 223MB, 베이스 약 130MB). 레지스트리 전송량(압축)은 약 1.0GB.

## 속도 (2026-11-11, CPU 스레드 2개 기준)

종 판별 약 0.3초/장, 품종 약 0.5초/장(개발 PC). 서버 노드 1에서는 종 0.75초, 품종 0.21초였다. 사진 3장이면 3초 안쪽.

2,500만 화소가 넘는 사진은 AI 팀 코드가 거부하므로 서버가 먼저 줄여서 넣는다.
