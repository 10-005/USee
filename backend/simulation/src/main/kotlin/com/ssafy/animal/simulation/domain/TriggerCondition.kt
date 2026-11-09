package com.ssafy.animal.simulation.domain

/**
 * CONDITION 시나리오 조건식(DB 41 trigger_condition_jsonb). 예 {"stat":"health","op":"<","value":40}.
 * 스탯 하나와 비교 연산 하나만 쓴다(명세 18.12). 형식이 틀리면 시드 오류이므로 IllegalArgumentException(500).
 */
data class TriggerCondition(
    val stat: Stat,
    val op: Op,
    val value: Int,
) {
    enum class Op(val symbol: String, private val test: (Int, Int) -> Boolean) {
        LT("<", { a, b -> a < b }),
        LTE("<=", { a, b -> a <= b }),
        GT(">", { a, b -> a > b }),
        GTE(">=", { a, b -> a >= b }),
        ;

        fun test(actual: Int, value: Int) = test.invoke(actual, value)

        companion object {
            fun of(symbol: String): Op = requireNotNull(entries.firstOrNull { it.symbol == symbol }) { "알 수 없는 비교 연산입니다: $symbol" }
        }
    }

    fun matches(stats: Stats): Boolean = op.test(stats[stat], value)

    companion object {
        fun of(json: Map<String, Any>): TriggerCondition {
            val stat = requireNotNull(json["stat"] as? String) { "조건식에 stat이 없습니다: $json" }
            val op = requireNotNull(json["op"] as? String) { "조건식에 op가 없습니다: $json" }
            val value = requireNotNull(json["value"] as? Number) { "조건식에 숫자 value가 없습니다: $json" }
            return TriggerCondition(Stat.fromKey(stat), Op.of(op), value.toInt())
        }
    }
}
