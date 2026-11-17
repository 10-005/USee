package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.out.ImageUrlResolver
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InventoryQueryServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val service = InventoryQueryService(
        sessionRepository = store,
        inventoryRepository = store,
        imageUrlResolver = object : ImageUrlResolver {
            override fun publicUrl(key: String) = "https://cdn/$key"
        },
    )
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private val session = store.save(
        SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
        ),
    )

    private fun own(itemSeq: Long, quantity: Int) {
        store.save(SimulationInventoryItem(session.sessionSeq!!, itemSeq).apply { if (quantity > 0) add(quantity) })
    }

    @Test
    fun `잔액·예상 비용과 보유 물품을 물품 정렬 순서로 돌려준다(다 쓴 소모품 포함)`() {
        own(31, 0)
        own(22, 12)
        own(1, 1)

        val view = service.getInventory(17, session.sessionSeq!!)

        assertEquals(150_000, view.session.balance)
        assertEquals(listOf(1L, 22L, 31L), view.items.map { it.entry.shopItem.itemSeq })
        assertEquals(listOf(1, 12, 0), view.items.map { it.entry.inventoryItem.quantity })
        assertNull(view.items[0].imageUrl)
        assertEquals("https://cdn/simulation/items/litter.png", view.items[2].imageUrl)
    }

    @Test
    fun `보유 기구는 목록에서 판정한다`() {
        own(1, 1)
        own(22, 12)

        val view = service.getInventory(17, session.sessionSeq!!)

        assertEquals(Equipment(foodBowl = true, waterBowl = false, litterBox = false, toy = false), view.equipment)
    }

    @Test
    fun `보유 물품이 없으면 빈 목록과 기구 없음`() {
        val view = service.getInventory(17, session.sessionSeq!!)

        assertTrue(view.items.isEmpty())
        assertEquals(Equipment.NONE, view.equipment)
    }

    @Test
    fun `남의 세션이나 없는 세션이면 404 SIMULATION_NOT_FOUND`() {
        listOf(18L to session.sessionSeq!!, 17L to 9999L).forEach { (userSeq, sessionSeq) ->
            val error = assertFailsWith<BusinessException> { service.getInventory(userSeq, sessionSeq) }
            assertEquals(SimulationErrorCode.SIMULATION_NOT_FOUND, error.errorCode)
        }
    }
}
