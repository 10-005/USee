package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stat
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.WalkConfig
import com.ssafy.animal.simulation.domain.WalkStatus
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.random.RandomGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalkServiceTest {

    private val store = FakeSimulationStore().apply {
        stockShop()
        stockScenarios()
    }
    private val properties = SimulationProperties(
        tick = Duration.ofHours(1),
        sessionDuration = Duration.ofDays(7),
        stats = StatCalculatorTest.RULES,
        walk = WalkConfig(
            effectsPerUnit = mapOf("energy" to 7, "calmness" to 4, "bond" to 1, "satiety" to -2, "hydration" to -3, "cleanliness" to -2),
            waterEffects = mapOf("hydration" to 20),
        ),
    )
    private val inventory = SimulationInventory(store, properties)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private var random: RandomGenerator = ScriptedRandom()

    private val dog = session(SpeciesType.DOG)
    private val dogSeq = dog.sessionSeq!!

    private fun session(species: SpeciesType, userSeq: Int = 17) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = species, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(properties.stats, properties.tick).initialStats(),
        ),
    )

    private fun serviceAt(now: OffsetDateTime): WalkService {
        val events = store.events(properties, random)
        return WalkService(
            sessionRepository = store,
            walkRepository = store,
            inventoryRepository = store,
            shopItemRepository = store,
            actionLogRepository = store,
            inventory = inventory,
            statRefresher = store.refresher(properties, now),
            progress = store.progress(properties, now),
            events = events,
            lifecycle = WalkLifecycle(store, store, events, store.terminator(properties), properties),
            properties = properties,
        )
    }

    private fun buy(itemSeq: Long, seq: Long = dogSeq) {
        inventory.receive(seq, store.shopItems.getValue(itemSeq), 1)
    }

    private fun startWalk(at: OffsetDateTime = start): Long {
        buy(9)
        return serviceAt(at).start(17, dogSeq).walk.walkSeq!!
    }

    private fun pendingWalkEvent(walkSeq: Long, scenarioSeq: Long, at: OffsetDateTime = start, expiresAt: OffsetDateTime = start.plusMinutes(5)) =
        store.save(EventOccurrence.occur(dog, store.scenarios.getValue(scenarioSeq), at, expiresAt, walkSeq))

    // ===== 18.14 시작 =====

    @Test
    fun `하네스와 리드줄이 있는 개 세션은 산책을 시작하고 WALK_START를 남긴다`() {
        val walkSeq = startWalk()

        val walk = assertNotNull(store.findByWalkSeq(walkSeq))
        assertEquals(WalkStatus.IN_PROGRESS, walk.status)
        assertEquals(start, walk.startedDttm)
        val log = store.logs.single()
        assertEquals(ActionCode.WALK_START, log.actionCode)
        assertEquals(walkSeq, log.walkSeq)
        assertNull(log.statDeltaJsonb)
        assertEquals(start, dog.lastActionDttm)
    }

    @Test
    fun `고양이 세션·리드줄 없음·이미 산책 중·남의 세션은 시작할 수 없다`() {
        val cat = session(SpeciesType.CAT, userSeq = 30)
        assertError(SimulationErrorCode.SIMULATION_WALK_NOT_FOR_SPECIES) { serviceAt(start).start(30, cat.sessionSeq!!) }
        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING) { serviceAt(start).start(17, dogSeq) }
        startWalk()
        assertError(SimulationErrorCode.SIMULATION_WALK_ALREADY_IN_PROGRESS) { serviceAt(start).start(17, dogSeq) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { serviceAt(start).start(18, dogSeq) }
    }

    // ===== 18.15 하트비트 =====

    @Test
    fun `하트비트는 확률에 걸리면 이 산책의 이벤트를 만들고, 다음 하트비트는 대기 이벤트로 다시 준다`() {
        val walkSeq = startWalk()
        // 개 산책 시나리오: 13번(가중치 1) + 16번(가중치 1). 추첨값 1 → 16번
        random = ScriptedRandom(doubles = listOf(0.0), ints = listOf(1))

        val first = serviceAt(start.plusMinutes(1)).heartbeat(17, dogSeq, walkSeq)
        val second = serviceAt(start.plusMinutes(2)).heartbeat(17, dogSeq, walkSeq)

        val created = assertNotNull(first.newEvent)
        assertEquals(16L, created.scenario.scenarioSeq)
        assertEquals(walkSeq, created.occurrence.walkSeq)
        assertEquals(start.plusMinutes(6), created.occurrence.expiresDttm)
        assertNull(first.pendingEvent)
        assertNull(second.newEvent)
        assertEquals(created.occurrence, second.pendingEvent?.occurrence)
        assertEquals(2, second.elapsedMinutes)
        assertEquals(start.plusMinutes(2), store.findByWalkSeq(walkSeq)?.lastHeartbeatDttm)
    }

    @Test
    fun `산책당 이벤트는 3회까지만 만든다`() {
        val walkSeq = startWalk()
        repeat(3) { i ->
            pendingWalkEvent(walkSeq, 13, at = start.plusMinutes(i.toLong())).resolve(start.plusMinutes(i.toLong()), 60_000L + i)
        }
        random = ScriptedRandom(doubles = listOf(0.0))

        assertNull(serviceAt(start.plusMinutes(5)).heartbeat(17, dogSeq, walkSeq).newEvent)
        assertEquals(3, store.occurrences.size)
    }

    @Test
    fun `하트비트가 10분 넘게 끊긴 산책은 마지막 하트비트 시각으로 끝내고 409`() {
        val walkSeq = startWalk()
        listOf(5L, 10L, 15L, 20L).forEach { serviceAt(start.plusMinutes(it)).heartbeat(17, dogSeq, walkSeq) }

        assertError(SimulationErrorCode.SIMULATION_WALK_NOT_IN_PROGRESS) { serviceAt(start.plusMinutes(31)).heartbeat(17, dogSeq, walkSeq) }

        val walk = assertNotNull(store.findByWalkSeq(walkSeq))
        assertEquals(WalkStatus.ENDED, walk.status)
        assertEquals(start.plusMinutes(20), walk.endedDttm)
        // 20분 산책 효과(2단위)
        assertEquals(mapOf("energy" to 14, "calmness" to 8, "bond" to 2, "satiety" to -4, "hydration" to -6, "cleanliness" to -4), store.logs.last().statDeltaJsonb)
    }

    @Test
    fun `없는 산책·다른 세션의 산책은 404, 끝난 산책은 409`() {
        val walkSeq = startWalk()
        assertError(SimulationErrorCode.SIMULATION_WALK_NOT_FOUND) { serviceAt(start).heartbeat(17, dogSeq, 9999L) }
        serviceAt(start.plusMinutes(1)).end(17, dogSeq, walkSeq)
        assertError(SimulationErrorCode.SIMULATION_WALK_NOT_IN_PROGRESS) { serviceAt(start.plusMinutes(2)).heartbeat(17, dogSeq, walkSeq) }
    }

    // ===== 18.16 산책 중 행동 =====

    @Test
    fun `물 주기는 산책 물 효과를 주고, 이 산책의 대기 이벤트 정답이면 해결한다`() {
        val walkSeq = startWalk()
        // 13번(헥헥): 정답 GIVE_WATER, 해결 수분 +10
        val occurrence = pendingWalkEvent(walkSeq, 13)

        val result = serviceAt(start.plusMinutes(1)).act(17, dogSeq, walkSeq, CareActionCommand(ActionCode.GIVE_WATER, null))

        assertEquals(walkSeq, result.walkSeq)
        assertEquals(mapOf(Stat.HYDRATION to 30), result.action.statDelta)
        assertEquals(mapOf(Stat.HYDRATION to 10), result.action.resolvedEvent?.eventStatDelta)
        assertEquals(OccurrenceStatus.RESOLVED, occurrence.status)
        assertEquals(ActionType.WALK, result.action.log.actionType)
        assertEquals(walkSeq, result.action.log.walkSeq)
        assertEquals(occurrence.occurrenceSeq, result.action.log.occurrenceSeq)
    }

    @Test
    fun `간식 주기는 간식 1개를 쓴다`() {
        val walkSeq = startWalk()
        buy(23)

        val result = serviceAt(start.plusMinutes(1)).act(17, dogSeq, walkSeq, CareActionCommand(ActionCode.GIVE_SNACK, 23))

        assertEquals(23L, result.action.consumedItem?.item?.itemSeq)
        assertNull(result.action.resolvedEvent)
        assertEquals(23L, result.action.log.itemSeq)
    }

    @Test
    fun `배변 치우기는 배변 이벤트가 있을 때만 배변봉투를 쓰고 해결한다`() {
        val walkSeq = startWalk()
        buy(32)
        assertError(SimulationErrorCode.SIMULATION_POOP_NOT_FOUND) {
            serviceAt(start.plusMinutes(1)).act(17, dogSeq, walkSeq, CareActionCommand(ActionCode.CLEAN_UP_POOP, null))
        }
        assertEquals(1, store.find(dogSeq, 32)?.quantity)

        val poop = pendingWalkEvent(walkSeq, 16)
        val result = serviceAt(start.plusMinutes(2)).act(17, dogSeq, walkSeq, CareActionCommand(ActionCode.CLEAN_UP_POOP, null))

        assertEquals(OccurrenceStatus.RESOLVED, poop.status)
        assertEquals(mapOf(Stat.BOND to 2), result.action.statDelta)
        assertEquals(0, result.action.consumedItem?.remainingQuantity)
        assertEquals("깨끗이 치웠어요.", result.action.resolvedEvent?.resultText)
    }

    @Test
    fun `배변봉투가 없으면 409, 산책 행동이 아닌 코드는 400`() {
        val walkSeq = startWalk()
        pendingWalkEvent(walkSeq, 16)

        assertError(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY) {
            serviceAt(start.plusMinutes(1)).act(17, dogSeq, walkSeq, CareActionCommand(ActionCode.CLEAN_UP_POOP, null))
        }
        val error = assertFailsWith<BusinessException> {
            serviceAt(start.plusMinutes(1)).act(17, dogSeq, walkSeq, CareActionCommand(ActionCode.FEED, 22))
        }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
        assertEquals("actionCode", error.errors.single().field)
    }

    // ===== 18.17 종료 =====

    @Test
    fun `종료하면 남은 산책 이벤트는 방치 효과 후 만료하고 산책 길이로 효과를 반영한다`() {
        val walkSeq = startWalk()
        // 16번 방치: 안정감 -5·친밀도 -3, 시한은 종료 뒤
        listOf(10L, 20L, 30L).forEach { serviceAt(start.plusMinutes(it)).heartbeat(17, dogSeq, walkSeq) }
        val poop = pendingWalkEvent(walkSeq, 16, at = start.plusMinutes(33), expiresAt = start.plusMinutes(38))

        val result = serviceAt(start.plusMinutes(35)).end(17, dogSeq, walkSeq)

        assertEquals(OccurrenceStatus.EXPIRED, poop.status)
        assertEquals(35, result.durationMinutes)
        assertEquals(
            mapOf(Stat.ENERGY to 21, Stat.CALMNESS to 12, Stat.BOND to 3, Stat.SATIETY to -6, Stat.HYDRATION to -9, Stat.CLEANLINESS to -6),
            result.statDelta,
        )
        assertEquals(1, result.eventCount)
        assertEquals(WalkStatus.ENDED, result.walk.status)
        assertEquals(ActionCode.WALK_END, store.logs.last().actionCode)
        assertTrue(store.logs.any { it.actionCode == ActionCode.EVENT_EXPIRE && it.occurrenceSeq == poop.occurrenceSeq })
    }

    @Test
    fun `10분 미만 산책은 효과가 없다`() {
        val walkSeq = startWalk()

        val result = serviceAt(start.plusMinutes(9)).end(17, dogSeq, walkSeq)

        assertEquals(emptyMap(), result.statDelta)
        assertEquals(WalkStatus.ENDED, result.walk.status)
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }

    @Test
    fun `하트비트가 끊긴 뒤 누른 종료는 마지막 하트비트 시각으로 끝내고 결과를 준다`() {
        val walkSeq = startWalk()
        listOf(10L, 20L).forEach { serviceAt(start.plusMinutes(it)).heartbeat(17, dogSeq, walkSeq) }

        val result = serviceAt(start.plusMinutes(45)).end(17, dogSeq, walkSeq)

        assertEquals(start.plusMinutes(20), result.walk.endedDttm)
        assertEquals(20, result.durationMinutes)
    }
}
