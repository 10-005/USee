package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.application.port.out.WalkRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSource
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.stereotype.Component

/**
 * 세션의 지금까지 지출·카운트로 리포트를 계산한다(저장하지 않음).
 * 종료 처리는 이 결과를 저장하고(isFinal), 진행 중 세션의 중간 리포트(18.18)는 계산만 해서 돌려준다.
 */
@Component
class ReportCalculator(
    private val walkRepository: WalkRepository,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val transactionRepository: TransactionRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val properties: SimulationProperties,
) {

    fun calculate(session: SimulationSession): SimulationReport {
        val sessionSeq = checkNotNull(session.sessionSeq) { "저장 전 세션입니다." }
        val events = occurrenceRepository.countEvents(sessionSeq)
        return ReportAggregator.aggregate(
            ReportSource(
                session = session,
                spends = transactionRepository.findSpends(sessionSeq),
                actionCount = actionLogRepository.countByTypes(sessionSeq, ACTION_TYPES),
                eventCount = events.total,
                eventResolvedCount = events.resolved,
                walkCount = walkRepository.countWalks(sessionSeq),
            ),
            sessionDays = properties.sessionDuration.toDays().toInt(),
        )
    }

    companion object {
        /** 리포트 actionCount는 사용자가 한 행동만(SYSTEM·EVENT_EXPIRE 제외, Q496) */
        private val ACTION_TYPES = listOf(ActionType.CARE, ActionType.WALK)
    }
}
