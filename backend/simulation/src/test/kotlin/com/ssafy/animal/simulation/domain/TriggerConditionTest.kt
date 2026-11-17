package com.ssafy.animal.simulation.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TriggerConditionTest {

    private val stats = Stats(satiety = 15, hydration = 70, cleanliness = 80, bond = 30, calmness = 30, health = 40, energy = 60)

    @Test
    fun `JSONB 조건식을 읽어 스탯과 비교한다`() {
        val condition = TriggerCondition.of(mapOf("stat" to "health", "op" to "<", "value" to 40))

        assertEquals(TriggerCondition(Stat.HEALTH, TriggerCondition.Op.LT, 40), condition)
        assertFalse(condition.matches(stats))
        assertTrue(condition.matches(stats.copy(health = 39)))
    }

    @Test
    fun `비교 연산 4종의 경계가 맞다`() {
        fun check(op: String, expected: Boolean) =
            assertEquals(expected, TriggerCondition.of(mapOf("stat" to "calmness", "op" to op, "value" to 30)).matches(stats), op)

        check("<", false)
        check("<=", true)
        check(">", false)
        check(">=", true)
    }

    @Test
    fun `JSON 숫자가 Long·Double로 와도 정수로 읽는다`() {
        assertTrue(TriggerCondition.of(mapOf("stat" to "satiety", "op" to "<", "value" to 20L)).matches(stats))
        assertTrue(TriggerCondition.of(mapOf("stat" to "satiety", "op" to "<", "value" to 20.0)).matches(stats))
    }

    @Test
    fun `형식이 틀린 조건식은 시드 오류로 실패한다`() {
        assertFailsWith<IllegalArgumentException> { TriggerCondition.of(mapOf("stat" to "stress", "op" to "<", "value" to 40)) }
        assertFailsWith<IllegalArgumentException> { TriggerCondition.of(mapOf("stat" to "health", "op" to "!=", "value" to 40)) }
        assertFailsWith<IllegalArgumentException> { TriggerCondition.of(mapOf("stat" to "health", "op" to "<")) }
    }
}
