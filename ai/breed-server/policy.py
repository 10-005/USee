"""사진 1~3장의 결과를 하나로 합치는 규칙. 모델 호출 없이 숫자만 다룬다."""


def decide_species(species_scores, min_score):
    """사진별 {"dog": p, "cat": p}(읽지 못한 사진은 None) → (종 또는 None, 사진별 판정 목록).

    사진마다 더 높은 쪽이 min_score 이상이면 그 종, 아니면 동물 없음(None).
    종은 다수결, 동률이면 먼저 올린 사진을 따른다.
    """
    labels = []
    for scores in species_scores:
        best = max(scores, key=scores.get) if scores else None
        labels.append(best if best and scores[best] >= min_score else None)
    voted = [label for label in labels if label]
    if not voted:
        return None, labels
    return max(voted, key=lambda label: (voted.count(label), -voted.index(label))), labels


def decide_breed(breed_scores, min_score, top_k=3):
    """사진별 {라벨: 확률} 목록 → (품종 또는 None, 상위 후보 [(라벨, 평균 확률)]).

    사진별 확률을 평균해 1등을 고르고, 평균이 min_score 미만이면 품종은 비운다(후보는 그대로 준다).
    """
    labels = breed_scores[0].keys()
    average = {label: sum(scores[label] for scores in breed_scores) / len(breed_scores) for label in labels}
    candidates = sorted(average.items(), key=lambda item: item[1], reverse=True)[:top_k]
    return (candidates[0][0] if candidates[0][1] >= min_score else None), candidates
