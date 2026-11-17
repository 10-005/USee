package com.ssafy.animal.simulation.domain

import java.time.Duration

/** 돌봄 코드 설정값(S15P31D106-238·246). application.yml의 simulation.care로 받는다 */
data class CareConfig(
    /** 물 주기 효과. 물은 무료라 물품이 없어 효과를 설정에 둔다(키는 스탯 컬럼명) */
    val waterEffects: Map<String, Int> = emptyMap(),
    /** 직전 캣닢 주기 뒤 이 시간 안에 또 주면 남용(과흥분) */
    val catnipOveruseWindow: Duration = Duration.ofHours(6),
    /** 남용 시 캣닢 효과 대신 들어가는 효과. 안정감을 크게 떨어뜨려 조건 이벤트(259)로 이어지게 한다 */
    val catnipOveruseEffects: Map<String, Int> = emptyMap(),
) {
    init {
        require(!catnipOveruseWindow.isNegative && !catnipOveruseWindow.isZero) { "catnipOveruseWindow는 0보다 커야 합니다." }
    }

    // 키 검증은 기동 시 한 번 한다(모르는 키면 IllegalArgumentException)
    val waterEffect: StatEffect = StatEffect.fromJson(waterEffects)
    val catnipOveruseEffect: StatEffect = StatEffect.fromJson(catnipOveruseEffects)
}
