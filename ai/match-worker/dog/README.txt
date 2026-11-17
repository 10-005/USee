DOG 개체 인식 Python 전달 패키지 (2026-11-10)

이 폴더만 다른 위치에 복사하여 사용할 수 있습니다. 다른 프로젝트 경로, 웹 서버, DB, API 키,
Jupyter 노트북이 필요하지 않습니다. models/에 필요한 모든 추론 가중치를 포함했습니다.
Python 3.12 권장. 원본 웹사이트/릴리스 파일은 수정하지 않았습니다.

1. 설치 및 실행
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux: source .venv/bin/activate
python -m pip install -r requirements.txt
python run.py extract --image animal.jpg --device cpu --output query.json
python run.py extract --image animal.jpg --device cpu --artifacts debug_images --output query.json

CPU가 명시적 기본값입니다. CUDA 사용 시 별도 환경에서 requirements-cuda.txt를 설치하고
--device cuda:1을 사용합니다. 물리 GPU 1만 허용하며 CUDA_VISIBLE_DEVICES 재매핑이나
CPU/다른 GPU로의 자동 전환은 없습니다. 이 GPU 환경은 원본 릴리스의 검증 사양이며
이번 Windows CPU 전달 검증에서 GPU 추론을 다시 수행한 것은 아닙니다.
패키지 설치용 인터넷은 필요할 수 있지만 추론 시 모델 다운로드는 하지 않습니다.
새 빈 가상환경의 전체 설치는 별도 검증 사항입니다. validation/ 결과를 확인하세요.

2. 등록과 검색 (같은 개체의 다른 사진도 등록 가능)
python run.py register --image registered.jpg --pet-id dog-001 --photo-id dog-001-01 --output record.json
record.json은 등록 레코드 한 개입니다. 여러 레코드를 JSON 배열로 묶어 gallery.json에 저장하세요.
python run.py extract --image query.jpg --output query.json
python run.py search --query query.json --gallery gallery.json --top-k 5
python run.py compare --query query.json --reference registered_feature.json
등록 사진과 검색 사진은 같은 파이프라인을 사용해야 합니다. 등록 사진 원본/학습 데이터는 미포함입니다.

3. Python API
from pipeline import ReIDPipeline
pipe = ReIDPipeline(device='cpu')  # 반복 호출할 때 이 객체를 재사용하세요.
feature = pipe.extract('animal.jpg')
row = pipe.registration('registered.jpg', pet_id='dog-001', photo_id='dog-001-01')
result = pipe.search(feature, [row], top_k=5)
pair = pipe.compare(feature, row['embedding'])

반복 검색은 from petid_cosine import CosineIndex; index = CosineIndex(records) 후
index.search(feature, top_k=5) 형태로 메모리 인덱스를 재사용할 수 있습니다.
개·고양이 독립 폴더는 각각 별도 Python 프로세스/환경에서 사용하는 구성을 권장합니다.
같은 이름의 pipeline 및 공식 cat_recognition 모듈을 다른 경로에서 중복 import하지 마세요.

4. 현재 배포와 동일한 처리
YOLO11m-seg로 개(COCO class16)를 찾고 단일 개 검사를 합니다.
confidence0.25, IoU0.5, imgsz640; 탐지 박스 6% 여백, mask>0.5로 배경을 검게 제거합니다.
body는 검정 letterbox256 + 기존 정규화 후 BowwowID에서 512차원 특징을 추출합니다.
개가 없거나 여러 마리거나 너무 작거나 마스크 면적이 부족하면 오류입니다.
이 최종 BowwowID는 몸 전용입니다. 개 얼굴 랜드마크/얼굴 벡터는 사용하지 않습니다.

5. JSON 계약
extract: schema_version=1, species, model_id, route, vectors, metadata, dimension=512.
feature 벡터는 정규화된 512개 실수입니다. 등록/검색 모두 JSON 전체를 저장하고 전달하세요.
검색은 같은 gallery_id/species/model_id/route끼리 비교합니다.
고양이 query에 face가 있으면 face끼리만, 없으면 body끼리만 비교합니다.
개는 항상 body끼리 비교합니다. 얼굴/몸 점수를 평균하거나 임의로 섞지 않습니다.
같은 개체의 여러 등록 사진은 최대 점수 사진이 대표합니다. 기본 임계값은 없습니다.
score와 cosine_similarity는 [-1,1] 코사인 유사도입니다. 백분율/동일 개체 확률이 아닙니다.
임계값을 사용하려면 별도 검증 후 --min-score를 지정하세요.
query route에 맞는 후보가 없으면 no_candidates이며 compare의 cosine_similarity는 null입니다.

6. 입출력/실패
JPEG, PNG, WEBP만; 최대 20 MiB/25백만 픽셀, 짧은 변 최소 128픽셀.
EXIF 방향 보정 후 RGB 처리. 성공은 stdout JSON 한 개/종료0, 오류는 stderr JSON/종료1.
--output은 같은 성공 JSON을 UTF-8 파일에도 저장합니다. 이미 있는 출력 파일은 덮어씁니다.
CLI에 잘못된 인자를 주면 argparse가 사용법과 종료2를 반환합니다.
모델이 누락/변경되면 SHA256 검사에서 중단하며 자동 다운로드하지 않습니다.

7. 검증/출처
python -m unittest discover -s tests -v
python verify_assets.py
vendor/meowid는 원본 공식 소스의 필요한 Python 패키지를 수정 없이 포함했습니다.
licenses/, vendor_manifest.json, models/provenance.json을 함께 보존하세요.
원본 공개 MeowID 출처: https://huggingface.co/RicePasteM/MeowID-Base
원본 릴리스: MeowID_BowwowID_Pipelines_20261105, PetID_Cosine_Module_20261105.
이 폴더의 adapter.py/common.py/pipeline.py/run.py는 전달용 연결 코드입니다.
