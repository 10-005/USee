package com.ssafy.animal.simulation.domain

/**
 * 스탯 변화량(S15P31D106-231). 물품 effects_jsonb, 물 주기 설정, 이벤트 해결·방치 효과를 담는다.
 * 키는 스탯 7종, 양수가 좋은 방향(Q485). 성격 배율은 시간 경과 감소에만 쓰고 여기에는 곱하지 않는다.
 */
data class StatEffect(
    val changes: Map<Stat, Int>,
) {
    val isEmpty: Boolean get() = changes.values.all { it == 0 }

    /** 두 효과를 스탯별로 더한다(클램프 전 값) */
    operator fun plus(other: StatEffect) = StatEffect(
        (changes.keys + other.changes.keys).associateWith { (changes[it] ?: 0) + (other.changes[it] ?: 0) },
    )

    companion object {
        val NONE = StatEffect(emptyMap())

        /**
         * JSONB(키는 스탯 컬럼명) → 효과. null이면 효과 없음.
         * 모르는 키는 시드·설정 오류이므로 IllegalArgumentException(500)으로 드러낸다.
         */
        fun fromJson(json: Map<String, Int>?): StatEffect {
            if (json == null) return NONE
            return StatEffect(json.entries.associate { (key, value) -> Stat.fromKey(key) to value })
        }
    }
}

/**
 * 효과를 적용한 결과. 클램프(0~100) 때문에 효과값과 실제 변화가 다를 수 있어 둘을 나눠 둔다.
 * 예: 포만감 90에 +30이면 after는 100, delta는 +10.
 */
data class EffectResult(
    val before: Stats,
    val after: Stats,
) {
    /** 실제로 바뀐 양. 변화 없는 스탯은 뺀다(18.6 statDelta·로그 stat_delta_jsonb) */
    val delta: Map<Stat, Int> = Stat.entries
        .associateWith { after[it] - before[it] }
        .filterValues { it != 0 }

    /** JSONB·응답용(키는 스탯 컬럼명, Stat 선언 순서) */
    fun deltaJson(): Map<String, Int> = delta.entries.associate { (stat, value) -> stat.key to value }

    /**
     * 이어서 다른 효과를 적용한다. before는 처음 값 그대로라 delta는 두 효과의 합산 변화가 된다
     * (18.6: 물품 효과 + 이벤트 해결 효과 합산. 이벤트 몫은 다음 단계 결과의 delta로 따로 본다).
     */
    fun andThen(effect: StatEffect): EffectResult = EffectResult(before, after.apply(effect).after)
}

/** 효과를 더하고 0~100으로 자른다 */
fun Stats.apply(effect: StatEffect): EffectResult {
    var next = this
    for ((stat, value) in effect.changes) {
        if (value != 0) next = next.plus(stat, value)
    }
    return EffectResult(before = this, after = next)
}
