package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.service.FakeSimulationStore.Companion.MALTESE
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.Stats
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationSessionQueryServiceTest {

    private val store = FakeSimulationStore().apply { breeds[21] = MALTESE }
    private val calculator = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1))
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun serviceAt(now: OffsetDateTime) = SimulationSessionQueryService(
        sessionRepository = store,
        detailAssembler = store.assembler(),
        statCalculator = calculator,
        clock = Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul")),
    )

    private fun startSession(userSeq: Int = 17, endsAt: OffsetDateTime = start.plusDays(7)) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = endsAt, initialStats = calculator.initialStats(),
        ),
    )

    @Test
    fun `진행 중 세션이 없으면 null`() {
        assertNull(serviceAt(start).getActive(17))
    }

    @Test
    fun `다른 사용자의 진행 중 세션은 돌려주지 않는다`() {
        startSession(userSeq = 18)

        assertNull(serviceAt(start.plusHours(1)).getActive(17))
    }

    @Test
    fun `조회 시점까지 지난 tick만큼 스탯을 계산해 세션에 저장하고 상세를 돌려준다`() {
        val session = startSession()

        val detail = assertNotNull(serviceAt(start.plusHours(3).plusMinutes(30)).getActive(17))

        // FRIENDLY 3 tick: 안정감 2 × 1.3 = 2.6/tick → floor(7.8) = 7
        val expected = Stats(satiety = 58, hydration = 55, cleanliness = 74, bond = 27, calmness = 43, health = 90, energy = 51)
        assertEquals(expected, session.stats())
        assertEquals(start.plusHours(3), session.statsCalculatedDttm)
        assertEquals(session, detail.session)
        assertEquals(MALTESE, detail.breed)
    }

    @Test
    fun `같은 tick 안에서 다시 조회하면 스탯이 더 바뀌지 않는다`() {
        val session = startSession()
        serviceAt(start.plusHours(3).plusMinutes(5)).getActive(17)
        val afterFirst = session.stats()

        serviceAt(start.plusHours(3).plusMinutes(55)).getActive(17)

        assertEquals(afterFirst, session.stats())
        assertEquals(start.plusHours(3), session.statsCalculatedDttm)
    }

    @Test
    fun `18_4는 본인 세션 상세를 돌려주고 진행 중이면 스탯을 계산해 저장한다`() {
        val session = startSession()

        val detail = serviceAt(start.plusHours(2)).getDetail(17, session.sessionSeq!!)

        assertEquals(session, detail.session)
        assertEquals(start.plusHours(2), session.statsCalculatedDttm)
    }

    @Test
    fun `18_4는 남의 세션이나 없는 세션이면 404 SIMULATION_NOT_FOUND`() {
        val session = startSession(userSeq = 18)

        assertNotFound { serviceAt(start).getDetail(17, session.sessionSeq!!) }
        assertNotFound { serviceAt(start).getDetail(17, 9999L) }
    }

    @Test
    fun `18_4는 종료된 세션의 스탯을 다시 계산하지 않는다`() {
        val session = startSession()
        ReflectionTestUtils.setField(session, "status", SessionStatus.ABANDONED)
        ReflectionTestUtils.setField(session, "endedDttm", start.plusHours(1))

        val detail = serviceAt(start.plusDays(3)).getDetail(17, session.sessionSeq!!)

        assertEquals(SessionStatus.ABANDONED, detail.session.status)
        assertEquals(calculator.initialStats(), session.stats())
        assertEquals(start, session.statsCalculatedDttm)
    }

    private fun assertNotFound(block: () -> Unit) {
        assertEquals(SimulationErrorCode.SIMULATION_NOT_FOUND, assertFailsWith<BusinessException> { block() }.errorCode)
    }

    @Test
    fun `예정 종료가 지나도 스탯은 예정 종료 시각까지만 계산하고 진행 중으로 둔다`() {
        val session = startSession(endsAt = start.plusHours(5))

        serviceAt(start.plusHours(10)).getActive(17)

        assertEquals(start.plusHours(5), session.statsCalculatedDttm)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
    }

    @Test
    fun `돌봄 없이 건강이 0이 되면 그 시각에서 계산을 멈추고 진행 중으로 둔다`() {
        val session = startSession()

        serviceAt(start.plusDays(6)).getActive(17)

        assertEquals(0, session.stats().health)
        assertTrue(session.statsCalculatedDttm.isBefore(start.plusDays(6)))
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
    }
}
