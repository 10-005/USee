package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionMaintenanceServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun serviceAt(now: OffsetDateTime) = SessionMaintenanceService(store, store.progress(properties, now))

    private fun session(userSeq: Int, endsAt: OffsetDateTime = start.plusDays(7)) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = endsAt,
            initialStats = StatCalculator(properties.stats, properties.tick).initialStats(),
        ),
    )

    @Test
    fun `스케줄러 진행에서 예정 종료가 지난 세션은 COMPLETED, 아직인 세션은 스탯만 계산`() {
        val due = session(17, endsAt = start.plusHours(2))
        val running = session(18)
        val service = serviceAt(start.plusHours(3))

        assertEquals(listOf(due.sessionSeq, running.sessionSeq), service.inProgressSessionSeqs())
        service.inProgressSessionSeqs().forEach(service::advance)

        assertEquals(SessionStatus.COMPLETED, due.status)
        assertEquals(SessionStatus.IN_PROGRESS, running.status)
        assertEquals(start.plusHours(3), running.statsCalculatedDttm)
        assertEquals(listOf(running.sessionSeq), service.inProgressSessionSeqs())
    }

    @Test
    fun `이미 끝난 세션이나 없는 세션을 advance해도 아무것도 하지 않는다`() {
        val session = session(17)
        serviceAt(start.plusHours(1)).abandonForWithdrawal(17)

        serviceAt(start.plusDays(9)).advance(session.sessionSeq!!)
        serviceAt(start.plusDays(9)).advance(9999L)

        assertEquals(SessionStatus.ABANDONED, session.status)
        assertEquals(1, store.reports.size)
    }

    @Test
    fun `회원 탈퇴면 진행 중 세션을 ABANDONED로 끝내고, 다시 와도 아무것도 하지 않는다`() {
        val session = session(17)

        serviceAt(start.plusHours(1)).abandonForWithdrawal(17)
        serviceAt(start.plusHours(2)).abandonForWithdrawal(17)

        assertEquals(SessionStatus.ABANDONED, session.status)
        assertEquals(start.plusHours(1), session.endedDttm)
        assertEquals(1, store.reports.size)
    }

    @Test
    fun `회원 탈퇴 시점에 이미 건강 0이었으면 FAILED로 끝낸다`() {
        val session = session(17)

        serviceAt(start.plusDays(3)).abandonForWithdrawal(17)

        assertEquals(SessionStatus.FAILED, session.status)
        assertEquals(1, store.reports.size)
    }

    @Test
    fun `진행 중 세션이 없는 회원의 탈퇴는 무시한다`() {
        serviceAt(start).abandonForWithdrawal(99)

        assertTrue(store.reports.isEmpty())
    }
}
