package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseLine
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.ShopRules
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PurchaseServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val properties = SimulationProperties(
        Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES, ShopRules(mapOf(22L to 12, 30L to 10, 31L to 10)),
    )
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun serviceAt(now: OffsetDateTime) = PurchaseService(
        sessionRepository = store,
        shopItemRepository = store,
        transactionRepository = store,
        inventory = SimulationInventory(store, properties),
        progress = store.progress(properties, now),
    )

    private val service = serviceAt(start)

    private val session = startSession(SpeciesType.DOG, expectedCost = 30_000)
    private val sessionSeq = session.sessionSeq!!

    private fun startSession(species: SpeciesType, expectedCost: Int, userSeq: Int = 17) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = species, personality = Personality.FRIENDLY,
            expectedCost = expectedCost, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
        ),
    )

    /** 장바구니: (물품 번호, 수량) 순서대로 */
    private fun cart(vararg lines: Pair<Long, Int>) = PurchaseCommand(lines.map { (itemSeq, quantity) -> PurchaseLine(itemSeq, quantity) })

    @Test
    fun `묶음 소모품을 사면 가격 × 수량을 차감하고 지출을 기록하며 수량 × 묶음 수만큼 보유한다`() {
        val result = service.purchase(17, sessionSeq, cart(22L to 2))

        val line = result.lines.single()
        assertEquals(21_200, line.transaction.amount)
        assertEquals(8_800, line.transaction.balanceAfter)
        assertEquals(24, line.inventoryQuantity)
        assertEquals(21_200, result.totalAmount)
        assertEquals(8_800, result.balanceAfter)
        assertEquals(8_800, session.balance)

        val recorded = store.transactions.single()
        assertEquals(TransactionType.PURCHASE, recorded.transactionType)
        assertEquals(sessionSeq, recorded.sessionSeq)
        assertEquals(22L, recorded.itemSeq)
        assertEquals(2, recorded.quantity)
    }

    @Test
    fun `장바구니의 여러 물품을 한 번에 사고 물품마다 요청 순서대로 지출을 남긴다`() {
        val result = service.purchase(17, sessionSeq, cart(22L to 2, 1L to 1, 23L to 3))

        assertEquals(listOf(22L, 1L, 23L), result.lines.map { it.item.itemSeq })
        assertEquals(listOf(21_200, 10_000, 13_500), result.lines.map { it.transaction.amount })
        // 잔액은 순서대로 줄고 마지막은 음수다(M5)
        assertEquals(listOf(8_800, -1_200, -14_700), result.lines.map { it.transaction.balanceAfter })
        assertEquals(listOf(24, 1, 3), result.lines.map { it.inventoryQuantity })
        assertEquals(44_700, result.totalAmount)
        assertEquals(-14_700, result.balanceAfter)
        assertEquals(-14_700, session.balance)
        assertEquals(listOf(22L, 1L, 23L), store.transactions.map { it.itemSeq })
    }

    @Test
    fun `잔액이 모자라도 사고 잔액은 음수가 된다`() {
        service.purchase(17, sessionSeq, cart(22L to 2))

        val result = service.purchase(17, sessionSeq, cart(22L to 1))

        assertEquals(-1_800, result.balanceAfter)
        assertEquals(36, result.lines.single().inventoryQuantity)
    }

    @Test
    fun `기구는 1개로 보유하고 다시 사면 409이며 돈과 기록은 그대로다`() {
        assertEquals(1, service.purchase(17, sessionSeq, cart(1L to 1)).lines.single().inventoryQuantity)

        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED) { service.purchase(17, sessionSeq, cart(1L to 1)) }
        assertEquals(20_000, session.balance)
        assertEquals(1, store.transactions.size)
    }

    @Test
    fun `뒤쪽 물품이 실패하면 앞쪽 물품도 사지 않는다`() {
        service.purchase(17, sessionSeq, cart(1L to 1))

        // 사료는 문제없지만 이미 가진 밥그릇이 뒤에 있다
        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED) { service.purchase(17, sessionSeq, cart(22L to 2, 1L to 1)) }
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND) { service.purchase(17, sessionSeq, cart(23L to 1, 999L to 1)) }

        assertEquals(20_000, session.balance)
        assertEquals(1, store.transactions.size)
        assertEquals(setOf(1L), store.inventory.map { it.itemSeq }.toSet())
    }

    @Test
    fun `같은 물품을 두 줄로 담으면 400 COMMON_INVALID_INPUT(items 인덱스)이고 아무것도 사지 않는다`() {
        val error = assertFailsWith<BusinessException> { service.purchase(17, sessionSeq, cart(22L to 1, 23L to 1, 22L to 2)) }

        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
        assertEquals("items[2].itemSeq", error.errors.single().field)
        assertTrue(store.transactions.isEmpty())
        assertTrue(store.inventory.isEmpty())
    }

    @Test
    fun `기구를 2개 이상 사면 400 COMMON_INVALID_INPUT(items 인덱스의 quantity)`() {
        val error = assertFailsWith<BusinessException> { service.purchase(17, sessionSeq, cart(22L to 1, 1L to 2)) }

        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
        assertEquals("items[1].quantity", error.errors.single().field)
        assertTrue(store.inventory.isEmpty())
        assertEquals(30_000, session.balance)
    }

    @Test
    fun `없는 물품이나 판매 중단 물품은 404 SIMULATION_SHOP_ITEM_NOT_FOUND`() {
        listOf(999L, 90L).forEach { itemSeq ->
            assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND) { service.purchase(17, sessionSeq, cart(itemSeq to 1)) }
        }
    }

    @Test
    fun `세션 종과 맞지 않는 물품은 409 SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`() {
        // 개 세션에 고양이 화장실 모래
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) { service.purchase(17, sessionSeq, cart(31L to 1)) }

        val catSession = startSession(SpeciesType.CAT, expectedCost = 10_000, userSeq = 18)
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) {
            service.purchase(18, catSession.sessionSeq!!, cart(30L to 1))
        }
        assertEquals(10, service.purchase(18, catSession.sessionSeq!!, cart(31L to 1)).lines.single().inventoryQuantity)
    }

    @Test
    fun `남의 세션·없는 세션은 404, 진행 중이 아니면 409`() {
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { service.purchase(18, sessionSeq, cart(22L to 1)) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { service.purchase(17, 9999L, cart(22L to 1)) }

        ReflectionTestUtils.setField(session, "status", SessionStatus.COMPLETED)
        ReflectionTestUtils.setField(session, "endedDttm", start.plusDays(7))
        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { service.purchase(17, sessionSeq, cart(22L to 1)) }
        assertTrue(store.transactions.isEmpty())
    }

    @Test
    fun `사려는데 그 사이 세션이 끝났으면 종료 처리하고 409, 돈과 지출은 그대로다`() {
        // 돌봄 없이 8일: 건강이 0이 되어 FAILED
        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { serviceAt(start.plusDays(8)).purchase(17, sessionSeq, cart(22L to 1)) }

        assertEquals(SessionStatus.FAILED, session.status)
        assertEquals(30_000, session.balance)
        assertTrue(store.transactions.isEmpty())
        assertEquals(1, store.reports.size)
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
