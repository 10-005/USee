package com.ssafy.animal.simulation.domain

/**
 * 세션 종료(simulation 내부 도메인 이벤트). 종료 트랜잭션 안에서 발행하고,
 * 리포트 요약 생성(263)·SESSION_END 푸시(260)가 커밋 후(@TransactionalEventListener AFTER_COMMIT) 받아 처리한다.
 */
data class SimulationSessionEnded(
    val sessionSeq: Long,
    val userSeq: Int,
    val status: SessionStatus,
)
