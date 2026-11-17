package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventQueryServiceTest {

    private val store = FakeSimulationStore().apply {
        stockShop()
        stockScenarios()
    }
    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val session = store.save(
        SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(properties.stats, properties.tick).initialStats(),
        ),
    )
    private val sessionSeq = session.sessionSeq!!

    private fun serviceAt(now: OffsetDateTime) = EventQueryService(store, store, store, store.progress(properties, now))

    private fun occur(scenarioSeq: Long, at: OffsetDateTime, expiresAt: OffsetDateTime) =
        store.save(EventOccurrence.occur(session, store.scenarios.getValue(scenarioSeq), at, expiresAt))

    @Test
    fun `조회 전에 시한이 지난 대기 이벤트를 만료하고 시나리오와 함께 최근 발생 순으로 준다`() {
        val resolved = occur(1, start, start.plusHours(6)).also { it.resolve(start.plusHours(1), 50_001L) }
        val due = occur(5, start.plusHours(2), start.plusHours(8))

        val page = serviceAt(start.plusHours(9)).getEvents(17, sessionSeq, null, PageRequest.of(0, 20))

        assertEquals(listOf(due.occurrenceSeq, resolved.occurrenceSeq), page.content.map { it.occurrence.occurrenceSeq })
        assertEquals(OccurrenceStatus.EXPIRED, page.content.first().occurrence.status)
        assertEquals("쿠션을 물어뜯어요", page.content.first().scenario.title)
    }

    @Test
    fun `상태로 거를 수 있다`() {
        occur(1, start, start.plusHours(6)).also { it.resolve(start.plusHours(1), 50_001L) }
        occur(5, start.plusHours(2), start.plusHours(8))

        val pending = serviceAt(start.plusHours(3)).getEvents(17, sessionSeq, OccurrenceStatus.PENDING, PageRequest.of(0, 20))

        assertEquals(listOf(5L), pending.content.map { it.scenario.scenarioSeq })
    }

    @Test
    fun `남의 세션·없는 세션은 404`() {
        listOf(18L to sessionSeq, 17L to 9999L).forEach { (user, seq) ->
            val error = assertFailsWith<BusinessException> { serviceAt(start).getEvents(user, seq, null, PageRequest.of(0, 20)) }
            assertEquals(SimulationErrorCode.SIMULATION_NOT_FOUND, error.errorCode)
        }
    }
}
