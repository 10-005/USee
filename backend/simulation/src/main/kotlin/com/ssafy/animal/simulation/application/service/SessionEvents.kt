package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.domain.EffectResult
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * 세션의 돌발 이벤트 처리(Q486, 명세 18.12). 부르는 쪽이 세션 행을 잠근 상태여야 한다.
 * 종료 판정은 하지 않는다(SessionProgress가 결과 스탯을 보고 판정).
 */
@Component
class SessionEvents(
    private val scenarioRepository: EventScenarioRepository,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val actionLogRepository: SimulationActionLogRepository,
) {

    /** 대기 중 이벤트와 그 시나리오. 없으면 null */
    fun pending(session: SimulationSession): PendingEvent? {
        val occurrence = occurrenceRepository.findPending(checkNotNull(session.sessionSeq)) ?: return null
        return PendingEvent(occurrence, scenarioOf(occurrence))
    }

    /** until 시점까지 해결 시한이 지난 대기 이벤트. 없으면 null */
    fun dueExpiry(session: SimulationSession, until: OffsetDateTime): PendingEvent? =
        pending(session)?.takeIf { it.occurrence.isDueAt(until) }

    /**
     * 시한이 지난 이벤트를 만료한다: 방치 효과(성격 배율 없음, Q490)를 스탯에 더하고 EVENT_EXPIRE 로그를 남긴 뒤 EXPIRED.
     * 부르는 쪽이 스탯을 만료 시각까지 먼저 계산해 둔다.
     */
    fun expireWithNeglect(session: SimulationSession, pending: PendingEvent): EffectResult {
        val result = session.applyEffect(pending.scenario.neglectEffect())
        actionLogRepository.save(SimulationActionLog.eventExpire(session, pending.occurrence, result))
        pending.occurrence.expire()
        return result
    }

    private fun scenarioOf(occurrence: EventOccurrence) =
        checkNotNull(scenarioRepository.findByScenarioSeq(occurrence.scenarioSeq)) { "이벤트의 시나리오가 없습니다: scenarioSeq=${occurrence.scenarioSeq}" }
}

data class PendingEvent(
    val occurrence: EventOccurrence,
    val scenario: EventScenario,
)
