package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.stereotype.Component

/**
 * 세션을 지금 시각까지 진행시킨다(명세 21.5의 2): 스탯 지연 계산 후 종료 조건이면 그 자리에서 끝낸다.
 * 조회(18.2·18.4·18.12·18.18)·행동(18.6)·구매(18.9)·스케줄러가 세션 행을 잠근 뒤 부른다.
 *
 * - 대기 이벤트의 시한이 그 사이 지났으면 만료 시각까지 먼저 계산하고 방치 효과를 반영한 뒤 이어서 계산한다(Q486)
 * - 건강 0 → FAILED, 종료 시각 = 건강이 0이 된 tick(계산기가 그 시각에서 멈춤) 또는 방치 효과를 받은 만료 시각
 * - 예정 종료 경과 → COMPLETED, 종료 시각 = 예정 종료 시각. 그 뒤에 시한이 오는 이벤트는 종료 처리에서 효과 없이 만료
 * 둘 다면 건강 0이 먼저 일어난 일이라 FAILED.
 */
@Component
class SessionProgress(
    private val statRefresher: SessionStatRefresher,
    private val terminator: SessionTerminator,
    private val events: SessionEvents,
) {

    /** 이번 호출에서 세션이 끝났으면 만든 리포트, 아니면 null. 이미 끝난 세션은 건드리지 않는다 */
    fun catchUp(session: SimulationSession): SimulationReport? {
        if (!session.isInProgress) return null
        expireDueEvent(session)?.let { return it }
        statRefresher.refresh(session)
        return when {
            session.stats().health == 0 -> terminator.end(session, SessionStatus.FAILED, session.statsCalculatedDttm)
            !statRefresher.now().isBefore(session.endsDttm) -> terminator.end(session, SessionStatus.COMPLETED, session.endsDttm)
            else -> null
        }
    }

    /** 포기(18.5)·회원 탈퇴. 지금까지 스탯을 반영한 뒤 ABANDONED로 끝낸다 */
    fun abandon(session: SimulationSession): SimulationReport {
        statRefresher.refresh(session)
        return terminator.end(session, SessionStatus.ABANDONED, statRefresher.now())
    }

    /** 시한이 지난 대기 이벤트를 만료 시각 기준으로 처리한다. 그 과정에서 건강 0이 되면 FAILED로 끝내고 리포트를 돌려준다 */
    private fun expireDueEvent(session: SimulationSession): SimulationReport? {
        val due = events.dueExpiry(session, minOf(statRefresher.now(), session.endsDttm)) ?: return null
        val expiresAt = due.occurrence.expiresDttm
        statRefresher.refresh(session, expiresAt)
        // 시한 전에 이미 건강이 0이 됐으면 방치 효과 없이 그 시각에 끝난다(남은 이벤트는 종료 처리에서 만료)
        if (session.stats().health == 0) return terminator.end(session, SessionStatus.FAILED, session.statsCalculatedDttm)
        events.expireWithNeglect(session, due)
        if (session.stats().health == 0) return terminator.end(session, SessionStatus.FAILED, expiresAt)
        return null
    }
}
