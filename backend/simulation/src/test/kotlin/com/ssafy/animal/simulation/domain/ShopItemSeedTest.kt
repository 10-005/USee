package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.config.SimulationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 펫샵 시드(DB에 넣기 전 SQL 텍스트)와 코드 규칙·묶음 수 설정의 짝을 검사한다.
 * DB CHECK가 막지 못하는 효과 키·설정 번호 어긋남을 여기서 잡는다.
 */
class ShopItemSeedTest {

    private data class SeedRow(
        val itemSeq: Long,
        val name: String,
        val category: ItemCategory,
        val consumable: Boolean,
        val scope: SpeciesScope,
        val effects: Map<String, Int>?,
        val displayOrder: Int,
    )

    private val rows: List<SeedRow> = ClassPathResource("db/migration/V20261109_1110__seed_simulation_shop_items.sql")
        .getContentAsString(Charsets.UTF_8)
        .lines()
        .mapNotNull { ROW.matchEntire(it.trim()) }
        .map { m ->
            val (seq, name, category, consumable, _, scope, effects, order) = m.destructured
            SeedRow(
                itemSeq = seq.toLong(),
                name = name,
                category = ItemCategory.valueOf(category),
                consumable = consumable == "TRUE",
                scope = SpeciesScope.valueOf(scope),
                effects = effects.takeIf { it.isNotEmpty() }?.let { json ->
                    EFFECT.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toInt() }
                },
                displayOrder = order.toInt(),
            )
        }

    private val shop = Binder(ConfigurationPropertySources.from(YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))))
        .bind("simulation", SimulationProperties::class.java)
        .get()
        .shop

    @Test
    fun `물품 35개가 1번부터 빈틈없이 있고 정렬 순서는 번호와 같다`() {
        assertEquals((1L..35L).toList(), rows.map { it.itemSeq })
        assertTrue(rows.all { it.displayOrder.toLong() == it.itemSeq })
    }

    @Test
    fun `소모 여부는 분류 규칙과 같다`() {
        rows.forEach { assertEquals(!it.category.isEquipment, it.consumable, it.name) }
    }

    @Test
    fun `효과 키는 스탯 7종 이름만 쓴다`() {
        val statKeys = Stat.entries.map { it.key }.toSet()
        rows.forEach { row -> row.effects?.keys?.forEach { assertTrue(it in statKeys, "${row.name}: $it") } }
    }

    @Test
    fun `장난감과 배변봉투 외 소모품은 효과가 있고 나머지 기구·배변봉투는 효과가 없다`() {
        rows.forEach { row ->
            when {
                row.category == ItemCategory.TOY -> assertNotNull(row.effects, row.name)
                row.category == ItemCategory.POOP_BAG -> assertNull(row.effects, row.name)
                row.category.isEquipment -> assertNull(row.effects, row.name)
                else -> assertNotNull(row.effects, row.name)
            }
        }
    }

    @Test
    fun `묶음 수 설정은 시드의 소모품 번호만 가리킨다`() {
        val consumableSeqs = rows.filter { it.consumable }.map { it.itemSeq }.toSet()

        assertTrue(shop.usesPerPurchase.isNotEmpty())
        shop.usesPerPurchase.keys.forEach { assertTrue(it in consumableSeqs, "설정의 $it 번은 시드의 소모품이 아닙니다") }
        // 이번 목록의 소모품은 모두 묶음으로 판다
        assertEquals(consumableSeqs, shop.usesPerPurchase.keys)
        assertEquals(12, shop.usesPerPurchase(22))
    }

    @Test
    fun `산책 기본 조건인 리드줄은 개 전용 기구로 있다`() {
        val leash = rows.single { it.category == ItemCategory.LEASH }
        assertEquals(SpeciesScope.DOG, leash.scope)
    }

    companion object {
        /** (seq, 'name', 'CATEGORY', TRUE|FALSE, price, 'description', NULL, 'SCOPE', NULL|'{json}', order) */
        private val ROW = Regex(
            """^\((\d+), '([^']*)', '([A-Z_]+)', (TRUE|FALSE), (\d+), '[^']*', NULL, '(ALL|DOG|CAT)', (?:NULL|'(\{[^']*\})'), (\d+)\)[,;]$""",
        )
        private val EFFECT = Regex(""""(\w+)": (-?\d+)""")
    }
}
