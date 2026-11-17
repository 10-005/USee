package com.ssafy.animal.simulation.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulationShopItemTest {

    @Test
    fun `소모 여부는 분류에서 정해진다(기구는 비소모)`() {
        assertFalse(SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL).isConsumable)
        assertFalse(SimulationShopItem(9, "하네스와 리드줄", ItemCategory.LEASH, 14_900, SpeciesScope.DOG).isConsumable)
        assertTrue(SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL).isConsumable)
        assertTrue(SimulationShopItem(32, "배변봉투", ItemCategory.POOP_BAG, 500, SpeciesScope.DOG).isConsumable)
    }

    @Test
    fun `적용 종 ALL은 개·고양이 모두, DOG·CAT은 그 종만 포함한다`() {
        assertTrue(SpeciesScope.ALL.includes(SpeciesType.DOG))
        assertTrue(SpeciesScope.ALL.includes(SpeciesType.CAT))
        assertTrue(SpeciesScope.DOG.includes(SpeciesType.DOG))
        assertFalse(SpeciesScope.DOG.includes(SpeciesType.CAT))
        assertFalse(SpeciesScope.CAT.includes(SpeciesType.DOG))
    }

    @Test
    fun `묶음 수는 설정에 없으면 1이고 1 미만은 허용하지 않는다`() {
        val rules = ShopRules(mapOf(22L to 12))

        assertEquals(12, rules.usesPerPurchase(22))
        assertEquals(1, rules.usesPerPurchase(1))
        assertFailsWith<IllegalArgumentException> { ShopRules(mapOf(22L to 0)) }
    }
}
