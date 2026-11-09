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

    /** 마지막 계산 시각부터 min(지금, 예정 종료)까지 지난 tick을 반영한다. 반영할 tick이 없으면 쓰지 않는다 */
    fun refresh(session: SimulationSession) {
        if (session.status != SessionStatus.IN_PROGRESS) return
        val until = minOf(now(), session.endsDttm)
        val result = statCalculator.advance(session.stats(), session.personality, session.startedDttm, session.statsCalculatedDttm, until)
        if (result.ticksApplied > 0) session.applyStats(result.stats, result.calculatedAt)
    }
}
