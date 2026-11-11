# 빌드 전에 models/dog, models/cat 에 AI 팀 가중치가 있어야 한다(README 참고). GPU 없이 CPU로만 돈다
FROM python:3.12-slim
WORKDIR /app

# CUDA 묶음이 딸려 오지 않게 torch는 CPU 전용 인덱스에서 먼저 받는다
COPY requirements.txt .
RUN pip install --no-cache-dir torch==2.14.1 torchvision==0.29.1 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt

# 종 판별용 공개 가중치는 빌드할 때 받아 이미지에 넣는다(실행 중 외부 다운로드 없음)
ENV HF_HOME=/app/.cache/hf TORCH_HOME=/app/.cache/torch
COPY species.py .
RUN python -c "from species import SpeciesClassifier; SpeciesClassifier()"

COPY breed_pipeline.py policy.py app.py ./
COPY models/ models/

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
