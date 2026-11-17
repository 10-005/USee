package com.ssafy.animal.simulation.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.OffsetDateTime

/**
 * 스탯 튜닝값(API 명세 18.0 코드 설정값 [가안], Q429). DB에 두지 않고 application.yml의 simulation.stats로 받는다.
 * 감소량은 양수로 적고 tick마다 그만큼 뺀다(7종 모두 시간이 지나면 떨어짐, Q485).
 */
data class StatRules(
    /** 시작 스탯 7종 */
    val initial: Map<Stat, Int>,
    /** tick당 기본 감소량. 없는 스탯은 0(건강은 기본 감소 없이 아래 벌점으로만 떨어진다) */
    val decreasePerTick: Map<Stat, Int>,
    /** 성격별 감소 배율(M4). 없으면 1 */
    val personalityDecrease: Map<Personality, Map<Stat, BigDecimal>> = emptyMap(),
    val healthPenalty: HealthPenalty,
)

/** tick마다 감소를 적용한 뒤 판정하는 건강 벌점(Q485) */
data class HealthPenalty(
    /** 포만감·수분·청결도 중 하나라도 이 값 미만이면 basicNeedAmount만큼 건강 감소 */
    val basicNeedBelow: Int,
    val basicNeedAmount: Int,
    /** 안정감이 이 값 이하면 calmnessAmount만큼 건강 추가 감소 */
    val calmnessAtOrBelow: Int,
    val calmnessAmount: Int,
)

/** 지연 계산 결과. calculatedAt은 마지막으로 반영한 tick 경계 시각 */
data class StatAdvance(
    val stats: Stats,
    val calculatedAt: OffsetDateTime,
    val ticksApplied: Long,
)

/**
 * 스탯 지연 계산(M2). 조회·행동·스케줄러가 마지막 계산 시각부터 지난 tick만큼만 반영한다.
 *
 * tick은 세션 시작 시각 기준 경계로 센다. 배율 때문에 생기는 소수 감소량(예 4 × 1.3 = 5.2)은
 * k번째 tick에 `floor(k × 감소율) - floor((k - 1) × 감소율)`만큼 빼서, 계산을 몇 번에 나눠 호출하든
 * 같은 시각이면 같은 값이 되게 한다(10분 주기 스케줄러와 조회가 섞여 불러도 결과가 흔들리지 않음).
 */
class StatCalculator(
    private val rules: StatRules,
    private val tick: Duration,
) {
    init {
        require(!tick.isNegative && !tick.isZero) { "tick은 0보다 커야 합니다: $tick" }
    }

    private val rates: Map<Personality, Map<Stat, BigDecimal>> = Personality.entries.associateWith { personality ->
        Stat.entries.associateWith { stat ->
            val base = BigDecimal.valueOf((rules.decreasePerTick[stat] ?: 0).toLong())
            base.multiply(rules.personalityDecrease[personality]?.get(stat) ?: BigDecimal.ONE)
        }
    }

    fun initialStats(): Stats = Stats.of(rules.initial)

    /**
     * calculatedAt 이후 until까지 지난 tick을 반영한다. until은 호출하는 쪽이 min(지금, 예정 종료)로 넘긴다.
     * 건강이 0이 되면 그 tick에서 멈추고 그 시각을 calculatedAt으로 돌려준다(FAILED 판정 시각).
     */
    fun advance(
        stats: Stats,
        personality: Personality,
        startedAt: OffsetDateTime,
        calculatedAt: OffsetDateTime,
        until: OffsetDateTime,
    ): StatAdvance {
        val from = ticksBetween(startedAt, calculatedAt)
        val to = ticksBetween(startedAt, until)
        var current = stats
        var k = from
        while (k < to && current.health > 0) {
            k++
            current = applyTick(current, personality, k)
        }
        if (k == from) return StatAdvance(stats, calculatedAt, 0)
        return StatAdvance(current, startedAt.plus(tick.multipliedBy(k)), k - from)
    }

    private fun applyTick(stats: Stats, personality: Personality, k: Long): Stats {
        var next = stats
        for (stat in Stat.entries) {
            val amount = decreaseAt(rates.getValue(personality).getValue(stat), k)
            if (amount != 0) next = next.plus(stat, -amount)
        }
        val penalty = rules.healthPenalty
        var healthLoss = 0
        if (BASIC_NEEDS.any { next[it] < penalty.basicNeedBelow }) healthLoss += penalty.basicNeedAmount
        if (next.calmness <= penalty.calmnessAtOrBelow) healthLoss += penalty.calmnessAmount
        return if (healthLoss > 0) next.plus(Stat.HEALTH, -healthLoss) else next
    }

    private fun decreaseAt(rate: BigDecimal, k: Long): Int = cumulative(rate, k) - cumulative(rate, k - 1)

    private fun cumulative(rate: BigDecimal, ticks: Long): Int =
        rate.multiply(BigDecimal.valueOf(ticks)).setScale(0, RoundingMode.FLOOR).intValueExact()

    private fun ticksBetween(startedAt: OffsetDateTime, at: OffsetDateTime): Long {
        val elapsed = Duration.between(startedAt, at)
        return if (elapsed.isNegative) 0 else elapsed.dividedBy(tick)
    }

    companion object {
        private val BASIC_NEEDS = listOf(Stat.SATIETY, Stat.HYDRATION, Stat.CLEANLINESS)
    }
}
