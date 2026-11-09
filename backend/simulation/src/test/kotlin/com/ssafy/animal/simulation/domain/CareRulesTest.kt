package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.config.SimulationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CareRulesTest {

    @Test
    fun `238 돌봄 4종의 필요 기구·소모품·효과 출처`() {
        assertEquals(CareRule(ActionCode.FEED, ItemCategory.FOOD_BOWL, ItemCategory.FOOD, EffectSource.CONSUMED_ITEM), CareRules.of(ActionCode.FEED))
        assertEquals(CareRule(ActionCode.WATER, ItemCategory.WATER_BOWL, null, EffectSource.CONFIG), CareRules.of(ActionCode.WATER))
        assertEquals(
            CareRule(ActionCode.CLEAN_LITTER, ItemCategory.LITTER_BOX, ItemCategory.LITTER, EffectSource.CONSUMED_ITEM),
            CareRules.of(ActionCode.CLEAN_LITTER),
        )
        assertEquals(CareRule(ActionCode.BATHE, ItemCategory.GROOMING_KIT, null, EffectSource.REQUIRED_EQUIPMENT), CareRules.of(ActionCode.BATHE))
        assertTrue(CareRules.of(ActionCode.FEED)!!.needsItem)
        assertFalse(CareRules.of(ActionCode.BATHE)!!.needsItem)
    }

    @Test
    fun `간식·약은 기구 없이 소모품 1개를 쓰고 그 효과를 쓴다`() {
        assertEquals(CareRule(ActionCode.SNACK, null, ItemCategory.SNACK, EffectSource.CONSUMED_ITEM), CareRules.of(ActionCode.SNACK))
        assertEquals(CareRule(ActionCode.MEDICINE, null, ItemCategory.MEDICINE, EffectSource.CONSUMED_ITEM), CareRules.of(ActionCode.MEDICINE))
    }

    @Test
    fun `놀아 주기는 장난감을 골라 쓰고 소모하지 않는다`() {
        val play = CareRules.of(ActionCode.PLAY)!!

        assertEquals(CareRule(ActionCode.PLAY, ItemCategory.TOY, null, EffectSource.SELECTED_EQUIPMENT), play)
        assertTrue(play.selectsEquipment)
        assertFalse(play.needsItem)
        assertFailsWith<IllegalArgumentException> { CareRule(ActionCode.PLAY, null, null, EffectSource.SELECTED_EQUIPMENT) }
        assertFailsWith<IllegalArgumentException> { CareRule(ActionCode.PLAY, ItemCategory.TOY, ItemCategory.SNACK, EffectSource.SELECTED_EQUIPMENT) }
    }

    @Test
    fun `아직 만들지 않은 돌봄과 산책·시스템 코드는 규칙이 없다`() {
        listOf(ActionCode.VET_VISIT, ActionCode.GIVE_WATER, ActionCode.SESSION_START)
            .forEach { assertNull(CareRules.of(it), it.name) }
    }

    @Test
    fun `규칙은 분류가 맞지 않으면 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { CareRule(ActionCode.WALK_START, null, null, EffectSource.CONFIG) }
        assertFailsWith<IllegalArgumentException> { CareRule(ActionCode.FEED, ItemCategory.FOOD, null, EffectSource.CONFIG) }
        assertFailsWith<IllegalArgumentException> { CareRule(ActionCode.FEED, null, ItemCategory.FOOD_BOWL, EffectSource.CONSUMED_ITEM) }
        assertFailsWith<IllegalArgumentException> { CareRule(ActionCode.BATHE, null, null, EffectSource.REQUIRED_EQUIPMENT) }
    }

    @Test
    fun `물 주기 효과는 설정에서 읽고 모르는 키는 거부한다`() {
        val care = Binder(ConfigurationPropertySources.from(YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))))
            .bind("simulation", SimulationProperties::class.java)
            .get()
            .care

        assertEquals(StatEffect(mapOf(Stat.HYDRATION to 50)), care.waterEffect)
        assertFailsWith<IllegalArgumentException> { CareConfig(mapOf("thirst" to 50)) }
    }
}
