package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 스탯 지연 계산(M2)을 세션에 반영한다. 조회(18.2·18.4)·행동(18.6)·스케줄러가 진입할 때 부른다.
 * 부르는 쪽이 세션 행을 잠근 상태여야 한다. 종료 판정(건강 0·예정 종료)은 세션 상태 관리(232)에서 붙인다.
 */
@Component
class SessionStatRefresher(
    private val statCalculator: StatCalculator,
    private val clock: Clock,
) {

    fun now(): OffsetDateTime = OffsetDateTime.now(clock)

    /**
     * 마지막 계산 시각부터 min(until, 예정 종료)까지 지난 tick을 반영한다. 반영할 tick이 없으면 쓰지 않는다.
     * until은 기본 지금이고, 이벤트 만료처럼 그 시각까지만 먼저 계산할 때 넘긴다.
     */
    fun refresh(session: SimulationSession, until: OffsetDateTime = now()) {
        if (session.status != SessionStatus.IN_PROGRESS) return
        val limit = minOf(until, session.endsDttm)
        val result = statCalculator.advance(session.stats(), session.personality, session.startedDttm, session.statsCalculatedDttm, limit)
        if (result.ticksApplied > 0) session.applyStats(result.stats, result.calculatedAt)
    }
}
