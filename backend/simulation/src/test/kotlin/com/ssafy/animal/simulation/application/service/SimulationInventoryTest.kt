package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ShopRules
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimulationInventoryTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val inventory = SimulationInventory(
        inventoryRepository = store,
        properties = SimulationProperties(
            Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES, ShopRules(mapOf(22L to 12, 31L to 10)),
        ),
    )
    private val session = 3001L
    private val bowl = store.shopItems.getValue(1)
    private val food = store.shopItems.getValue(22)
    private val litter = store.shopItems.getValue(31)

    @Test
    fun `묶음 소모품은 구매 수량 × 묶음 수만큼 늘고 다시 사면 더해진다`() {
        assertEquals(12, inventory.receive(session, food, 1).quantity)
        assertEquals(36, inventory.receive(session, food, 2).quantity)
        assertEquals(1, store.inventory.size)
    }

    @Test
    fun `기구는 1개로 들어가고 이미 있으면 409 SIMULATION_EQUIPMENT_ALREADY_OWNED`() {
        assertEquals(1, inventory.receive(session, bowl, 1).quantity)

        val error = assertFailsWith<BusinessException> { inventory.receive(session, bowl, 1) }
        assertEquals(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED, error.errorCode)
        assertEquals(1, store.find(session, 1)!!.quantity)
    }

    @Test
    fun `기구를 2개 이상 사거나 수량 0 이하로 받는 것은 호출 오류다`() {
        assertFailsWith<IllegalArgumentException> { inventory.receive(session, bowl, 2) }
        assertFailsWith<IllegalArgumentException> { inventory.receive(session, food, 0) }
    }

    @Test
    fun `소모품을 쓰면 1 줄고 0이 되어도 행은 남는다`() {
        inventory.receive(session, food, 1)

        repeat(12) { inventory.consumeOne(session, food) }

        assertEquals(0, store.find(session, 22)!!.quantity)
        assertEquals(1, store.inventory.size)
    }

    @Test
    fun `다 썼거나 보유하지 않은 소모품을 쓰면 409 SIMULATION_ITEM_NOT_IN_INVENTORY`() {
        inventory.receive(session, litter, 1)
        repeat(10) { inventory.consumeOne(session, litter) }

        listOf(litter, food).forEach { item ->
            val error = assertFailsWith<BusinessException> { inventory.consumeOne(session, item) }
            assertEquals(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY, error.errorCode)
        }
    }

    @Test
    fun `다른 세션의 보유는 섞이지 않는다`() {
        inventory.receive(session, food, 1)

        assertFailsWith<BusinessException> { inventory.consumeOne(3002L, food) }
    }

    @Test
    fun `기구는 소모하지 않는다`() {
        inventory.receive(session, bowl, 1)

        assertFailsWith<IllegalArgumentException> { inventory.consumeOne(session, bowl) }
    }
}
