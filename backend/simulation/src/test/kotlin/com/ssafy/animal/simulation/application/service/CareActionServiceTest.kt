package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.CareConfig
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.ShopRules
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stat
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareActionServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val properties = SimulationProperties(
        tick = Duration.ofHours(1),
        sessionDuration = Duration.ofDays(7),
        stats = StatCalculatorTest.RULES,
        shop = ShopRules(mapOf(22L to 12, 23L to 5, 30L to 10, 31L to 10, 35L to 3)),
        care = CareConfig(mapOf("hydration" to 50)),
    )
    private val calculator = StatCalculator(properties.stats, properties.tick)
    private val inventory = SimulationInventory(store, properties)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun serviceAt(now: OffsetDateTime) = CareActionService(
        sessionRepository = store,
        shopItemRepository = store,
        inventoryRepository = store,
        actionLogRepository = store,
        inventory = inventory,
        statRefresher = SessionStatRefresher(calculator, Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul"))),
        properties = properties,
    )

    private val service = serviceAt(start)
    private val session = startSession(SpeciesType.DOG)
    private val sessionSeq = session.sessionSeq!!

    private fun startSession(species: SpeciesType, userSeq: Int = 17) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = species, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7), initialStats = calculator.initialStats(),
        ),
    )

    private fun buy(itemSeq: Long, seq: Long = sessionSeq) {
        inventory.receive(seq, store.shopItems.getValue(itemSeq), 1)
    }

    private fun act(code: ActionCode, itemSeq: Long? = null, at: OffsetDateTime = start) =
        serviceAt(at).act(17, sessionSeq, CareActionCommand(code, itemSeq))

    @Test
    fun `밥 주기는 밥그릇이 있어야 하고 사료 1개를 써서 효과를 반영하고 CARE 로그를 남긴다`() {
        buy(1)
        buy(22)

        val result = act(ActionCode.FEED, itemSeq = 22)

        assertEquals(mapOf(Stat.SATIETY to 30), result.statDelta)
        assertEquals(100, session.stats().satiety)
        assertEquals(11, result.consumedItem!!.remainingQuantity)
        assertEquals(22L, result.consumedItem!!.item.itemSeq)
        assertEquals(start, session.lastActionDttm)

        val log = store.logs.single()
        assertEquals(ActionType.CARE, log.actionType)
        assertEquals(ActionCode.FEED, log.actionCode)
        assertEquals(22L, log.itemSeq)
        assertEquals(mapOf("satiety" to 30), log.statDeltaJsonb)
        assertEquals(session.stats().toMap(), log.statsAfterJsonb)
    }

    @Test
    fun `행동 전에 지난 시간만큼 스탯을 먼저 계산한다`() {
        buy(1)
        buy(22)

        act(ActionCode.FEED, itemSeq = 22, at = start.plusHours(3))

        // 3 tick 동안 포만감 70 → 58, 그 뒤 +30
        assertEquals(88, session.stats().satiety)
        assertEquals(start.plusHours(3), session.statsCalculatedDttm)
        assertEquals(start.plusHours(3), session.lastActionDttm)
    }

    @Test
    fun `물 주기는 물그릇만 있으면 되고 설정 효과를 쓴다`() {
        buy(2)

        val result = act(ActionCode.WATER)

        // 수분 70 + 50 → 100(실제 +30)
        assertEquals(mapOf(Stat.HYDRATION to 30), result.statDelta)
        assertNull(result.consumedItem)
        assertNull(store.logs.single().itemSeq)
    }

    @Test
    fun `목욕은 위생 관리 키트만 있으면 소모 없이 키트 효과를 쓴다`() {
        buy(10)

        val first = act(ActionCode.BATHE)
        act(ActionCode.BATHE)

        assertEquals(mapOf(Stat.CLEANLINESS to 20, Stat.CALMNESS to -5), first.statDelta)
        assertNull(first.consumedItem)
        assertEquals(45 - 5, session.stats().calmness)
        assertEquals(1, store.find(sessionSeq, 10)!!.quantity)
    }

    @Test
    fun `배변 치우기는 화장실과 배변용품이 있어야 한다`() {
        buy(4)
        buy(30)
        ReflectionTestUtils.setField(session, "cleanliness", 40.toShort())

        val result = act(ActionCode.CLEAN_LITTER, itemSeq = 30)

        assertEquals(mapOf(Stat.CLEANLINESS to 30), result.statDelta)
        assertEquals(9, result.consumedItem!!.remainingQuantity)
    }

    @Test
    fun `간식은 기구 없이 간식 1개를 써서 효과를 반영한다`() {
        buy(23)

        val result = act(ActionCode.SNACK, itemSeq = 23)

        assertEquals(mapOf(Stat.SATIETY to 5, Stat.BOND to 3, Stat.CALMNESS to 3), result.statDelta)
        assertEquals(4, result.consumedItem!!.remainingQuantity)
        assertEquals(ActionCode.SNACK, store.logs.single().actionCode)
    }

    @Test
    fun `약은 기구 없이 약 1개를 써서 건강을 올린다`() {
        buy(35)
        ReflectionTestUtils.setField(session, "health", 50.toShort())

        val result = act(ActionCode.MEDICINE, itemSeq = 35)

        assertEquals(mapOf(Stat.HEALTH to 20), result.statDelta)
        assertEquals(70, session.stats().health)
    }

    @Test
    fun `간식·약의 물품 오류(누락·분류 불일치·종 불일치·미보유)`() {
        buy(1)
        val missing = assertFailsWith<BusinessException> { act(ActionCode.SNACK) }
        assertEquals("itemSeq", missing.errors.single().field)
        val mismatch = assertFailsWith<BusinessException> { act(ActionCode.MEDICINE, itemSeq = 23) }
        assertEquals("itemSeq", mismatch.errors.single().field)
        // 개 세션에 고양이 전용 간식, 사지 않은 약
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) { act(ActionCode.SNACK, itemSeq = 27) }
        assertError(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY) { act(ActionCode.MEDICINE, itemSeq = 35) }
    }

    @Test
    fun `필요 기구가 없으면 409 SIMULATION_EQUIPMENT_MISSING이고 소모품은 그대로다`() {
        buy(22)

        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING) { act(ActionCode.FEED, itemSeq = 22) }
        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING) { act(ActionCode.WATER) }
        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING) { act(ActionCode.BATHE) }
        assertEquals(12, store.find(sessionSeq, 22)!!.quantity)
        assertTrue(store.logs.isEmpty())
    }

    @Test
    fun `소모품 행동의 물품 오류`() {
        buy(1)
        buy(4)

        // itemSeq 누락, 행동과 분류가 다른 물품은 400(itemSeq)
        listOf<Long?>(null, 30L).forEach { itemSeq ->
            val error = assertFailsWith<BusinessException> { act(ActionCode.FEED, itemSeq = itemSeq) }
            assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
            assertEquals("itemSeq", error.errors.single().field)
        }
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND) { act(ActionCode.FEED, itemSeq = 999) }
        // 사지 않은 사료, 고양이 전용 모래
        assertError(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY) { act(ActionCode.FEED, itemSeq = 22) }
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) { act(ActionCode.CLEAN_LITTER, itemSeq = 31) }
    }

    @Test
    fun `아직 만들지 않은 돌봄이나 산책·시스템 코드는 400 COMMON_INVALID_INPUT(actionCode)`() {
        listOf(ActionCode.VET_VISIT, ActionCode.GIVE_WATER, ActionCode.SESSION_START).forEach { code ->
            val error = assertFailsWith<BusinessException> { act(code) }
            assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
            assertEquals("actionCode", error.errors.single().field)
        }
    }

    @Test
    fun `남의 세션·없는 세션은 404, 진행 중이 아니면 409`() {
        buy(2)
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { service.act(18, sessionSeq, CareActionCommand(ActionCode.WATER, null)) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { service.act(17, 9999L, CareActionCommand(ActionCode.WATER, null)) }

        ReflectionTestUtils.setField(session, "status", SessionStatus.ABANDONED)
        ReflectionTestUtils.setField(session, "endedDttm", start)
        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { act(ActionCode.WATER) }
    }

    @Test
    fun `효과가 다 차 있어도 행동은 기록되고 statDelta는 비어 있다`() {
        buy(2)
        ReflectionTestUtils.setField(session, "hydration", Stats.MAX.toShort())

        val result = act(ActionCode.WATER)

        assertTrue(result.statDelta.isEmpty())
        assertEquals(emptyMap(), store.logs.single().statDeltaJsonb)
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
