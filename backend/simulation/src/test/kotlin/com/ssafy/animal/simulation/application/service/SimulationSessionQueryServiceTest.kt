package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.service.FakeSimulationStore.Companion.KOREAN_SHORTHAIR
import com.ssafy.animal.simulation.application.service.FakeSimulationStore.Companion.MALTESE
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.Stats
import org.springframework.data.domain.PageRequest
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.OffsetDateTime
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

    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)

    private fun serviceAt(now: OffsetDateTime) = SimulationSessionQueryService(
        sessionRepository = store,
        breedReader = store,
        detailReader = store,
        detailAssembler = store.assembler(),
        progress = store.progress(properties, now),
    )

    private var startedCount = 0L

    /** 세션마다 시작 시각을 1시간씩 늦춰 정렬 순서를 드러낸다 */
    private fun startSession(userSeq: Int = 17, breedSeq: Int = 21, endsAt: OffsetDateTime? = null): SimulationSession {
        val startedAt = start.plusHours(startedCount++)
        return store.save(
            SimulationSession.start(
                userSeq = userSeq, breedSeq = breedSeq, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
                expectedCost = 150_000, startedAt = startedAt, endsAt = endsAt ?: startedAt.plusDays(7), initialStats = calculator.initialStats(),
            ),
        )
    }

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

    @Test
    fun `18_3은 내 세션만 최근 시작 순으로 품종·리포트 유무와 함께 돌려준다`() {
        store.breeds[31] = KOREAN_SHORTHAIR
        val first = startSession().also { end(it) }
        val second = startSession(breedSeq = 31)
        startSession(userSeq = 18)
        store.reported += first.sessionSeq!!

        val page = serviceAt(start).getMySessions(17, null, PageRequest.of(0, 20))

        assertEquals(2, page.totalElements)
        assertEquals(listOf(second, first), page.content.map { it.session })
        assertEquals(listOf(KOREAN_SHORTHAIR, MALTESE), page.content.map { it.breed })
        assertEquals(listOf(false, true), page.content.map { it.hasReport })
    }

    @Test
    fun `18_3은 상태로 거른다`() {
        val ended = startSession().also { end(it) }
        startSession()

        val page = serviceAt(start).getMySessions(17, SessionStatus.COMPLETED, PageRequest.of(0, 20))

        assertEquals(listOf(ended), page.content.map { it.session })
    }

    /** 종료 처리는 232 작업이라 상태만 바꿔 흉내 낸다 */
    private fun end(session: SimulationSession) {
        ReflectionTestUtils.setField(session, "status", SessionStatus.COMPLETED)
        ReflectionTestUtils.setField(session, "endedDttm", session.endsDttm)
    }

    private fun assertNotFound(block: () -> Unit) {
        assertEquals(SimulationErrorCode.SIMULATION_NOT_FOUND, assertFailsWith<BusinessException> { block() }.errorCode)
    }

    @Test
    fun `예정 종료가 지났으면 예정 종료까지 계산하고 COMPLETED로 끝낸 상세를 한 번 돌려준 뒤 다음 조회는 null`() {
        val session = startSession(endsAt = start.plusHours(5))

        val detail = assertNotNull(serviceAt(start.plusHours(10)).getActive(17))

        assertEquals(SessionStatus.COMPLETED, detail.session.status)
        assertEquals(start.plusHours(5), session.endedDttm)
        assertEquals(start.plusHours(5), session.statsCalculatedDttm)
        assertEquals(session.sessionSeq, store.reports.single().sessionSeq)
        assertNull(serviceAt(start.plusHours(11)).getActive(17))
    }

    @Test
    fun `돌봄 없이 건강이 0이 되면 그 시각을 종료 시각으로 FAILED 처리한다`() {
        val session = startSession()

        val detail = assertNotNull(serviceAt(start.plusDays(6)).getActive(17))

        assertEquals(SessionStatus.FAILED, detail.session.status)
        assertEquals(0, session.stats().health)
        assertTrue(session.statsCalculatedDttm.isBefore(start.plusDays(6)))
        assertEquals(session.statsCalculatedDttm, session.endedDttm)
        assertEquals(0, store.reports.single().finalStatsJsonb["health"])
    }

    @Test
    fun `18_4도 조회 시 종료 조건이면 끝낸다`() {
        val session = startSession(endsAt = start.plusHours(5))

        val detail = serviceAt(start.plusHours(6)).getDetail(17, session.sessionSeq!!)

        assertEquals(SessionStatus.COMPLETED, detail.session.status)
        assertEquals(1, store.reports.size)
    }

    @Test
    fun `18_22는 본인 세션의 스탯을 지금 시각까지 계산해 저장하고 세션을 돌려준다`() {
        val session = startSession()

        val result = serviceAt(start.plusHours(3)).getStats(17, session.sessionSeq!!)

        assertEquals(session, result)
        assertEquals(SessionStatus.IN_PROGRESS, result.status)
        assertEquals(start.plusHours(3), session.statsCalculatedDttm)
        assertTrue(session.stats() != calculator.initialStats())
    }

    @Test
    fun `18_22는 남의 세션이나 없는 세션이면 404 SIMULATION_NOT_FOUND`() {
        val session = startSession(userSeq = 18)

        assertNotFound { serviceAt(start).getStats(17, session.sessionSeq!!) }
        assertNotFound { serviceAt(start).getStats(17, 9999L) }
    }

    @Test
    fun `18_22는 조회 시 종료 조건이면 끝내고 끝난 상태로 돌려준다`() {
        val session = startSession(endsAt = start.plusHours(5))

        val result = serviceAt(start.plusHours(6)).getStats(17, session.sessionSeq!!)

        assertEquals(SessionStatus.COMPLETED, result.status)
        assertEquals(start.plusHours(5), result.statsCalculatedDttm)
        assertEquals(1, store.reports.size)
    }

    @Test
    fun `18_22는 이미 끝난 세션이면 다시 계산하지 않고 종료 시점 스탯을 돌려준다`() {
        val session = startSession()
        ReflectionTestUtils.setField(session, "status", SessionStatus.ABANDONED)
        ReflectionTestUtils.setField(session, "endedDttm", start.plusHours(1))

        val result = serviceAt(start.plusDays(3)).getStats(17, session.sessionSeq!!)

        assertEquals(SessionStatus.ABANDONED, result.status)
        assertEquals(calculator.initialStats(), result.stats())
        assertEquals(start, result.statsCalculatedDttm)
    }
}
