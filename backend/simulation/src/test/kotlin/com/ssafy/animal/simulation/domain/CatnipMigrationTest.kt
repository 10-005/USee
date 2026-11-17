package com.ssafy.animal.simulation.domain

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals

/** 캣닢 혼합 효과 마이그레이션이 33번 CATNIP을 가리키고 스탯 키만 쓰는지 */
class CatnipMigrationTest {

    @Test
    fun `33번 CATNIP의 효과를 활력·친밀도 플러스, 안정감 마이너스로 바꾼다`() {
        val sql = ClassPathResource("db/migration/V20261109_1451__catnip_mixed_effects.sql").getContentAsString(Charsets.UTF_8)
        val update = Regex("""SET effects_jsonb = '(\{[^']*})'[^;]*WHERE item_seq = 33 AND category = 'CATNIP'""").find(sql)!!
        val effects = Regex(""""(\w+)": (-?\d+)""").findAll(update.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2].toInt() }

        assertEquals(StatEffect(mapOf(Stat.ENERGY to 15, Stat.BOND to 3, Stat.CALMNESS to -5)), StatEffect.fromJson(effects))
    }
}
