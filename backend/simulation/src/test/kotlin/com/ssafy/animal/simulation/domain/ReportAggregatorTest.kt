package com.ssafy.animal.simulation.domain

import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportAggregatorTest {

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val session = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
        initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
    ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }

    @Test
    fun `지출을 분류별·일자별로 합치고 지출 없는 분류·날도 0으로 둔다`() {
        val spends = listOf(
            SpendRecord("FOOD_BOWL", 10_000, start.plusMinutes(5)),
            SpendRecord("FOOD", 21_200, start.plusHours(23)),
            SpendRecord("FOOD", 10_600, start.plusHours(25)),
            SpendRecord(ReportAggregator.EVENT_KEY, 30_000, start.plusDays(3).plusHours(1)),
        )

        val report = ReportAggregator.aggregate(ReportSource(session, spends, 41, 9, 6, 6), sessionDays = 7)

        assertEquals(71_800, report.totalSpent)
        assertEquals(150_000 - 71_800, report.difference)
        assertEquals(10_000, report.spentByCategoryJsonb["FOOD_BOWL"])
        assertEquals(31_800, report.spentByCategoryJsonb["FOOD"])
        assertEquals(30_000, report.spentByCategoryJsonb["EVENT"])
        assertEquals(0, report.spentByCategoryJsonb["CATNIP"])
        assertEquals(ReportAggregator.CATEGORY_KEYS, report.spentByCategoryJsonb.keys.toList())
        assertEquals(
            mapOf("1" to 31_200, "2" to 10_600, "3" to 0, "4" to 30_000, "5" to 0, "6" to 0, "7" to 0),
            report.dailySpentJsonb,
        )
        assertEquals(session.stats().toMap(), report.finalStatsJsonb)
        assertEquals(listOf(41, 9, 6, 6), listOf(report.actionCount, report.eventCount, report.eventResolvedCount, report.walkCount))
        assertEquals(3001L, report.sessionSeq)
        assertEquals(null, report.summary)
    }

    @Test
    fun `일차는 시작 기준이고 범위를 벗어나면 첫날·마지막 날로 붙인다`() {
        assertEquals(1, ReportAggregator.dayOf(start, start, 7))
        assertEquals(1, ReportAggregator.dayOf(start, start.plusHours(23).plusMinutes(59), 7))
        assertEquals(2, ReportAggregator.dayOf(start, start.plusDays(1), 7))
        assertEquals(7, ReportAggregator.dayOf(start, start.plusDays(7), 7))
        assertEquals(1, ReportAggregator.dayOf(start, start.minusMinutes(1), 7))
    }

    @Test
    fun `지출이 없으면 총지출 0이고 차이는 예상 비용 그대로다`() {
        val report = ReportAggregator.aggregate(ReportSource(session, emptyList(), 0, 0, 0, 0), sessionDays = 7)

        assertEquals(0, report.totalSpent)
        assertEquals(150_000, report.difference)
        assertEquals(7, report.dailySpentJsonb.size)
    }
}
