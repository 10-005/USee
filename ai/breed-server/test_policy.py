"""python test_policy.py  (모델 없이 합치기 규칙만 검사)"""
from policy import decide_breed, decide_species

DOG, CAT, NONE = {"dog": 0.9, "cat": 0.0}, {"dog": 0.1, "cat": 0.8}, {"dog": 0.1, "cat": 0.2}

# 종: 다수결, 동물 없는 사진·읽지 못한 사진은 빼고, 동률이면 먼저 올린 사진
assert decide_species([DOG, CAT, DOG], 0.5) == ("dog", ["dog", "cat", "dog"])
assert decide_species([NONE, CAT, None], 0.5) == ("cat", [None, "cat", None])
assert decide_species([CAT, DOG], 0.5)[0] == "cat"
assert decide_species([NONE, None], 0.5) == (None, [None, None])

# 품종: 사진별 확률 평균. 한 장만 확신해도 평균이 기준 미만이면 비운다
breed, candidates = decide_breed([{"a": 0.9, "b": 0.1}, {"a": 0.7, "b": 0.3}], 0.6)
assert breed == "a" and candidates[0][0] == "a" and abs(candidates[0][1] - 0.8) < 1e-9
breed, candidates = decide_breed([{"a": 0.9, "b": 0.1}, {"a": 0.1, "b": 0.9}], 0.6)
assert breed is None and len(candidates) == 2

print("ok")
