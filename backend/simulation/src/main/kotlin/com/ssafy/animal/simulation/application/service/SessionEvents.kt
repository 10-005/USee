package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.`in`.ResolvedEvent
import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.application.port.out.WalkRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.EffectResult
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime
import java.util.random.RandomGenerator

/**
 * 세션의 돌발 이벤트 처리(Q486, 명세 18.12). 부르는 쪽이 세션 행을 잠근 상태여야 한다.
 * 종료 판정은 하지 않는다(SessionProgress가 결과 스탯을 보고 판정).
 */
@Component
class SessionEvents(
    private val scenarioRepository: EventScenarioRepository,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val transactionRepository: TransactionRepository,
    private val walkRepository: WalkRepository,
    private val properties: SimulationProperties,
    /** RANDOM 발생 판정·시나리오 추첨. 테스트는 정해진 값을 돌려주는 구현으로 바꾼다 */
    private val random: RandomGenerator,
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

    /**
     * 정답 행동으로 대기 이벤트를 해결한다(Q486). 해결 효과는 부르는 쪽이 세션에 반영하고 결과를 넘긴다.
     * 행동 로그를 먼저 저장한 뒤 불러 그 로그 번호를 남긴다. 해결 비용(병원)이 있으면 EVENT 지출로 잔액에서 뺀다(음수 허용, M5).
     */
    fun resolve(session: SimulationSession, pending: PendingEvent, log: SimulationActionLog, eventResult: EffectResult, at: OffsetDateTime): ResolvedEvent {
        val occurrence = pending.occurrence
        val occurrenceSeq = checkNotNull(occurrence.occurrenceSeq) { "저장 전 이벤트입니다." }
        occurrence.resolve(at, checkNotNull(log.actionLogSeq) { "저장 전 로그입니다." })
        val cost = pending.scenario.resolveCost
        if (cost > 0) {
            val balance = session.spend(cost)
            transactionRepository.save(SimulationTransaction.event(checkNotNull(session.sessionSeq), occurrenceSeq, cost, balance, pending.scenario.title))
        }
        return ResolvedEvent(occurrenceSeq, pending.scenario.title, pending.scenario.resultText, eventResult.delta, cost)
    }

    /**
     * HOME·CONDITION 이벤트를 만든다(스케줄러 판정 1회분, Q481·사용자 결정 2026-11-09 Q499).
     * 대기 이벤트가 없고 조용한 시간이 아닐 때, 조건식이 지금 스탯에 맞고 쿨다운(cooldown_hours, 없으면 설정 24시간)이 지난
     * 시나리오 중 가중치로 하나를 골라 그 시나리오의 발생 확률로 만든다. 하루 횟수 제한은 없다. 만들었으면 그 이벤트와 시나리오, 아니면 null.
     */
    fun triggerCondition(session: SimulationSession, at: OffsetDateTime): PendingEvent? {
        if (!canTrigger(session, at)) return null
        val sessionSeq = checkNotNull(session.sessionSeq)
        val stats = session.stats()
        val candidates = scenarioRepository.findActive(TriggerContext.HOME, TriggerType.CONDITION)
            .filter { it.appliesTo(session.speciesType, session.personality) && it.conditionMatches(stats) }
            .filter { scenario ->
                val cooldown = scenario.cooldownHours?.let { Duration.ofHours(it.toLong()) } ?: properties.event.conditionDefaultCooldown
                val last = occurrenceRepository.findLastOccurredAt(sessionSeq, scenario.scenarioSeq)
                last == null || !at.isBefore(last.plus(cooldown))
            }
        val scenario = pickWeighted(candidates) ?: return null
        if (random.nextDouble() >= scenario.probability) return null
        return PendingEvent(occurrenceRepository.save(EventOccurrence.occur(session, scenario, at, at.plus(properties.event.homeExpiry))), scenario)
    }

    /**
     * 집 이벤트는 진행 중이고, 조용한 시간이 아니고, 예정 종료 전이며, 대기 이벤트가 없고,
     * 산책 중이 아닐 때만 만든다(산책 중에는 산책 이벤트만, 사용자 결정 2026-11-09). 시연 회원은 만들지 않는다
     */
    private fun canTrigger(session: SimulationSession, at: OffsetDateTime): Boolean {
        val sessionSeq = checkNotNull(session.sessionSeq)
        return !properties.demo.isDemo(session) &&
            session.isInProgress && !properties.event.isQuiet(at) && at.isBefore(session.endsDttm) &&
            occurrenceRepository.findPending(sessionSeq) == null && walkRepository.findActive(sessionSeq) == null
    }

    /**
     * 산책 이벤트(WALK·RANDOM, 252). 하트비트마다 대기 이벤트가 없고 이 산책의 이벤트가 최대 횟수 미만이면
     * 설정 확률로 만들고, 종·성격에 맞는 산책 시나리오를 가중치로 고른다. 시한은 산책 이벤트 시한(5분 [가안]).
     * 산책은 밤에도 하므로 조용한 시간을 보지 않는다. 푸시는 없고 하트비트 응답으로 전한다(Q481).
     */
    fun triggerWalk(session: SimulationSession, walk: SimulationWalk, at: OffsetDateTime): PendingEvent? {
        val config = properties.walk
        val walkSeq = checkNotNull(walk.walkSeq) { "저장 전 산책입니다." }
        if (!session.isInProgress || !walk.isInProgress) return null
        if (occurrenceRepository.findPending(checkNotNull(session.sessionSeq)) != null) return null
        if (properties.demo.isDemo(session)) return triggerDemoWalk(session, walk, walkSeq, at)
        if (occurrenceRepository.countByWalk(walkSeq) >= config.eventMaxPerWalk) return null
        if (random.nextDouble() >= config.eventChancePerHeartbeat) return null

        val candidates = scenarioRepository.findActive(TriggerContext.WALK, TriggerType.RANDOM)
            .filter { it.appliesTo(session.speciesType, session.personality) }
        val scenario = pickWeighted(candidates) ?: return null
        return PendingEvent(occurrenceRepository.save(EventOccurrence.occur(session, scenario, at, at.plus(config.eventExpiry), walkSeq)), scenario)
    }

    /**
     * 시연 회원의 산책 이벤트: 확률·추첨 없이 시작 후 설정 시간(30초)이 지난 첫 하트비트에서 배변 시나리오를 산책당 한 번만 만든다.
     * 다른 산책 이벤트는 만들지 않는다.
     */
    private fun triggerDemoWalk(session: SimulationSession, walk: SimulationWalk, walkSeq: Long, at: OffsetDateTime): PendingEvent? {
        val demo = properties.demo
        if (occurrenceRepository.countByWalk(walkSeq) > 0) return null
        if (Duration.between(walk.startedDttm, at) < demo.walkPoopAfter) return null
        val scenario = checkNotNull(scenarioRepository.findByScenarioSeq(demo.walkPoopScenarioSeq)) {
            "시연 배변 시나리오가 없습니다: scenarioSeq=${demo.walkPoopScenarioSeq}"
        }
        return PendingEvent(occurrenceRepository.save(EventOccurrence.occur(session, scenario, at, at.plus(properties.walk.eventExpiry), walkSeq)), scenario)
    }

    /**
     * HOME·RANDOM 이벤트를 확률로 만든다(스케줄러 판정 1회분, M11·사용자 결정 2026-11-09).
     * 대기 이벤트가 없고, 조용한 시간(22~8시 [가안])이 아니고, 세션 일차(시작 기준 24시간)당 횟수가 남았을 때만
     * 판정 확률로 발생시키며, 세션의 종·성격에 맞는 시나리오를 가중치로 고른다. 만들었으면 그 이벤트와 시나리오, 아니면 null.
     */
    fun triggerRandom(session: SimulationSession, at: OffsetDateTime): PendingEvent? {
        val config = properties.event
        if (!canTrigger(session, at)) return null
        val sessionSeq = checkNotNull(session.sessionSeq)
        val dayStart = session.startedDttm.plusDays(Duration.between(session.startedDttm, at).toDays())
        if (occurrenceRepository.countOccurredSince(sessionSeq, TriggerContext.HOME, TriggerType.RANDOM, dayStart) >= config.randomMaxPerDay) return null
        if (random.nextDouble() >= config.randomChancePerCheck) return null

        val candidates = scenarioRepository.findActive(TriggerContext.HOME, TriggerType.RANDOM)
            .filter { it.appliesTo(session.speciesType, session.personality) }
        val scenario = pickWeighted(candidates) ?: return null
        return PendingEvent(occurrenceRepository.save(EventOccurrence.occur(session, scenario, at, at.plus(config.homeExpiry))), scenario)
    }

    private fun pickWeighted(candidates: List<EventScenario>): EventScenario? {
        if (candidates.isEmpty()) return null
        var roll = random.nextInt(candidates.sumOf { it.weight.toInt() })
        for (scenario in candidates) {
            roll -= scenario.weight
            if (roll < 0) return scenario
        }
        return candidates.last()
    }

    private fun scenarioOf(occurrence: EventOccurrence) =
        checkNotNull(scenarioRepository.findByScenarioSeq(occurrence.scenarioSeq)) { "이벤트의 시나리오가 없습니다: scenarioSeq=${occurrence.scenarioSeq}" }
}

data class PendingEvent(
    val occurrence: EventOccurrence,
    val scenario: EventScenario,
) {
    /** 집 이벤트이고 이 돌봄 행동이 정답인지. 산책 이벤트는 산책 중 행동으로만 해결한다(252) */
    fun isAnsweredBy(actionCode: ActionCode): Boolean =
        scenario.triggerContext == TriggerContext.HOME && scenario.resolveActionCode == actionCode

    /** 이 산책의 이벤트이고 이 산책 중 행동이 정답인지(Q486: WALK는 같은 walk_seq일 때만) */
    fun isAnsweredOnWalk(actionCode: ActionCode, walkSeq: Long): Boolean =
        scenario.triggerContext == TriggerContext.WALK && occurrence.walkSeq == walkSeq && scenario.resolveActionCode == actionCode
}
