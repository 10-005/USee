package com.ssafy.animal.simulation.domain

/** 스탯 7종(M3·Q485). 모두 0~100이고 높을수록 좋으며 시간이 지나면 떨어진다. key는 DB 컬럼명이자 JSONB 키 */
enum class Stat(val key: String) {
    SATIETY("satiety"),
    HYDRATION("hydration"),
    CLEANLINESS("cleanliness"),
    BOND("bond"),
    CALMNESS("calmness"),
    HEALTH("health"),
    ENERGY("energy"),
}

/** 스탯 7종 값. 만들 때와 바꿀 때 모두 0~100으로 자른다 */
data class Stats(
    val satiety: Int,
    val hydration: Int,
    val cleanliness: Int,
    val bond: Int,
    val calmness: Int,
    val health: Int,
    val energy: Int,
) {
    init {
        require(Stat.entries.all { get(it) in MIN..MAX }) { "스탯은 $MIN~$MAX 범위여야 합니다: $this" }
    }

    operator fun get(stat: Stat): Int = when (stat) {
        Stat.SATIETY -> satiety
        Stat.HYDRATION -> hydration
        Stat.CLEANLINESS -> cleanliness
        Stat.BOND -> bond
        Stat.CALMNESS -> calmness
        Stat.HEALTH -> health
        Stat.ENERGY -> energy
    }

    /** stat에 delta를 더한 결과(0~100 클램프) */
    fun plus(stat: Stat, delta: Int): Stats {
        val value = (get(stat) + delta).coerceIn(MIN, MAX)
        return when (stat) {
            Stat.SATIETY -> copy(satiety = value)
            Stat.HYDRATION -> copy(hydration = value)
            Stat.CLEANLINESS -> copy(cleanliness = value)
            Stat.BOND -> copy(bond = value)
            Stat.CALMNESS -> copy(calmness = value)
            Stat.HEALTH -> copy(health = value)
            Stat.ENERGY -> copy(energy = value)
        }
    }

    /** JSONB 저장용(stats_after_jsonb 등). 키 순서는 Stat 선언 순서 */
    fun toMap(): Map<String, Int> = Stat.entries.associate { it.key to get(it) }

    companion object {
        const val MIN = 0
        const val MAX = 100

        fun of(values: Map<Stat, Int>): Stats {
            fun value(stat: Stat) = requireNotNull(values[stat]) { "스탯 값이 없습니다: ${stat.key}" }
            return Stats(
                satiety = value(Stat.SATIETY),
                hydration = value(Stat.HYDRATION),
                cleanliness = value(Stat.CLEANLINESS),
                bond = value(Stat.BOND),
                calmness = value(Stat.CALMNESS),
                health = value(Stat.HEALTH),
                energy = value(Stat.ENERGY),
            )
        }
    }
}
