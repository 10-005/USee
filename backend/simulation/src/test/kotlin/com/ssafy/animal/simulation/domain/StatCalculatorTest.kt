package com.ssafy.animal.simulation.domain

import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class StatCalculatorTest {

    private val calculator = StatCalculator(RULES, Duration.ofHours(1))
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val initial = calculator.initialStats()

    private fun hours(h: Long) = start.plusHours(h)

    @Test
    fun `시작 스탯은 설정 초기값이다`() {
        assertEquals(Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60), initial)
    }

    @Test
    fun `1 tick이 안 지났으면 스탯과 계산 시각을 그대로 둔다`() {
        val result = calculator.advance(initial, Personality.TIMID, start, start, start.plusMinutes(59))

        assertEquals(initial, result.stats)
        assertEquals(start, result.calculatedAt)
        assertEquals(0, result.ticksApplied)
    }

    @Test
    fun `1 tick이 지나면 기본 감소량과 성격 배율을 적용하고 계산 시각을 tick 경계로 옮긴다`() {
        // FRIENDLY: 안정감 감소 2 × 1.3 = 2.6 → 첫 tick은 floor(2.6) = 2
        val result = calculator.advance(initial, Personality.FRIENDLY, start, start, start.plusMinutes(90))

        assertEquals(Stats(satiety = 66, hydration = 65, cleanliness = 78, bond = 29, calmness = 48, health = 90, energy = 57), result.stats)
        assertEquals(hours(1), result.calculatedAt)
        assertEquals(1, result.ticksApplied)
    }

    @Test
    fun `소수 감소량은 누적으로 반영돼 한 번에 계산하든 나눠 계산하든 결과가 같다`() {
        val once = calculator.advance(initial, Personality.ACTIVE, start, start, hours(10))

        var stats = initial
        var calculatedAt = start
        for (h in 1L..10L) {
            val step = calculator.advance(stats, Personality.ACTIVE, start, calculatedAt, hours(h).plusMinutes(10))
            stats = step.stats
            calculatedAt = step.calculatedAt
        }

        // ACTIVE 포만감 4 × 1.3 = 5.2/tick → 10 tick 52, 활력 3 × 1.3 = 3.9/tick → 39
        assertEquals(70 - 52, once.stats.satiety)
        assertEquals(60 - 39, once.stats.energy)
        assertEquals(once.stats, stats)
        assertEquals(once.calculatedAt, calculatedAt)
    }

    @Test
    fun `포만감·수분·청결도 중 하나라도 20 미만이면 건강이 떨어지고 안정감 20 이하면 더 떨어진다`() {
        val low = Stats(satiety = 23, hydration = 90, cleanliness = 90, bond = 50, calmness = 22, health = 50, energy = 50)

        // TIMID 기준 1 tick: 포만감 23 → 19(<20) 건강 -3, 안정감 22 → 19(≤20) 건강 -2
        val result = calculator.advance(low, Personality.TIMID, start, start, hours(1))

        assertEquals(19, result.stats.satiety)
        assertEquals(19, result.stats.calmness)
        assertEquals(45, result.stats.health)
    }

    @Test
    fun `건강이 0이 되면 그 tick에서 멈추고 그 시각을 돌려준다`() {
        val dying = Stats(satiety = 0, hydration = 0, cleanliness = 0, bond = 0, calmness = 0, health = 10, energy = 0)

        // tick마다 건강 -5 → 2 tick 만에 0
        val result = calculator.advance(dying, Personality.FRIENDLY, start, start, hours(24))

        assertEquals(0, result.stats.health)
        assertEquals(hours(2), result.calculatedAt)
        assertEquals(2, result.ticksApplied)
    }

    @Test
    fun `스탯은 0 아래로 내려가지 않는다`() {
        val result = calculator.advance(initial, Personality.TIMID, start, start, hours(30))

        assertEquals(0, result.stats.satiety)
        assertEquals(0, result.stats.hydration)
    }

    @Test
    fun `계산 시각이 이미 until 이후면 아무것도 바꾸지 않는다`() {
        val result = calculator.advance(initial, Personality.ACTIVE, start, hours(5), hours(3))

        assertEquals(initial, result.stats)
        assertEquals(hours(5), result.calculatedAt)
    }

    companion object {
        /** API 명세 18.0 코드 설정값 [가안]과 같은 값 */
        val RULES = StatRules(
            initial = mapOf(
                Stat.SATIETY to 70, Stat.HYDRATION to 70, Stat.CLEANLINESS to 80, Stat.BOND to 30,
                Stat.CALMNESS to 50, Stat.HEALTH to 90, Stat.ENERGY to 60,
            ),
            decreasePerTick = mapOf(
                Stat.SATIETY to 4, Stat.HYDRATION to 5, Stat.CLEANLINESS to 2, Stat.BOND to 1,
                Stat.CALMNESS to 2, Stat.HEALTH to 0, Stat.ENERGY to 3,
            ),
            personalityDecrease = mapOf(
                Personality.ACTIVE to mapOf(Stat.ENERGY to BigDecimal("1.3"), Stat.SATIETY to BigDecimal("1.3")),
                Personality.TIMID to mapOf(Stat.CALMNESS to BigDecimal("1.5")),
                Personality.AGGRESSIVE to mapOf(Stat.BOND to BigDecimal("1.3")),
                Personality.FRIENDLY to mapOf(Stat.CALMNESS to BigDecimal("1.3")),
            ),
            healthPenalty = HealthPenalty(basicNeedBelow = 20, basicNeedAmount = 3, calmnessAtOrBelow = 20, calmnessAmount = 2),
        )
    }
}
