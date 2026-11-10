package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.domain.CareFixtures.care
import com.ssafy.animal.simulation.domain.CareFixtures.log
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CareAggregatorTest {

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `일차는 시작 기준 24시간 단위이고 경계 시각은 다음 일차다`() {
        val report = care(
            start,
            logs = listOf(
                log(ActionCode.FEED, start.plusHours(23).plusMinutes(59)),
                log(ActionCode.FEED, start.plusDays(1)),
                log(ActionCode.FEED, start.plusDays(7)), // 예정 종료 시각은 7일차로 붙는다
            ),
        )

        assertEquals(listOf(1, 1, 0, 0, 0, 0, 1), report.days.map { it.counts.getValue(CareKey.FEED) })
        assertEquals((1..7).toList(), report.days.map { it.day })
    }

    @Test
    fun `물·놀이 키는 산책 중 물·간식과 캣닢을 합산하고 목욕·약·병원·배변 치우기는 세지 않는다`() {
        val at = start.plusHours(2)
        val report = care(
            start,
            logs = listOf(ActionCode.WATER, ActionCode.GIVE_WATER, ActionCode.PLAY, ActionCode.SNACK, ActionCode.GIVE_SNACK, ActionCode.GIVE_CATNIP,
                ActionCode.WALK_START, ActionCode.WALK_END, ActionCode.CLEAN_LITTER, ActionCode.BATHE, ActionCode.MEDICINE, ActionCode.VET_VISIT,
                ActionCode.CLEAN_UP_POOP).map { log(it, at) },
        )

        assertEquals(mapOf(CareKey.FEED to 0, CareKey.WATER to 2, CareKey.CLEAN_LITTER to 1, CareKey.PLAY to 4, CareKey.WALK to 1), report.days[0].counts)
    }

    @Test
    fun `고양이 세션은 권장·일차별 모두 WALK 키가 없다`() {
        val report = care(start, species = SpeciesType.CAT, logs = listOf(log(ActionCode.GIVE_CATNIP, start.plusHours(1))))

        assertEquals(listOf(CareKey.FEED, CareKey.WATER, CareKey.CLEAN_LITTER, CareKey.PLAY), report.guideline.keys.toList())
        report.days.forEach { assertFalse(CareKey.WALK in it.counts) }
        assertFalse(CareKey.WALK in report.belowGuidelineDays)
        assertEquals(1, report.days[0].counts.getValue(CareKey.PLAY))
    }

    @Test
    fun `하루 최저 스탯은 행동 직전·직후 값 중 최솟값이고 행동이 없는 날은 null`() {
        val after = CareFixtures.STATS.copy(satiety = 45, hydration = 15)
        val report = care(
            start,
            logs = listOf(
                log(ActionCode.FEED, start.plusHours(3), after, mapOf("satiety" to 30)), // 직전 포만감 15
                log(ActionCode.PLAY, start.plusHours(5), after.copy(calmness = 25), mapOf("calmness" to 10)), // 직전 안정감 15
                log(ActionCode.WALK_START, start.plusHours(6), after), // delta 없음
            ),
        )

        val lowest = report.days[0].lowestStats!!
        assertEquals(15, lowest.getValue("satiety"))
        assertEquals(15, lowest.getValue("hydration"))
        assertEquals(15, lowest.getValue("calmness"))
        assertEquals(80, lowest.getValue("cleanliness"))
        assertNull(report.days[1].lowestStats)
    }

    @Test
    fun `평가 일차 뒤는 0이고 이탈 판정에서 빠지며 권장 미달·위험 구간·미해결 이벤트를 표시한다`() {
        val low = CareFixtures.STATS.copy(hydration = 19, calmness = 20)
        val report = care(
            start,
            evaluatedDays = 2,
            logs = listOf(
                *Array(3) { log(ActionCode.FEED, start.plusHours(1L + it)) },
                log(ActionCode.WATER, start.plusDays(1).plusHours(1), low),
                log(ActionCode.FEED, start.plusDays(3)), // 평가 일차 뒤(종료 뒤) 로그는 세지 않는다
            ),
            events = listOf(
                CareEventRecord("물그릇을 엎었어요", ActionCode.WATER, OccurrenceStatus.RESOLVED, start.plusHours(2), start.plusHours(2).plusMinutes(42)),
                CareEventRecord("쿠션을 물어뜯어요", ActionCode.PLAY, OccurrenceStatus.EXPIRED, start.plusDays(1).plusHours(3), null),
            ),
            dailySpent = mapOf("1" to 31_200, "2" to 0),
        )

        assertEquals(2, report.evaluatedDays)
        assertEquals(0, report.days[3].counts.getValue(CareKey.FEED))
        assertEquals(listOf(2), report.belowGuidelineDays[CareKey.FEED])
        assertEquals(listOf(1, 2), report.belowGuidelineDays[CareKey.WALK])
        assertEquals(listOf(1, 2), report.belowGuidelineDays[CareKey.WATER])
        assertEquals(mapOf("hydration" to listOf(2), "calmness" to listOf(2)), report.dangerZoneDays)
        assertEquals(1, report.unresolvedEventCount)
        assertEquals(listOf(DayEvent("물그릇을 엎었어요", ActionCode.WATER, OccurrenceStatus.RESOLVED, 42)), report.days[0].events)
        assertNull(report.days[1].events.single().minutesToResolve)
        assertEquals(31_200, report.days[0].spent)
        assertEquals(0, report.days[6].spent)
    }
}
