package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.CareConfig
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.StatEffect
import com.ssafy.animal.simulation.domain.TransactionType
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareActionServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val properties = SimulationProperties(
        tick = Duration.ofHours(1),
        sessionDuration = Duration.ofDays(7),
        stats = StatCalculatorTest.RULES,
        shop = ShopRules(mapOf(22L to 12, 23L to 5, 30L to 10, 31L to 10, 33L to 4, 35L to 3)),
        care = CareConfig(
            waterEffects = mapOf("hydration" to 50),
            catnipOveruseWindow = Duration.ofHours(6),
            catnipOveruseEffects = mapOf("calmness" to -20),
        ),
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
        progress = store.progress(properties, now),
        events = store.events(),
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
    fun `놀아 주기는 장난감을 고르지 않으면 보유한 첫 번째 장난감 효과를 쓰고 장난감은 줄지 않는다`() {
        buy(12)
        buy(11)

        val result = act(ActionCode.PLAY)

        assertEquals(mapOf(Stat.BOND to 3), result.statDelta)
        assertNull(result.consumedItem)
        assertEquals(11L, store.logs.single().itemSeq)
        assertEquals(1, store.find(sessionSeq, 11)!!.quantity)
    }

    @Test
    fun `놀아 주기는 itemSeq로 보유한 장난감을 고를 수 있다`() {
        buy(11)
        buy(12)

        val result = act(ActionCode.PLAY, itemSeq = 12)

        assertEquals(mapOf(Stat.BOND to 4, Stat.CALMNESS to 3), result.statDelta)
        assertEquals(12L, store.logs.single().itemSeq)
    }

    @Test
    fun `놀아 주기의 장난감 오류`() {
        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING) { act(ActionCode.PLAY) }

        buy(11)
        buy(22)
        // 장난감이 아닌 물품은 400, 없는 물품 404, 사지 않은 장난감 409
        val notToy = assertFailsWith<BusinessException> { act(ActionCode.PLAY, itemSeq = 22) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, notToy.errorCode)
        assertEquals("itemSeq", notToy.errors.single().field)
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND) { act(ActionCode.PLAY, itemSeq = 999) }
        assertError(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY) { act(ActionCode.PLAY, itemSeq = 20) }
        assertTrue(store.logs.isEmpty())
    }

    /** 고양이 세션에서 캣닢 주기. 로그 시각을 행동 시각에 맞춘다 */
    private fun giveCatnip(catSeq: Long, at: OffsetDateTime) = run {
        store.now = at
        serviceAt(at).act(18, catSeq, CareActionCommand(ActionCode.GIVE_CATNIP, 33))
    }

    @Test
    fun `캣닢은 활력·친밀도는 오르고 안정감은 조금 떨어지며 캣닢 1개를 쓴다`() {
        val cat = startSession(SpeciesType.CAT, userSeq = 18).sessionSeq!!
        buy(33, seq = cat)

        val result = giveCatnip(cat, start)

        assertEquals(mapOf(Stat.BOND to 3, Stat.CALMNESS to -5, Stat.ENERGY to 15), result.statDelta)
        assertEquals(3, result.consumedItem!!.remainingQuantity)
        assertEquals(ActionCode.GIVE_CATNIP, store.logs.single().actionCode)
    }

    @Test
    fun `직전 캣닢 뒤 6시간 안에 또 주면 남용으로 안정감만 크게 떨어진다`() {
        val cat = startSession(SpeciesType.CAT, userSeq = 18).sessionSeq!!
        buy(33, seq = cat)
        giveCatnip(cat, start)

        val overused = giveCatnip(cat, start.plusHours(5))

        assertEquals(mapOf(Stat.CALMNESS to -20), overused.statDelta)
        // 남용이어도 캣닢은 쓴다
        assertEquals(2, overused.consumedItem!!.remainingQuantity)
    }

    @Test
    fun `직전 캣닢에서 6시간이 지나면 다시 평소 효과다`() {
        val cat = startSession(SpeciesType.CAT, userSeq = 18).sessionSeq!!
        buy(33, seq = cat)
        giveCatnip(cat, start)

        val result = giveCatnip(cat, start.plusHours(6))

        assertEquals(mapOf(Stat.BOND to 3, Stat.CALMNESS to -5, Stat.ENERGY to 15), result.statDelta)
    }

    @Test
    fun `캣닢은 고양이 전용이라 개 세션은 409`() {
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) { act(ActionCode.GIVE_CATNIP, itemSeq = 33) }
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
    fun `행동하려는데 그 사이 세션이 끝났으면 종료 처리하고 409 SIMULATION_NOT_IN_PROGRESS`() {
        buy(2)

        // 돌봄 없이 8일: 예정 종료 전에 건강이 0이 되어 FAILED
        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { act(ActionCode.WATER, at = start.plusDays(8)) }

        assertEquals(SessionStatus.FAILED, session.status)
        assertTrue(session.endedDttm!!.isBefore(start.plusDays(7)))
        assertEquals(1, store.reports.size)
        // 행동 로그는 없고 SESSION_END만 남는다
        assertEquals(listOf(ActionCode.SESSION_END), store.logs.map { it.actionCode })
    }

    @Test
    fun `효과가 다 차 있어도 행동은 기록되고 statDelta는 비어 있다`() {
        buy(2)
        ReflectionTestUtils.setField(session, "hydration", Stats.MAX.toShort())

        val result = act(ActionCode.WATER)

        assertTrue(result.statDelta.isEmpty())
        assertEquals(emptyMap(), store.logs.single().statDeltaJsonb)
    }

    // ===== 돌발 이벤트 해결(Q486) =====

    private fun pending(scenarioSeq: Long, expiresAt: OffsetDateTime = start.plusHours(6)) = store.save(
        EventOccurrence.occur(session, store.scenarios.getValue(scenarioSeq), start, expiresAt),
    )

    @Test
    fun `정답 행동이면 해결 효과를 더해 합산 변화로 기록하고 이벤트를 RESOLVED로 바꾼다`() {
        store.stockScenarios()
        buy(2)
        // 1번(물그릇): 정답 WATER, 해결 효과 수분 +10·안정감 +5
        val occurrence = pending(1)

        val result = act(ActionCode.WATER)

        // 물 +50으로 수분 70 → 100이라 해결 효과의 수분 +10은 잘리고 안정감 +5만 남는다
        assertEquals(mapOf(Stat.HYDRATION to 30, Stat.CALMNESS to 5), result.statDelta)
        val resolved = assertNotNull(result.resolvedEvent)
        assertEquals(occurrence.occurrenceSeq, resolved.occurrenceSeq)
        assertEquals("물그릇을 엎었어요", resolved.scenarioTitle)
        assertEquals("시원하게 마셨어요.", resolved.resultText)
        assertEquals(mapOf(Stat.CALMNESS to 5), resolved.eventStatDelta)
        assertEquals(0, resolved.eventCost)
        assertEquals(OccurrenceStatus.RESOLVED, occurrence.status)
        assertEquals(start, occurrence.resolvedDttm)
        assertEquals(result.log.actionLogSeq, occurrence.resolvedActionLogSeq)
        assertEquals(occurrence.occurrenceSeq, result.log.occurrenceSeq)
        assertEquals(mapOf("hydration" to 30, "calmness" to 5), result.log.statDeltaJsonb)
    }

    @Test
    fun `해결 효과의 대가(포만감·활력 감소)도 함께 반영한다`() {
        store.stockScenarios()
        buy(11)
        // 5번(쿠션): 정답 PLAY, 친밀도 +5·안정감 +10·포만감 -5·활력 -10. 장난감 11번은 친밀도 +3
        pending(5)

        val result = act(ActionCode.PLAY)

        assertEquals(mapOf(Stat.SATIETY to -5, Stat.BOND to 8, Stat.CALMNESS to 10, Stat.ENERGY to -10), result.statDelta)
        assertEquals(mapOf(Stat.SATIETY to -5, Stat.BOND to 5, Stat.CALMNESS to 10, Stat.ENERGY to -10), result.resolvedEvent?.eventStatDelta)
    }

    @Test
    fun `오답 행동은 평소 행동으로만 처리하고 이벤트는 대기 그대로`() {
        store.stockScenarios()
        buy(1)
        buy(22)
        val occurrence = pending(1)

        val result = act(ActionCode.FEED, itemSeq = 22)

        assertNull(result.resolvedEvent)
        assertNull(result.log.occurrenceSeq)
        assertTrue(occurrence.isPending)
    }

    @Test
    fun `행동 전에 시한이 지났으면 먼저 만료되어 해결되지 않는다`() {
        store.stockScenarios()
        buy(2)
        val occurrence = pending(1, expiresAt = start.plusHours(1))

        val result = act(ActionCode.WATER, at = start.plusHours(2))

        assertNull(result.resolvedEvent)
        assertEquals(OccurrenceStatus.EXPIRED, occurrence.status)
    }

    @Test
    fun `해결 비용이 있는 이벤트는 EVENT 지출로 잔액에서 뺀다`() {
        store.stockScenarios()
        // 8번(축 처져 있어요): 병원 30,000원. 병원 행동은 254에서 붙이므로 해결 처리만 직접 부른다
        val occurrence = pending(8)
        val log = store.save(SimulationActionLog.care(session, ActionCode.FEED, null, session.applyEffect(StatEffect.NONE)))
        val events = store.events()

        val resolved = events.resolve(session, assertNotNull(events.pending(session)), log, session.applyEffect(StatEffect.NONE), start)

        assertEquals(30_000, resolved.eventCost)
        assertEquals(120_000, session.balance)
        val spend = store.transactions.single()
        assertEquals(TransactionType.EVENT, spend.transactionType)
        assertEquals(occurrence.occurrenceSeq, spend.occurrenceSeq)
        assertEquals(30_000, spend.amount)
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
