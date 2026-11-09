package com.ssafy.animal.simulation.domain

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 돌발 이벤트 시나리오 시드(DB에 넣기 전 SQL 텍스트) 검사. DB CHECK가 막지 못하는
 * 효과 키 오타·조건식 형식·장소별 정답 행동 어긋남을 여기서 잡는다.
 */
class EventScenarioSeedTest {

    private data class SeedRow(
        val scenarioSeq: Long,
        val title: String,
        val description: String,
        val context: TriggerContext,
        val type: TriggerType,
        val condition: String?,
        val cooldownHours: Int?,
        val probability: Double,
        val scope: SpeciesScope,
        val weight: Int,
        val resolveAction: ActionCode,
        val resolveCost: Int,
        val resolveEffects: Map<String, Int>,
        val resultText: String,
        val neglectEffects: Map<String, Int>,
        val neglectText: String,
    )

    private val rows: List<SeedRow> = ClassPathResource("db/migration/V20261109_1800__seed_simulation_event_scenarios.sql")
        .getContentAsString(Charsets.UTF_8)
        .lines()
        .mapNotNull { ROW.matchEntire(it.trim()) }
        .map { m ->
            val g = m.groupValues
            SeedRow(
                scenarioSeq = g[1].toLong(),
                title = g[2],
                description = g[3],
                context = TriggerContext.valueOf(g[4]),
                type = TriggerType.valueOf(g[5]),
                condition = g[6].takeUnless { it == "NULL" }?.removeSurrounding("'"),
                cooldownHours = g[7].takeUnless { it == "NULL" }?.toInt(),
                probability = g[8].toDouble(),
                scope = SpeciesScope.valueOf(g[9]),
                weight = g[10].toInt(),
                resolveAction = ActionCode.valueOf(g[11]),
                resolveCost = g[12].toInt(),
                resolveEffects = effects(g[13]),
                resultText = g[14],
                neglectEffects = effects(g[15]),
                neglectText = g[16],
            )
        }

    @Test
    fun `시나리오 15개가 1번부터 빈틈없이 있고 장소·유형별 개수가 초안과 같다`() {
        assertEquals((1L..15L).toList(), rows.map { it.scenarioSeq })
        assertEquals(7, rows.count { it.context == TriggerContext.HOME && it.type == TriggerType.RANDOM })
        assertEquals(5, rows.count { it.context == TriggerContext.HOME && it.type == TriggerType.CONDITION })
        assertEquals(3, rows.count { it.context == TriggerContext.WALK })
    }

    @Test
    fun `효과 키는 스탯 7종이고 해결·방치 효과와 문구가 모두 있다`() {
        rows.forEach { row ->
            assertTrue(row.resolveEffects.isNotEmpty() && row.neglectEffects.isNotEmpty(), "${row.scenarioSeq}: 효과 없음")
            (row.resolveEffects.keys + row.neglectEffects.keys).forEach { Stat.fromKey(it) }
            assertTrue(row.title.isNotBlank() && row.description.isNotBlank() && row.resultText.isNotBlank() && row.neglectText.isNotBlank())
            assertTrue(row.neglectEffects.values.all { it < 0 }, "${row.scenarioSeq}: 방치 효과는 모두 나쁜 방향")
        }
    }

    @Test
    fun `장소별 정답 행동·비용·조건식이 규칙에 맞는다`() {
        rows.forEach { row ->
            when (row.context) {
                TriggerContext.HOME -> assertTrue(row.resolveAction.type == ActionType.CARE && row.resolveAction != ActionCode.GIVE_CATNIP, "${row.scenarioSeq}")
                TriggerContext.WALK -> {
                    assertTrue(row.resolveAction in setOf(ActionCode.GIVE_WATER, ActionCode.GIVE_SNACK), "${row.scenarioSeq}")
                    assertEquals(SpeciesScope.DOG, row.scope, "산책은 개 전용(Q488)")
                }
            }
            assertEquals(row.resolveAction == ActionCode.VET_VISIT, row.resolveCost > 0, "${row.scenarioSeq}: 비용은 병원만")
            assertTrue(row.weight > 0 && row.probability > 0 && row.probability <= 1)
            if (row.type == TriggerType.CONDITION) {
                val condition = assertNotNull(row.condition, "${row.scenarioSeq}: 조건식 없음")
                val match = assertNotNull(CONDITION.matchEntire(condition), "${row.scenarioSeq}: 조건식 형식 $condition")
                Stat.fromKey(match.groupValues[1])
                assertNotNull(row.cooldownHours)
            } else {
                assertEquals(null, row.condition)
                assertEquals(1.0, row.probability)
            }
        }
    }

    private fun effects(json: String): Map<String, Int> =
        EFFECT.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toInt() }

    companion object {
        private const val TEXT = """'([^']*)'"""
        private const val JSON = """('\{[^']*\}'|NULL)"""
        private const val NUM = """(NULL|\d+)"""
        private val ROW = Regex(
            """\((\d+), $TEXT, $TEXT, '(HOME|WALK)', '(RANDOM|CONDITION)', $JSON, $NUM, ([\d.]+), '(DOG|CAT|ALL)', NULL, (\d+), '(\w+)', (\d+), '(\{[^']*\})', $TEXT, '(\{[^']*\})', $TEXT\)[,;]""",
        )
        private val EFFECT = Regex(""""(\w+)": (-?\d+)""")
        private val CONDITION = Regex("""\{"stat": "(\w+)", "op": "(<|<=|>|>=)", "value": \d+}""")
    }
}
