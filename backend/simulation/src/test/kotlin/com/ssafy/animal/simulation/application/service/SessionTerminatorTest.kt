package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationSessionEnded
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.StatEffect
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SessionTerminatorTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val published = mutableListOf<Any>()
    private val terminator = SessionTerminator(
        walkRepository = store,
        occurrenceRepository = store,
        actionLogRepository = store,
        reportRepository = store,
        reportCalculator = store.reportCalculator(SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)),
        eventPublisher = ApplicationEventPublisher { published += it },
    )
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val session = store.save(
        SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 30_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
        ),
    )
    private val sessionSeq = session.sessionSeq!!

    @Test
    fun `산책·이벤트를 닫고 상태·종료 시각을 정한 뒤 리포트와 SESSION_END 로그를 남기고 종료 이벤트를 낸다`() {
        // 끝난 산책 1 + 진행 중 산책 1(종료 시 효과 없이 닫힌다)
        store.save(SimulationWalk.start(session, start).apply { end(start.plusHours(1)) })
        val activeWalk = store.save(SimulationWalk.start(session, start.plusDays(6)))
        store.stockScenarios()
        val scenario = store.scenarios.getValue(1)
        // 해결 2건 + 대기 1건(종료 시 방치 효과 없이 만료돼 발생 수에 들어간다)
        repeat(2) {
            store.save(EventOccurrence.occur(session, scenario, start, start.plusHours(6))).resolve(start.plusHours(1), 50_000L + it)
        }
        val pending = store.save(EventOccurrence.occur(session, scenario, start.plusDays(6), start.plusDays(6).plusHours(6)))
        store.now = start.plusHours(1)
        store.save(SimulationTransaction.purchase(sessionSeq, store.shopItems.getValue(22), 1, 10_600, 19_400))
        store.save(SimulationActionLog.care(session, ActionCode.FEED, 22L, session.applyEffect(StatEffect.fromJson(mapOf("satiety" to 30)))))
        store.save(SimulationActionLog.system(session, ActionCode.SESSION_START))

        val report = terminator.end(session, SessionStatus.COMPLETED, start.plusDays(7))

        assertEquals(SessionStatus.COMPLETED, session.status)
        assertEquals(start.plusDays(7), session.endedDttm)
        assertEquals(start.plusDays(7), activeWalk.endedDttm)
        assertNull(store.findActive(sessionSeq))
        assertEquals(OccurrenceStatus.EXPIRED, pending.status)
        assertNull(store.findPending(sessionSeq))

        assertEquals(report, store.reports.single())
        assertEquals(10_600, report.totalSpent)
        assertEquals(30_000 - 10_600, report.difference)
        assertEquals(10_600, report.spentByCategoryJsonb["FOOD"])
        assertEquals(10_600, report.dailySpentJsonb["1"])
        // CARE·WALK만 센다(SESSION_START·SESSION_END 제외)
        assertEquals(1, report.actionCount)
        assertEquals(3, report.eventCount)
        assertEquals(2, report.eventResolvedCount)
        assertEquals(2, report.walkCount)
        assertEquals(session.stats().toMap(), report.finalStatsJsonb)
        assertNull(report.summary)

        assertEquals(ActionCode.SESSION_END, store.logs.last().actionCode)
        assertEquals(listOf<Any>(SimulationSessionEnded(sessionSeq, 17, SessionStatus.COMPLETED)), published)
    }

    @Test
    fun `이미 끝난 세션은 다시 끝내지 않는다`() {
        terminator.end(session, SessionStatus.ABANDONED, start.plusHours(2))

        assertFailsWith<IllegalStateException> { terminator.end(session, SessionStatus.COMPLETED, start.plusDays(7)) }
        assertEquals(1, store.reports.size)
    }
}
