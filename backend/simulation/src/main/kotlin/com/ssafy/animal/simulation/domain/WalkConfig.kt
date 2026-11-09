package com.ssafy.animal.simulation.domain

import java.time.Duration

/**
 * 산책 코드 설정값(S15P31D106-252, 명세 18.0 [가안]). application.yml의 simulation.walk로 받는다.
 * 사용자 결정 2026-11-09: 효과는 10분 단위(60분까지만 인정, 10분 미만은 효과 없음), 이벤트는 하트비트마다 8%·산책당 최대 3회.
 */
data class WalkConfig(
    /** 앱이 보내는 하트비트 간격(응답 heartbeatIntervalSeconds) */
    val heartbeatInterval: Duration = Duration.ofSeconds(60),
    /** 하트비트가 이 시간 넘게 끊기면 마지막 하트비트 시각으로 자동 종료 */
    val autoEndAfter: Duration = Duration.ofMinutes(10),
    /** 산책 효과 단위 */
    val effectUnit: Duration = Duration.ofMinutes(10),
    /** 이 길이까지만 효과를 인정 */
    val effectMaxDuration: Duration = Duration.ofMinutes(60),
    /** 효과 단위당 변화(키는 스탯 컬럼명). 종료 시 단위 수만큼 곱해 한 번에 반영 */
    val effectsPerUnit: Map<String, Int> = emptyMap(),
    /** 산책 중 물 주기(GIVE_WATER) 효과. 물은 무료 */
    val waterEffects: Map<String, Int> = emptyMap(),
    /** 하트비트 1회당 산책 이벤트 발생 확률 */
    val eventChancePerHeartbeat: Double = 0.08,
    /** 산책 1회당 최대 이벤트 수 */
    val eventMaxPerWalk: Int = 3,
    /** 산책 이벤트 해결 시한 */
    val eventExpiry: Duration = Duration.ofMinutes(5),
) {
    init {
        listOf(heartbeatInterval, autoEndAfter, effectUnit, effectMaxDuration, eventExpiry).forEach {
            require(!it.isNegative && !it.isZero) { "산책 설정 시간은 0보다 커야 합니다: $it" }
        }
        require(eventChancePerHeartbeat in 0.0..1.0) { "eventChancePerHeartbeat는 0~1이어야 합니다." }
        require(eventMaxPerWalk >= 0) { "eventMaxPerWalk는 0 이상이어야 합니다." }
    }

    // 키 검증은 기동 시 한 번 한다
    val effectPerUnit: StatEffect = StatEffect.fromJson(effectsPerUnit)
    val waterEffect: StatEffect = StatEffect.fromJson(waterEffects)

    /** 산책 길이에 따른 효과(단위 수 = min(길이, 최대) / 단위, 버림) */
    fun effectFor(duration: Duration): StatEffect {
        val units = minOf(duration, effectMaxDuration).toMinutes() / effectUnit.toMinutes()
        if (units <= 0) return StatEffect.NONE
        return StatEffect(effectPerUnit.changes.mapValues { (_, value) -> value * units.toInt() })
    }
}
