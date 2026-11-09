package com.ssafy.animal.simulation.domain

/** 돌봄 코드 설정값(S15P31D106-238). application.yml의 simulation.care로 받는다 */
data class CareConfig(
    /** 물 주기 효과. 물은 무료라 물품이 없어 효과를 설정에 둔다(키는 스탯 컬럼명) */
    val waterEffects: Map<String, Int> = emptyMap(),
) {
    /** 키 검증은 기동 시 한 번 한다(모르는 키면 IllegalArgumentException) */
    val waterEffect: StatEffect = StatEffect.fromJson(waterEffects)
}
