package com.ssafy.animal.simulation.domain

/**
 * 리포트 요약이 저장됨(simulation 내부 도메인 이벤트, Q487). summary가 비어 있던 리포트를 이번에 채운 경우에만 한 번 발행한다.
 * 요약 저장은 자체 트랜잭션으로 끝난 뒤라 SESSION_END 푸시(260)는 @EventListener로 받으면 된다.
 */
data class SimulationReportSummarized(
    val sessionSeq: Long,
    val userSeq: Int,
    val status: SessionStatus,
)
