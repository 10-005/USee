package com.ssafy.animal.simulation.domain

import java.time.Duration

/**
 * 시연 회원 설정(명세 밖, 시연 전용). application.yml의 simulation.demo로 받는다.
 * 시연 회원의 세션은 집 돌발 이벤트(RANDOM·CONDITION)가 생기지 않고,
 * 산책마다 시작 후 [walkPoopAfter]가 지난 첫 하트비트에서 배변 시나리오가 한 번 무조건 생긴다.
 * 산책 시작 응답의 하트비트 간격도 [heartbeatInterval]로 줄여 그 시각에 가깝게 뜨게 한다.
 */
data class DemoConfig(
    /** 시연 회원 user_seq 목록 */
    val userSeqs: Set<Long> = emptySet(),
    /** 산책 시작 후 이 시간이 지나면 배변 이벤트를 만든다 */
    val walkPoopAfter: Duration = Duration.ofSeconds(30),
    /** 산책 배변 시나리오(V20261109_2000의 16번) */
    val walkPoopScenarioSeq: Long = 16,
    /** 시연 회원에게 주는 하트비트 간격 */
    val heartbeatInterval: Duration = Duration.ofSeconds(10),
) {
    init {
        require(!walkPoopAfter.isNegative) { "walkPoopAfter는 0 이상이어야 합니다." }
        require(!heartbeatInterval.isNegative && !heartbeatInterval.isZero) { "heartbeatInterval은 0보다 커야 합니다." }
    }

    fun isDemo(session: SimulationSession): Boolean = session.userSeq.toLong() in userSeqs
}
