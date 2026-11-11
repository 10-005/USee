"""python test_policy.py  (모델 없이 합치기 규칙만 검사)"""
from policy import decide_breed, decide_species

DOG, CAT, NONE = {"dog": 0.9, "cat": 0.0}, {"dog": 0.1, "cat": 0.8}, {"dog": 0.1, "cat": 0.2}

# 종: 다수결, 동물 없는 사진·읽지 못한 사진은 빼고, 동률이면 먼저 올린 사진
assert decide_species([DOG, CAT, DOG], 0.5) == ("dog", ["dog", "cat", "dog"])
assert decide_species([NONE, CAT, None], 0.5) == ("cat", [None, "cat", None])
assert decide_species([CAT, DOG], 0.5)[0] == "cat"
assert decide_species([NONE, None], 0.5) == (None, [None, None])

# 품종: 사진별 확률 평균. 0.8 이상 HIGH, 0.5 이상 LOW, 미만이면 비운다(경계값은 위 단계에 포함)
def top(*rows):
    return decide_breed([{"a": a, "b": 1 - a} for a in rows], 0.8, 0.5)

breed, confidence, candidates = top(0.9, 0.7)
assert (breed, confidence) == ("a", "HIGH") and candidates[0][0] == "a" and abs(candidates[0][1] - 0.8) < 1e-9
assert top(0.8)[:2] == ("a", "HIGH")
assert top(0.79)[:2] == ("a", "LOW")
assert top(0.5)[:2] == ("a", "LOW")
assert top(0.49)[:2] == ("b", "LOW")  # 1등이 b(0.51)
breed, confidence, candidates = decide_breed([{"a": 0.45, "b": 0.3, "c": 0.25}], 0.8, 0.5)
assert (breed, confidence) == (None, None) and len(candidates) == 3
# 한 장만 확신해도 평균이 기준 미만이면 비운다
breed, confidence, candidates = decide_breed([{"a": 0.9, "b": 0.1, "c": 0}, {"a": 0, "b": 0.1, "c": 0.9}], 0.8, 0.5)
assert (breed, confidence) == (None, None)

print("ok")
