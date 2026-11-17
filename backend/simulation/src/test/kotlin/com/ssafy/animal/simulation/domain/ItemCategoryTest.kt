package com.ssafy.animal.simulation.domain

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals

/** 코드의 분류 enum과 DB CHECK 제약(마지막 분류 마이그레이션)이 어긋나지 않는지 */
class ItemCategoryTest {

    private val sql = ClassPathResource("db/migration/V20261109_1057__extend_simulation_item_categories.sql")
        .getContentAsString(Charsets.UTF_8)

    /** "CONSTRAINT {name} CHECK" 뒤 첫 괄호 묶음 안의 따옴표 값들 */
    private fun quotedValuesOf(constraint: String): Set<String> {
        val body = sql.substringAfter("ADD CONSTRAINT $constraint CHECK").substringBefore(";")
        val inList = body.substringAfter("IN (").substringBefore(")")
        return Regex("'([A-Z_]+)'").findAll(inList).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `분류 14종이 DB CHECK와 같다`() {
        assertEquals(ItemCategory.entries.map { it.name }.toSet(), quotedValuesOf("ck_sim_item_category"))
        assertEquals(14, ItemCategory.entries.size)
    }

    @Test
    fun `기구 분류가 DB 비소모 CHECK와 같다`() {
        assertEquals(
            ItemCategory.entries.filter { it.isEquipment }.map { it.name }.toSet(),
            quotedValuesOf("ck_sim_item_consumable"),
        )
    }
}
