package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.ImageUrlResolver
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.ShopRules
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShopQueryServiceTest {

    private val items = listOf(
        SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL, displayOrder = 1),
        SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, imageKey = "simulation/items/food.png", displayOrder = 22),
        SimulationShopItem(31, "화장실 모래", ItemCategory.LITTER, 7_200, SpeciesScope.CAT, displayOrder = 31),
    )
    private val requested = mutableListOf<SpeciesType?>()

    private val repository = object : ShopItemRepository {
        override fun findActive(speciesType: SpeciesType?): List<SimulationShopItem> {
            requested += speciesType
            return items.filter { speciesType == null || it.speciesScope.includes(speciesType) }
        }

        override fun findByItemSeq(itemSeq: Long) = items.firstOrNull { it.itemSeq == itemSeq }
    }

    private val service = ShopQueryService(
        shopItemRepository = repository,
        imageUrlResolver = object : ImageUrlResolver {
            override fun publicUrl(key: String) = "https://cdn/$key"
        },
        properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES, ShopRules(mapOf(22L to 12, 31L to 10))),
    )

    @Test
    fun `물품마다 사진 URL과 묶음 수를 붙인다`() {
        val views = service.getItems(null)

        assertEquals(listOf(1L, 22L, 31L), views.map { it.item.itemSeq })
        assertNull(views[0].imageUrl)
        assertEquals("https://cdn/simulation/items/food.png", views[1].imageUrl)
        assertEquals(listOf(1, 12, 10), views.map { it.usesPerPurchase })
    }

    @Test
    fun `종을 넘기면 저장소에 그대로 전달한다`() {
        val views = service.getItems(SpeciesType.DOG)

        assertEquals(listOf<SpeciesType?>(SpeciesType.DOG), requested)
        assertEquals(listOf(1L, 22L), views.map { it.item.itemSeq })
    }
}
