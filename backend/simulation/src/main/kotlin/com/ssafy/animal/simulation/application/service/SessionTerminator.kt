package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.application.port.out.SessionClosingRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSource
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationSessionEnded
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * 세션 종료 공통 처리(명세 21.5의 6). 조회·행동·포기·스케줄러·회원 탈퇴가 모두 이것을 쓴다.
 * 부르는 쪽이 세션 행을 잠그고 스탯 계산을 끝낸 상태여야 한다(최종 스탯 = 그 시점 스탯).
 *
 * 진행 중 산책 ENDED·PENDING 이벤트 EXPIRED(효과 없음, Q486) → 상태·종료 시각 → 리포트(summary=NULL) →
 * SESSION_END 로그 → 종료 이벤트 발행(커밋 후 요약·푸시는 263·260).
 */
@Component
class SessionTerminator(
    private val closingRepository: SessionClosingRepository,
    private val transactionRepository: TransactionRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val reportRepository: ReportRepository,
    private val properties: SimulationProperties,
    private val eventPublisher: ApplicationEventPublisher,
) {

    fun end(session: SimulationSession, status: SessionStatus, endedAt: OffsetDateTime): SimulationReport {
        val sessionSeq = checkNotNull(session.sessionSeq) { "저장 전 세션입니다." }
        closingRepository.endActiveWalks(sessionSeq, endedAt)
        closingRepository.expirePendingEvents(sessionSeq)
        session.end(status, endedAt)

        val events = closingRepository.countEvents(sessionSeq)
        val report = reportRepository.save(
            ReportAggregator.aggregate(
                ReportSource(
                    session = session,
                    spends = transactionRepository.findSpends(sessionSeq),
                    actionCount = actionLogRepository.countByTypes(sessionSeq, ACTION_TYPES),
                    eventCount = events.total,
                    eventResolvedCount = events.resolved,
                    walkCount = closingRepository.countWalks(sessionSeq),
                ),
                sessionDays = properties.sessionDuration.toDays().toInt(),
            ),
        )
        actionLogRepository.save(SimulationActionLog.system(session, ActionCode.SESSION_END))
        eventPublisher.publishEvent(SimulationSessionEnded(sessionSeq, session.userSeq, status))
        return report
    }

    companion object {
        /** 리포트 actionCount는 사용자가 한 행동만(SYSTEM·EVENT_EXPIRE 제외) */
        private val ACTION_TYPES = listOf(ActionType.CARE, ActionType.WALK)
    }
}
