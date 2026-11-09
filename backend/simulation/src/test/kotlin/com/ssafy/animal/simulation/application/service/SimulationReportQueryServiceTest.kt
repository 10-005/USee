package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.service.FakeSimulationStore.Companion.MALTESE
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationTransaction
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SimulationReportQueryServiceTest {

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

    private fun serviceAt(now: OffsetDateTime) = SimulationReportQueryService(
        sessionRepository = store,
        reportRepository = store,
        breedReader = store,
        progress = store.progress(properties, now),
        reportCalculator = store.reportCalculator(properties),
        userReader = store,
    )

    private fun newSession(userSeq: Int, startedAt: OffsetDateTime) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "보리", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 100_000, startedAt = startedAt, endsAt = startedAt.plusDays(7),
            initialStats = StatCalculator(properties.stats, properties.tick).initialStats(),
        ),
    )

    @Test
    fun `18_19 내 리포트 목록은 끝난 내 세션만 최근 종료 순으로 준다`() {
        val progress = store.progress(properties, start.plusDays(1))
        progress.abandon(session)
        val second = newSession(17, start.plusDays(1)).also { store.progress(properties, start.plusDays(2)).abandon(it) }
        newSession(17, start.plusDays(2)) // 진행 중이라 리포트 없음
        newSession(18, start).also { progress.abandon(it) } // 남의 세션

        val page = serviceAt(start.plusDays(3)).getMyReports(17, PageRequest.of(0, 20))

        assertEquals(listOf(second.sessionSeq, sessionSeq), page.content.map { it.session.sessionSeq })
        assertEquals(MALTESE, page.content.first().breed)
        assertNull(page.content.first().user)
        assertEquals(2, page.totalElements)
    }

    @Test
    fun `18_20 보호소 목록은 회원 필터와 회원 정보를 붙여 준다`() {
        val progress = store.progress(properties, start.plusDays(1))
        progress.abandon(session)
        val other = newSession(18, start).also { progress.abandon(it) }
        store.users[17] = UserInfo(17, "반려가족", "USER", isDeleted = false, profileImageSeq = 1301, profileImageUrl = "https://cdn/1301.jpg")
        store.users[18] = UserInfo(18, "떠난이", "USER", isDeleted = true, profileImageSeq = null, profileImageUrl = null)
        val service = serviceAt(start.plusDays(2))

        val all = service.getReportsForShelter(null, PageRequest.of(0, 20))
        val only18 = service.getReportsForShelter(18, PageRequest.of(0, 20))

        assertEquals(2, all.totalElements)
        assertEquals(setOf(17, 18), all.content.mapNotNull { it.user?.userSeq }.toSet())
        assertEquals(listOf(other.sessionSeq), only18.content.map { it.session.sessionSeq })
        assertTrue(only18.content.single().user!!.isDeleted)
        assertTrue(service.getReportsForShelter(Long.MAX_VALUE, PageRequest.of(0, 20)).isEmpty)
    }

    @Test
    fun `본인 진행 중 세션은 지금까지로 계산한 중간 리포트를 저장하지 않고 돌려준다`() {
        store.now = start.plusHours(2)
        store.save(SimulationTransaction.purchase(sessionSeq, store.shopItems.getValue(22), 2, 21_200, 128_800))

        val view = serviceAt(start.plusHours(3)).getReport(17, Role.USER, sessionSeq)

        assertFalse(view.isFinal)
        assertEquals(SessionStatus.IN_PROGRESS, view.session.status)
        assertNull(view.session.endedDttm)
        assertEquals(21_200, view.report.totalSpent)
        assertEquals(21_200, view.report.spentByCategoryJsonb["FOOD"])
        assertEquals(21_200, view.report.dailySpentJsonb["1"])
        assertEquals(session.stats().toMap(), view.report.finalStatsJsonb)
        assertEquals(start.plusHours(3), session.statsCalculatedDttm)
        assertNull(view.report.summary)
        assertTrue(store.reports.isEmpty())
    }

    @Test
    fun `본인 종료 세션은 저장된 리포트를 돌려준다`() {
        val report = store.progress(properties, start.plusHours(1)).abandon(session)

        val view = serviceAt(start.plusDays(2)).getReport(17, Role.USER, sessionSeq)

        assertTrue(view.isFinal)
        assertSame(report, view.report)
        assertEquals(MALTESE, view.breed)
    }

    @Test
    fun `조회 사이 예정 종료·건강 0이 됐으면 그 자리에서 끝내고 저장 리포트를 돌려준다`() {
        val view = serviceAt(start.plusDays(3)).getReport(17, Role.USER, sessionSeq)

        assertTrue(view.isFinal)
        assertEquals(SessionStatus.FAILED, session.status)
        assertSame(store.reports.single(), view.report)
    }

    @Test
    fun `보호소는 종료 리포트를 누구 것이든 보지만 진행 중 세션은 404`() {
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start.plusHours(1)).getReport(900, Role.SHELTER, sessionSeq) }
        assertEquals(SessionStatus.IN_PROGRESS, session.status)

        store.progress(properties, start.plusHours(1)).abandon(session)
        val view = serviceAt(start.plusDays(1)).getReport(900, Role.SHELTER, sessionSeq)

        assertTrue(view.isFinal)
        assertSame(store.reports.single(), view.report)
    }

    @Test
    fun `남의 세션·없는 세션은 404`() {
        store.progress(properties, start.plusHours(1)).abandon(session)

        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start).getReport(18, Role.USER, sessionSeq) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start).getReport(17, Role.USER, 9999L) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start).getReport(900, Role.SHELTER, 9999L) }
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
