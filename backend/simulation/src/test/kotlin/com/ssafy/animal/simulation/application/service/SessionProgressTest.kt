package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.Stats
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionProgressTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun session(endsAt: OffsetDateTime = start.plusDays(7), stats: Stats? = null) = store.save(
        SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = endsAt,
            initialStats = stats ?: StatCalculator(properties.stats, properties.tick).initialStats(),
        ),
    )

    @Test
    fun `종료 조건이 아니면 스탯만 계산하고 끝내지 않는다`() {
        val session = session()

        assertNull(store.progress(properties, start.plusHours(3)).catchUp(session))
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(start.plusHours(3), session.statsCalculatedDttm)
    }

    @Test
    fun `예정 종료 시각이 되면 COMPLETED, 종료 시각은 예정 종료`() {
        val session = session(endsAt = start.plusHours(2))

        val report = assertNotNull(store.progress(properties, start.plusHours(2)).catchUp(session))

        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(start.plusHours(2), session.endedDttm)
        assertEquals(session.sessionSeq, report.sessionSeq)
    }

    @Test
    fun `건강이 0이 되면 예정 종료 전이라도 FAILED, 종료 시각은 건강 0이 된 tick`() {
        val dying = Stats(satiety = 0, hydration = 0, cleanliness = 0, bond = 0, calmness = 0, health = 10, energy = 0)
        val session = session(stats = dying)

        assertNotNull(store.progress(properties, start.plusHours(5)).catchUp(session))

        assertEquals(SessionStatus.FAILED, session.status)
        // tick마다 건강 -5 → 2 tick
        assertEquals(start.plusHours(2), session.endedDttm)
    }

    @Test
    fun `이미 끝난 세션은 건드리지 않는다`() {
        val session = session()
        ReflectionTestUtils.setField(session, "status", SessionStatus.ABANDONED)
        ReflectionTestUtils.setField(session, "endedDttm", start.plusHours(1))

        assertNull(store.progress(properties, start.plusDays(9)).catchUp(session))
        assertTrue(store.reports.isEmpty())
    }

    @Test
    fun `포기는 지금까지 스탯을 반영하고 지금 시각으로 ABANDONED`() {
        val session = session()

        store.progress(properties, start.plusHours(4).plusMinutes(30)).abandon(session)

        assertEquals(SessionStatus.ABANDONED, session.status)
        assertEquals(start.plusHours(4).plusMinutes(30), session.endedDttm)
        assertEquals(start.plusHours(4), session.statsCalculatedDttm)
        assertEquals(1, store.reports.size)
    }
}
