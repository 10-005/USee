"""종 판별. 품종 모델은 종을 가리지 못하고 동물이 아닌 사진도 거절하지 못하므로(AI 팀 README 6절),
그 앞에서 개/고양이/둘 다 아님을 정한다. 별도 학습 없이 공개 사전학습 가중치 두 개를 함께 쓴다.

- COCO 탐지(torchvision Faster R-CNN MobileNetV3 320): 사진 속 개·고양이 상자의 최고 점수
- ImageNet 분류(timm MobileNetV3): 개(151~268)·고양이(281~285) 확률의 합

종별로 둘 중 높은 점수를 쓴다. 2026-11-11 더미 사진 62장(스레드 2개)에서 탐지만 쓰면 개 1장을 고양이로,
분류만 쓰면 고양이 10장을 0.5 미만으로 냈고, 함께 쓰면 62장 모두 맞았다(장당 약 0.2초).
"""
from PIL import ImageOps
import timm
from timm.data import create_transform, resolve_data_config
import torch
from torchvision.models import detection

COCO_CAT, COCO_DOG = 17, 18
IMAGENET_DOG = list(range(151, 269))  # Chihuahua ~ Mexican hairless
IMAGENET_CAT = list(range(281, 286))  # tabby, tiger cat, Persian, Siamese, Egyptian cat
CLASSIFIER = "mobilenetv3_large_100.ra_in1k"
VERSION = f"frcnn_mnv3_320+{CLASSIFIER}"


class SpeciesClassifier:
    def __init__(self):
        weights = detection.FasterRCNN_MobileNet_V3_Large_320_FPN_Weights.DEFAULT
        self.detector = detection.fasterrcnn_mobilenet_v3_large_320_fpn(weights=weights, box_score_thresh=0.05).eval()
        self.detector_transform = weights.transforms()
        self.classifier = timm.create_model(CLASSIFIER, pretrained=True).eval()
        self.classifier_transform = create_transform(**resolve_data_config({}, model=self.classifier))

    def predict(self, image):
        """PIL 이미지 → {"dog": 점수, "cat": 점수} (0~1). 둘 다 낮으면 개·고양이가 없는 사진이다."""
        image = ImageOps.exif_transpose(image).convert("RGB")
        with torch.inference_mode():
            found = self.detector([self.detector_transform(image)])[0]
            probabilities = self.classifier(self.classifier_transform(image).unsqueeze(0)).softmax(dim=1)[0]
        scores = {"dog": probabilities[IMAGENET_DOG].sum().item(), "cat": probabilities[IMAGENET_CAT].sum().item()}
        for label, score in zip(found["labels"].tolist(), found["scores"].tolist()):
            species = {COCO_DOG: "dog", COCO_CAT: "cat"}.get(label)
            if species:
                scores[species] = max(scores[species], score)
        return scores
