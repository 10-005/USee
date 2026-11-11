"""발견 신고 사진 → 종·품종 추천 서버. 클러스터 내부에서 core-api만 부른다.

실행: uvicorn app:app --host 0.0.0.0 --port 8000
이 서버는 서비스 DB를 모른다. 품종은 AI 팀 한글 표시 이름(display_names.json, 말티즈 등)으로 돌려주고,
core-api가 그 이름으로 품종표를 찾는다. 영문 라벨(maltese 등)은 label로 함께 준다(로그·디버깅용).
"""
from contextlib import asynccontextmanager
import io
import os
from pathlib import Path

from fastapi import FastAPI
import httpx
from PIL import Image
from pydantic import BaseModel, Field

from breed_pipeline import MAX_PIXELS, SUPPORTED_FORMATS, BreedClassifier, PipelineError, decode_image
from policy import decide_breed, decide_species
from species import VERSION as SPECIES_VERSION, SpeciesClassifier

MODEL_DIR = Path(os.environ.get("MODEL_DIR", Path(__file__).parent / "models"))
# 종은 0.5 이상이면 그 종. 품종 평균 1등이 0.8 이상이면 HIGH(채움), 0.5 이상이면 LOW(채우되 앱이 "~인 것 같아요"), 미만이면 비움
SPECIES_MIN_SCORE = float(os.environ.get("SPECIES_MIN_SCORE", "0.5"))
BREED_HIGH_SCORE = float(os.environ.get("BREED_HIGH_SCORE", "0.8"))
BREED_MIN_SCORE = float(os.environ.get("BREED_MIN_SCORE", "0.5"))
CPU_THREADS = int(os.environ.get("CPU_THREADS", "2"))
# 내려받을 수 있는 사진 주소의 앞부분(우리 이미지 버킷). 비우면 검사하지 않는다(로컬 실행용)
IMAGE_URL_PREFIX = os.environ.get("IMAGE_URL_PREFIX", "")
MAX_IMAGE_BYTES = 15 * 1024 * 1024

models = {}


@asynccontextmanager
async def lifespan(_app):
    # 모델은 시작할 때 한 번만 읽는다. 파일이 없거나 해시가 다르면 여기서 죽어 배포가 실패로 드러난다
    for species in ("dog", "cat"):
        classifier = BreedClassifier(species, MODEL_DIR / species, cpu_threads=CPU_THREADS)
        classifier._load()
        models[species] = classifier
    models["species"] = SpeciesClassifier()
    yield


app = FastAPI(title="breed-server", lifespan=lifespan)


class PredictionRequest(BaseModel):
    imageUrls: list[str] = Field(min_length=1, max_length=3)


class Candidate(BaseModel):
    breed: str  # 한글 표시 이름
    label: str  # AI 팀 영문 라벨
    score: float


class ImageResult(BaseModel):
    species: str | None
    speciesScore: float | None
    error: str | None = None


class PredictionResponse(BaseModel):
    species: str | None
    breed: str | None
    label: str | None
    confidence: str | None  # HIGH / LOW / None(품종 비움)
    candidates: list[Candidate]
    images: list[ImageResult]
    modelVersion: str


def shrink_if_huge(payload):
    """AI 팀 decode_image는 2,500만 화소 초과를 거부한다. 폰의 고화소 사진은 먼저 줄여서 넘긴다(모델 입력은 384·224라 손실 없음).

    기준 이하면 원본 바이트를 그대로 돌려줘 AI 팀 경로를 건드리지 않는다.
    """
    try:
        image = Image.open(io.BytesIO(payload))
        if image.width * image.height <= MAX_PIXELS:
            return payload
        if image.format not in SUPPORTED_FORMATS:
            raise PipelineError("INVALID_IMAGE", "Supported formats: JPEG, PNG, WEBP.")
        image.thumbnail((4096, 4096))
        return image
    except PipelineError:
        raise
    except Exception as exc:  # 깨진 파일, 압축 폭탄 등
        raise PipelineError("INVALID_IMAGE", "Cannot decode the image safely.") from exc


def fetch_image(url):
    """사진을 내려받아 PIL 이미지로. 실패하면 오류 코드 문자열을 돌려준다(그 사진만 건너뛴다)."""
    if not url.startswith(IMAGE_URL_PREFIX):
        return "URL_NOT_ALLOWED"
    try:
        with httpx.stream("GET", url, timeout=5.0) as response:
            response.raise_for_status()
            payload = b""
            for chunk in response.iter_bytes():
                payload += chunk
                if len(payload) > MAX_IMAGE_BYTES:
                    return "IMAGE_TOO_LARGE"
        return decode_image(shrink_if_huge(payload))
    except PipelineError as exc:
        return exc.code
    except httpx.HTTPError:
        return "IMAGE_FETCH_FAILED"


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/v1/breed-predictions")
def predict(request: PredictionRequest) -> PredictionResponse:
    images = [fetch_image(url) for url in request.imageUrls]
    species_scores = [None if isinstance(image, str) else models["species"].predict(image) for image in images]
    species, labels = decide_species(species_scores, SPECIES_MIN_SCORE)

    breed, confidence, candidates = None, None, []
    if species:
        # 정해진 종으로 판정된 사진만 품종 모델에 넣고 확률을 평균한다
        breed_scores = [
            {p["breed"]: p["score"] for p in models[species].predict(image, top_k=15)["predictions"]}
            for image, label in zip(images, labels) if label == species
        ]
        breed, confidence, candidates = decide_breed(breed_scores, BREED_HIGH_SCORE, BREED_MIN_SCORE)
    names = models[species].names if species else {}

    return PredictionResponse(
        species=species,
        breed=names.get(breed),
        label=breed,
        confidence=confidence,
        candidates=[Candidate(breed=names[label], label=label, score=score) for label, score in candidates],
        images=[
            ImageResult(species=label, speciesScore=max(scores.values()) if scores else None,
                        error=image if isinstance(image, str) else None)
            for image, scores, label in zip(images, species_scores, labels)
        ],
        modelVersion=f"dog-{models['dog'].config['method_id']},cat-{models['cat'].config['method_id']},{SPECIES_VERSION}",
    )
