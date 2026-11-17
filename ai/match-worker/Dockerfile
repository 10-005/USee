# 빌드 전에 dog/models, cat/models 에 AI 팀 가중치가 있어야 한다(README 참고). GPU 없이 CPU로만 돈다
# 같은 이미지로 SPECIES=dog, SPECIES=cat 워커를 따로 띄운다
FROM python:3.12-slim
WORKDIR /app

# CUDA 묶음이 딸려 오지 않게 torch는 CPU 전용 인덱스에서 먼저 받는다
# ultralytics 가 끌어오는 opencv-python 은 libGL 이 필요해 같은 버전의 headless 로 바꾼다(화면 기능은 쓰지 않는다)
COPY requirements.txt .
RUN pip install --no-cache-dir torch==2.14.1 torchvision==0.29.1 --index-url https://download.pytorch.org/whl/cpu \
    && pip install --no-cache-dir -r requirements.txt \
    && OPENCV=$(pip show opencv-python | sed -n 's/^Version: //p') \
    && pip uninstall -y opencv-python \
    && pip install --no-cache-dir "opencv-python-headless==$OPENCV"

# 실행 중 외부 요청 금지: Ultralytics(이벤트 전송·자동 설치·다운로드), Hugging Face
ENV YOLO_OFFLINE=true YOLO_AUTOINSTALL=false HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 PYTHONUNBUFFERED=1

# AI 팀 코드와 가중치(가중치가 커서 자주 바뀌는 worker.py 보다 먼저 둔다)
COPY dog/ dog/
COPY cat/ cat/
COPY worker.py .

CMD ["python", "worker.py"]
