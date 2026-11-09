package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.service.FakeSimulationStore.Companion.MALTESE
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationSessionEnded
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AbandonSimulationServiceTest {

    private val store = FakeSimulationStore().apply {
        stockShop()
        breeds[21] = MALTESE
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

    private fun serviceAt(now: OffsetDateTime) = AbandonSimulationService(store, store, store.progress(properties, now))

    @Test
    fun `포기하면 지금 시각으로 ABANDONED 처리하고 리포트(summary null)를 돌려준다`() {
        val view = serviceAt(start.plusHours(5)).abandon(17, sessionSeq)

        assertEquals(SessionStatus.ABANDONED, session.status)
        assertEquals(start.plusHours(5), session.endedDttm)
        assertEquals(start.plusHours(5), session.statsCalculatedDttm)
        assertEquals(store.reports.single(), view.report)
        assertNull(view.report.summary)
        assertEquals(MALTESE, view.breed)
        assertEquals(ActionCode.SESSION_END, store.logs.last().actionCode)
        assertEquals(listOf<Any>(SimulationSessionEnded(sessionSeq, 17, SessionStatus.ABANDONED)), store.published)
    }

    @Test
    fun `남의 세션·없는 세션은 404`() {
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start).abandon(18, sessionSeq) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start).abandon(17, 9999L) }
    }

    @Test
    fun `이미 포기한 세션은 409이고 리포트를 더 만들지 않는다`() {
        serviceAt(start.plusHours(1)).abandon(17, sessionSeq)

        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { serviceAt(start.plusHours(2)).abandon(17, sessionSeq) }
        assertEquals(1, store.reports.size)
    }

    @Test
    fun `그 사이 건강 0이 됐으면 FAILED로 끝내고 409`() {
        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { serviceAt(start.plusDays(3)).abandon(17, sessionSeq) }

        assertEquals(SessionStatus.FAILED, session.status)
        assertEquals(1, store.reports.size)
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
