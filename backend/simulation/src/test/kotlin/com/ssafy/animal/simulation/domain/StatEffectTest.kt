package com.ssafy.animal.simulation.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StatEffectTest {

    private val stats = Stats(satiety = 90, hydration = 50, cleanliness = 3, bond = 30, calmness = 50, health = 90, energy = 60)

    @Test
    fun `JSONB 효과를 스탯별 변화로 읽고 null이면 효과 없음`() {
        assertEquals(StatEffect(mapOf(Stat.SATIETY to 30, Stat.BOND to 1)), StatEffect.fromJson(mapOf("satiety" to 30, "bond" to 1)))
        assertTrue(StatEffect.fromJson(null).isEmpty)
    }

    @Test
    fun `모르는 스탯 키는 데이터 오류다`() {
        assertFailsWith<IllegalArgumentException> { StatEffect.fromJson(mapOf("hunger" to 10)) }
    }

    @Test
    fun `효과를 더하고 0~100으로 자르며 delta는 실제로 바뀐 양만 담는다`() {
        val result = stats.apply(StatEffect.fromJson(mapOf("satiety" to 30, "bond" to 1, "cleanliness" to -5)))

        assertEquals(100, result.after.satiety)
        assertEquals(31, result.after.bond)
        assertEquals(0, result.after.cleanliness)
        // 포만감은 +30이 아니라 실제 +10, 청결도는 -5가 아니라 실제 -3
        assertEquals(mapOf(Stat.SATIETY to 10, Stat.CLEANLINESS to -3, Stat.BOND to 1), result.delta)
        assertEquals(mapOf("satiety" to 10, "cleanliness" to -3, "bond" to 1), result.deltaJson())
    }

    @Test
    fun `변화가 없는 스탯은 delta에서 빠진다`() {
        val full = stats.copy(satiety = 100)

        val result = full.apply(StatEffect.fromJson(mapOf("satiety" to 30)))

        assertEquals(full, result.after)
        assertTrue(result.delta.isEmpty())
    }

    @Test
    fun `이어서 적용하면 delta는 처음부터의 합산 변화이고 각 단계 몫은 따로 볼 수 있다`() {
        val item = StatEffect.fromJson(mapOf("satiety" to 5, "bond" to 3))
        val event = StatEffect.fromJson(mapOf("health" to 30, "calmness" to 10))

        val itemResult = stats.apply(item)
        val eventStep = itemResult.after.apply(event)
        val total = itemResult.andThen(event)

        assertEquals(mapOf(Stat.SATIETY to 5, Stat.BOND to 3, Stat.CALMNESS to 10, Stat.HEALTH to 10), total.delta)
        assertEquals(mapOf(Stat.CALMNESS to 10, Stat.HEALTH to 10), eventStep.delta)
        assertEquals(stats, total.before)
    }

    @Test
    fun `효과끼리 더하면 같은 스탯은 합쳐진다`() {
        val sum = StatEffect(mapOf(Stat.BOND to 3, Stat.CALMNESS to -5)) + StatEffect(mapOf(Stat.BOND to 2, Stat.HEALTH to 20))

        assertEquals(StatEffect(mapOf(Stat.BOND to 5, Stat.CALMNESS to -5, Stat.HEALTH to 20)), sum)
    }

    @Test
    fun `물품 효과는 effects_jsonb를 읽고 기구는 효과가 없다`() {
        val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, effects = mapOf("satiety" to 30))
        val bowl = SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL)

        assertEquals(StatEffect(mapOf(Stat.SATIETY to 30)), food.effect())
        assertTrue(bowl.effect().isEmpty)
    }
}
