"""종 판별. 품종 모델은 종을 가리지 못하고 동물이 아닌 사진도 거절하지 못하므로(AI 팀 README 6절),
그 앞에서 개/고양이/둘 다 아님을 정한다.

Hugging Face `openai/clip-vit-base-patch32`를 학습 없이 그대로 쓴다(제로샷). 사진이 아래 세 문장 중 어디에
가장 가까운지를 확률로 낸다. 문장 쪽 계산은 시작할 때 한 번만 하고, 사진마다 이미지 쪽만 돌린다.

2026-11-11 더미 사진 62장 + 동물 없는 사진 4장(CPU 스레드 2개. 정답 라벨이 있는 평가가 아니라 다른 모델 4개의 판정과 대조하고 엇갈린 사진만 눈으로 확인):
62장 모두 종이 맞았고(개 44·고양이 18), 동물 없는 사진 4장은 모두 거절. 장당 약 0.3초.
"""
from PIL import ImageOps
import torch
from transformers import CLIPModel, CLIPProcessor

MODEL = "openai/clip-vit-base-patch32"
VERSION = "clip-vit-base-patch32"
PROMPTS = {"dog": "a photo of a dog", "cat": "a photo of a cat", "none": "a photo with no dog or cat"}


class SpeciesClassifier:
    def __init__(self):
        self.model = CLIPModel.from_pretrained(MODEL).eval()
        self.processor = CLIPProcessor.from_pretrained(MODEL)
        with torch.inference_mode():
            text = self.model.get_text_features(**self.processor(text=list(PROMPTS.values()), return_tensors="pt", padding=True))
        self.text = self._unit(text)

    @staticmethod
    def _unit(features):
        # transformers 버전에 따라 텐서 또는 pooler_output을 가진 객체가 온다
        features = getattr(features, "pooler_output", features)
        return features / features.norm(dim=-1, keepdim=True)

    def predict(self, image):
        """PIL 이미지 → {"dog": 확률, "cat": 확률}. 나머지 확률은 '둘 다 아님'이다."""
        image = ImageOps.exif_transpose(image).convert("RGB")
        with torch.inference_mode():
            features = self._unit(self.model.get_image_features(**self.processor(images=image, return_tensors="pt")))
            probabilities = (self.model.logit_scale.exp() * features @ self.text.T).softmax(dim=-1)[0].tolist()
        scores = dict(zip(PROMPTS, probabilities))
        return {"dog": scores["dog"], "cat": scores["cat"]}
