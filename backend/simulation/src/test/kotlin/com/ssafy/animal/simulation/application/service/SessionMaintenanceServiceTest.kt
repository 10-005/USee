package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.random.RandomGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionMaintenanceServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private var random: RandomGenerator = ScriptedRandom()

    private fun serviceAt(now: OffsetDateTime) = SessionMaintenanceService(store, store.progress(properties, now), store.events(properties, random), store.refresher(properties, now))

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

    // ===== RANDOM 돌발 이벤트(256) =====

    private fun catSession() = store.save(
        SimulationSession.start(
            userSeq = 30, breedSeq = 31, animalName = "나비", speciesType = SpeciesType.CAT, personality = Personality.TIMID,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(properties.stats, properties.tick).initialStats(),
        ),
    )

    @Test
    fun `판정 확률에 걸리면 종에 맞는 HOME RANDOM 시나리오를 가중치로 골라 6시간 시한으로 만든다`() {
        store.stockScenarios()
        val dog = session(17)
        // 개: 1번(가중치 2) + 5번(가중치 1). 추첨값 2 → 5번
        random = ScriptedRandom(doubles = listOf(0.0), ints = listOf(2))

        serviceAt(start.plusHours(1)).advance(dog.sessionSeq!!)

        val occurrence = store.occurrences.single()
        assertEquals(5L, occurrence.scenarioSeq)
        assertTrue(occurrence.isPending)
        assertEquals(start.plusHours(1), occurrence.occurredDttm)
        assertEquals(start.plusHours(7), occurrence.expiresDttm)
    }

    @Test
    fun `고양이 세션은 고양이·공통 시나리오만 고른다`() {
        store.stockScenarios()
        val cat = catSession()
        // 고양이: 1번(가중치 2) + 7번(가중치 1). 추첨값 2 → 7번
        random = ScriptedRandom(doubles = listOf(0.0), ints = listOf(2))

        serviceAt(start.plusHours(1)).advance(cat.sessionSeq!!)

        assertEquals(7L, store.occurrences.single().scenarioSeq)
    }

    @Test
    fun `확률에 걸리지 않거나 대기 이벤트가 있으면 만들지 않는다`() {
        store.stockScenarios()
        val dog = session(17)

        random = ScriptedRandom(doubles = listOf(0.5))
        serviceAt(start.plusHours(1)).advance(dog.sessionSeq!!)
        assertTrue(store.occurrences.isEmpty())

        random = ScriptedRandom(doubles = listOf(0.0, 0.0))
        serviceAt(start.plusHours(1)).advance(dog.sessionSeq!!)
        serviceAt(start.plusHours(2)).advance(dog.sessionSeq!!)
        assertEquals(1, store.occurrences.size)
    }

    @Test
    fun `22시부터 8시 전까지는 만들지 않는다`() {
        store.stockScenarios()
        val dog = session(17)
        random = ScriptedRandom(doubles = listOf(0.0, 0.0, 0.0))

        // 시작 14시 → 22시·다음 날 7시 59분은 조용한 시간, 8시는 아님
        serviceAt(start.plusHours(8)).advance(dog.sessionSeq!!)
        serviceAt(start.plusHours(17).plusMinutes(59)).advance(dog.sessionSeq!!)
        assertTrue(store.occurrences.isEmpty())

        serviceAt(start.plusHours(18)).advance(dog.sessionSeq!!)
        assertEquals(1, store.occurrences.size)
    }

    @Test
    fun `세션 일차당 2회를 넘기지 않고 다음 일차에는 다시 만든다`() {
        store.stockScenarios()
        val dog = session(17)
        val scenario = store.scenarios.getValue(1)
        // 1일차(시작~24시간)에 이미 2회 발생·해결
        repeat(2) { i ->
            store.save(EventOccurrence.occur(dog, scenario, start.plusHours(1L + i), start.plusHours(7L + i))).resolve(start.plusHours(2L + i), 50_000L + i)
        }
        random = ScriptedRandom(doubles = listOf(0.0, 0.0))

        serviceAt(start.plusHours(5)).advance(dog.sessionSeq!!)
        assertEquals(2, store.occurrences.size)

        // 2일차 첫 판정(시작 + 24시간 = 다음 날 14시)
        serviceAt(start.plusHours(24)).advance(dog.sessionSeq!!)
        assertEquals(3, store.occurrences.size)
    }
}
