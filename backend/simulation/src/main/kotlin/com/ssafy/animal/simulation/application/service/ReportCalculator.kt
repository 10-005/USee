package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.application.port.out.WalkRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.CareAggregator
import com.ssafy.animal.simulation.domain.CareEventRecord
import com.ssafy.animal.simulation.domain.CareReport
import com.ssafy.animal.simulation.domain.CareSource
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSource
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 세션의 지금까지 지출·카운트로 리포트를 계산한다(저장하지 않음).
 * 종료 처리는 이 결과를 저장하고(isFinal), 진행 중 세션의 중간 리포트(18.18)는 계산만 해서 돌려준다.
 * 일차별 돌봄 집계(care, Q517)는 저장하지 않고 조회·요약할 때마다 행동 이력에서 계산한다.
 */
@Component
class ReportCalculator(
    private val walkRepository: WalkRepository,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val scenarioRepository: EventScenarioRepository,
    private val transactionRepository: TransactionRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val properties: SimulationProperties,
    private val clock: Clock,
) {

    private val sessionDays get() = properties.sessionDuration.toDays().toInt()

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
            sessionDays = sessionDays,
        )
    }

    /** 일차별 돌봄 집계. 평가 일수는 종료 세션이면 종료 시각, 진행 중이면 지금 시각의 일차 */
    fun care(session: SimulationSession, report: SimulationReport): CareReport {
        val sessionSeq = checkNotNull(session.sessionSeq) { "저장 전 세션입니다." }
        val occurrences = occurrenceRepository.findBySession(sessionSeq)
        val scenarios = scenarioRepository.findByScenarioSeqs(occurrences.map { it.scenarioSeq })
        val events = occurrences.map {
            val scenario = checkNotNull(scenarios[it.scenarioSeq]) { "이벤트의 시나리오가 없습니다: scenarioSeq=${it.scenarioSeq}" }
            CareEventRecord(it.scenarioSeq, scenario.title, scenario.resolveActionCode, it.status, it.occurredDttm, it.resolvedDttm)
        }
        val evaluatedAt = session.endedDttm ?: OffsetDateTime.now(clock)
        return CareAggregator.aggregate(
            CareSource(
                startedAt = session.startedDttm,
                speciesType = session.speciesType,
                evaluatedDays = ReportAggregator.dayOf(session.startedDttm, evaluatedAt, sessionDays),
                sessionDays = sessionDays,
                logs = actionLogRepository.findByTypes(sessionSeq, ACTION_TYPES),
                events = events,
                dailySpent = report.dailySpentJsonb,
            ),
            guideline = properties.report.careGuideline,
            penalty = properties.stats.healthPenalty,
        )
    }

    companion object {
        /** 리포트 actionCount는 사용자가 한 행동만(SYSTEM·EVENT_EXPIRE 제외, Q496) */
        private val ACTION_TYPES = listOf(ActionType.CARE, ActionType.WALK)
    }
}
