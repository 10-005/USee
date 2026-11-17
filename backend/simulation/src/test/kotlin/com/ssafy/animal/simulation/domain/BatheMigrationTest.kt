package com.ssafy.animal.simulation.domain

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 목욕 전환 마이그레이션이 시드 번호·분류를 정확히 가리키고, 키트 효과가 스탯 키만 쓰는지 */
class BatheMigrationTest {

    private val sql = ClassPathResource("db/migration/V20261109_1316__bathe_with_grooming_kit.sql").getContentAsString(Charsets.UTF_8)

    @Test
    fun `샴푸는 34번 SHAMPOO만 판매 중단한다`() {
        assertTrue(Regex("""SET is_active = FALSE[^;]*WHERE item_seq = 34 AND category = 'SHAMPOO'""").containsMatchIn(sql))
    }

    @Test
    fun `키트 효과는 10번 GROOMING_KIT에 넣고 스탯 키만 쓴다`() {
        val update = Regex("""SET effects_jsonb = '(\{[^']*})'[^;]*WHERE item_seq = 10 AND category = 'GROOMING_KIT'""").find(sql)!!
        val effects = Regex(""""(\w+)": (-?\d+)""").findAll(update.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2].toInt() }

        assertEquals(StatEffect(mapOf(Stat.CLEANLINESS to 40, Stat.CALMNESS to -5)), StatEffect.fromJson(effects))
    }
}
