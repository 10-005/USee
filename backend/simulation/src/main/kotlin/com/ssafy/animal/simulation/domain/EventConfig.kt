package com.ssafy.animal.simulation.domain

import java.time.Duration
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 돌발 이벤트 코드 설정값(S15P31D106-255, 명세 18.0 [가안]). application.yml의 simulation.event로 받는다.
 * 사용자 결정 2026-11-09: RANDOM은 스케줄러 판정마다 작은 확률(하루 평균 약 1.5회), 세션 일차당 최대 2회, 밤에는 발생하지 않는다.
 */
data class EventConfig(
    /** HOME 이벤트 해결 시한 */
    val homeExpiry: Duration = Duration.ofHours(6),
    /** 스케줄러 판정 1회당 RANDOM 발생 확률. 10분 주기면 하루 144회 × 0.0104 ≈ 1.5회 */
    val randomChancePerCheck: Double = 0.0104,
    /** 세션 일차(시작 기준 24시간)당 RANDOM 최대 횟수 */
    val randomMaxPerDay: Int = 2,
    /** 이 시각부터 */
    val quietStart: LocalTime = LocalTime.of(22, 0),
    /** 이 시각 전까지는 RANDOM을 만들지 않는다(밤 푸시·자는 동안 시한 경과 방지) */
    val quietEnd: LocalTime = LocalTime.of(8, 0),
) {
    init {
        require(!homeExpiry.isNegative && !homeExpiry.isZero) { "homeExpiry는 0보다 커야 합니다." }
        require(randomChancePerCheck in 0.0..1.0) { "randomChancePerCheck는 0~1이어야 합니다: $randomChancePerCheck" }
        require(randomMaxPerDay >= 0) { "randomMaxPerDay는 0 이상이어야 합니다." }
    }

    /** 서울 시각 기준 조용한 시간인지. 시작이 끝보다 늦으면 자정을 넘기는 구간이다 */
    fun isQuiet(at: OffsetDateTime): Boolean {
        val time = at.atZoneSameInstant(SEOUL).toLocalTime()
        return if (quietStart <= quietEnd) time >= quietStart && time < quietEnd else time >= quietStart || time < quietEnd
    }

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
