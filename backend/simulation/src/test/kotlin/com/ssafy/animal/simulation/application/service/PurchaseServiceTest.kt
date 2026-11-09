package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
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
    private val service = PurchaseService(
        sessionRepository = store,
        shopItemRepository = store,
        transactionRepository = store,
        inventory = SimulationInventory(store, properties),
    )
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private val session = startSession(SpeciesType.DOG, expectedCost = 30_000)
    private val sessionSeq = session.sessionSeq!!

    private fun startSession(species: SpeciesType, expectedCost: Int, userSeq: Int = 17) = store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = species, personality = Personality.FRIENDLY,
            expectedCost = expectedCost, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
        ),
    )

    @Test
    fun `묶음 소모품을 사면 가격 × 수량을 차감하고 지출을 기록하며 수량 × 묶음 수만큼 보유한다`() {
        val result = service.purchase(17, sessionSeq, PurchaseCommand(itemSeq = 22, quantity = 2))

        assertEquals(21_200, result.transaction.amount)
        assertEquals(8_800, result.transaction.balanceAfter)
        assertEquals(8_800, session.balance)
        assertEquals(24, result.inventoryQuantity)

        val recorded = store.transactions.single()
        assertEquals(TransactionType.PURCHASE, recorded.transactionType)
        assertEquals(sessionSeq, recorded.sessionSeq)
        assertEquals(22L, recorded.itemSeq)
        assertEquals(2, recorded.quantity)
    }

    @Test
    fun `잔액이 모자라도 사고 잔액은 음수가 된다`() {
        service.purchase(17, sessionSeq, PurchaseCommand(22, 2))

        val result = service.purchase(17, sessionSeq, PurchaseCommand(22, 1))

        assertEquals(-1_800, result.transaction.balanceAfter)
        assertEquals(36, result.inventoryQuantity)
    }

    @Test
    fun `기구는 1개로 보유하고 다시 사면 409이며 돈과 기록은 그대로다`() {
        assertEquals(1, service.purchase(17, sessionSeq, PurchaseCommand(1, 1)).inventoryQuantity)

        assertError(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED) { service.purchase(17, sessionSeq, PurchaseCommand(1, 1)) }
        assertEquals(20_000, session.balance)
        assertEquals(1, store.transactions.size)
    }

    @Test
    fun `기구를 2개 이상 사면 400 COMMON_INVALID_INPUT(quantity)`() {
        val error = assertFailsWith<BusinessException> { service.purchase(17, sessionSeq, PurchaseCommand(1, 2)) }

        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
        assertEquals("quantity", error.errors.single().field)
        assertTrue(store.inventory.isEmpty())
    }

    @Test
    fun `없는 물품이나 판매 중단 물품은 404 SIMULATION_SHOP_ITEM_NOT_FOUND`() {
        listOf(999L, 90L).forEach { itemSeq ->
            assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND) { service.purchase(17, sessionSeq, PurchaseCommand(itemSeq, 1)) }
        }
    }

    @Test
    fun `세션 종과 맞지 않는 물품은 409 SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES`() {
        // 개 세션에 고양이 화장실 모래
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) { service.purchase(17, sessionSeq, PurchaseCommand(31, 1)) }

        val catSession = startSession(SpeciesType.CAT, expectedCost = 10_000, userSeq = 18)
        assertError(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES) {
            service.purchase(18, catSession.sessionSeq!!, PurchaseCommand(30, 1))
        }
        assertEquals(10, service.purchase(18, catSession.sessionSeq!!, PurchaseCommand(31, 1)).inventoryQuantity)
    }

    @Test
    fun `남의 세션·없는 세션은 404, 진행 중이 아니면 409`() {
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { service.purchase(18, sessionSeq, PurchaseCommand(22, 1)) }
        assertError(SimulationErrorCode.SIMULATION_NOT_FOUND) { service.purchase(17, 9999L, PurchaseCommand(22, 1)) }

        ReflectionTestUtils.setField(session, "status", SessionStatus.COMPLETED)
        ReflectionTestUtils.setField(session, "endedDttm", start.plusDays(7))
        assertError(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS) { service.purchase(17, sessionSeq, PurchaseCommand(22, 1)) }
        assertTrue(store.transactions.isEmpty())
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
