package com.ssafy.animal.simulation.domain

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportSummaryWriterTest {

    private val goodStats = mapOf("satiety" to 55, "hydration" to 60, "cleanliness" to 70, "bond" to 62, "calmness" to 72, "health" to 84, "energy" to 58)

    private fun input(
        status: SessionStatus = SessionStatus.COMPLETED,
        speciesType: SpeciesType = SpeciesType.DOG,
        endedDay: Int = 7,
        expectedCost: Int = 150_000,
        spent: Map<String, Int> = mapOf("FOOD" to 40_000, "EVENT" to 80_000, "TOY" to 18_000, "SNACK" to 44_000),
        finalStats: Map<String, Int> = goodStats,
        actionCount: Int = 41,
        eventCount: Int = 9,
        eventResolvedCount: Int = 6,
        walkCount: Int = 6,
    ) = ReportSummaryInput(
        status = status, speciesType = speciesType, animalName = "초코", personality = Personality.FRIENDLY, endedDay = endedDay, sessionDays = 7,
        expectedCost = expectedCost, totalSpent = spent.values.sum(),
        spentByCategory = ReportAggregator.CATEGORY_KEYS.associateWith { spent[it] ?: 0 },
        finalStats = finalStats, actionCount = actionCount, eventCount = eventCount, eventResolvedCount = eventResolvedCount, walkCount = walkCount,
    )

    @Test
    fun `완료 세션은 결과, 돌봄·이벤트, 지출·컨디션, 실제 입양 조언 네 문단으로 말한다`() {
        assertEquals(
            "초코의 7일 돌봄을 끝까지 마쳤어요.\n\n" +
                "돌봄은 모두 41번 했어요. 산책은 6번 다녀왔어요. 돌발 상황 9건 중 6건을 해결했어요.\n\n" +
                "예상보다 32,000원을 더 썼어요. 병원 진료비에 가장 많이 썼어요(80,000원). 마지막까지 컨디션을 잘 지켰어요.\n\n" +
                "실제 입양에서는 밥, 물, 배변 관리 같은 기본 돌봄을 매일 같은 시간에 챙기는 습관이 가장 중요해요.",
            ReportSummaryWriter.write(input()),
        )
    }

    @Test
    fun `일차별 집계가 있으면 조언 문단에 가장 많이 못 채운 돌봄의 권장 횟수를 쓴다`() {
        val start = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9))
        val full = (0L until 7L).flatMap { day ->
            listOf(ActionCode.FEED, ActionCode.WATER, ActionCode.CLEAN_LITTER).flatMap { code -> List(3) { CareFixtures.log(code, start.plusDays(day).plusHours(1L + it)) } }
        }
        val text = ReportSummaryWriter.write(input().copy(care = CareFixtures.care(start, logs = full)))

        // 놀이·간식과 산책이 7일 모두 미달이라 선언 순으로 놀이·간식
        assertEquals(
            "실제 입양에서는 기본 돌봄을 매일 같은 시간에 챙기는 습관이 가장 중요해요. 놀이·간식을 하루 2회 챙겨 보세요.",
            text.split("\n\n").last(),
        )
    }

    @Test
    fun `돌봄 부족 중단은 일차와 낮은 스탯을 말하고 죽음 표현을 쓰지 않는다`() {
        val stats = goodStats + mapOf("health" to 0, "hydration" to 12, "satiety" to 25)
        val text = ReportSummaryWriter.write(
            input(status = SessionStatus.FAILED, endedDay = 3, spent = mapOf("FOOD_BOWL" to 10_000), finalStats = stats, eventCount = 2, eventResolvedCount = 0),
        )

        assertTrue(text.startsWith("돌봄이 부족해 3일째에 초코의 체험이 중단됐어요."))
        assertTrue("예상보다 140,000원 덜 썼어요." in text)
        assertTrue("밥그릇에 가장 많이 썼어요(10,000원)." in text)
        assertTrue("돌발 상황 2건을 해결하지 못했어요." in text)
        // 낮은 순서로 두 개까지(지출·컨디션 문단 끝)
        assertTrue(text.split("\n\n")[2].endsWith("마지막에 건강·수분 수치가 낮았어요."))
        listOf("죽", "사망", "숨").forEach { assertFalse(it in text, "금지 표현: $it") }
    }

    @Test
    fun `포기·무지출·무행동·고양이는 그에 맞는 문구를 쓰고 없는 항목은 뺀다`() {
        val text = ReportSummaryWriter.write(
            input(
                status = SessionStatus.ABANDONED, speciesType = SpeciesType.CAT, endedDay = 1, spent = emptyMap(),
                actionCount = 0, eventCount = 0, eventResolvedCount = 0, walkCount = 0,
            ),
        )

        assertEquals(
            "1일째에 초코의 돌봄을 그만뒀어요.\n\n돌봄 행동은 한 번도 하지 않았어요.\n\n아무것도 사지 않아 지출이 없었어요.\n\n" +
                "실제 입양에서는 밥, 물, 배변 관리 같은 기본 돌봄을 매일 같은 시간에 챙기는 습관이 가장 중요해요.",
            text,
        )
    }

    @Test
    fun `예상과 같게 썼거나 이벤트를 모두 해결하면 그렇게 말한다`() {
        val text = ReportSummaryWriter.write(input(expectedCost = 182_000, eventCount = 3, eventResolvedCount = 3))

        assertTrue("예상한 비용과 똑같이 썼어요." in text)
        assertTrue("돌발 상황 3건을 모두 잘 해결했어요." in text)
    }

    @Test
    fun `지출이 같은 분류가 여럿이면 분류 순서가 앞선 것을 말한다`() {
        val text = ReportSummaryWriter.write(input(spent = mapOf("FOOD" to 10_000, "FOOD_BOWL" to 10_000)))

        assertTrue("밥그릇에 가장 많이 썼어요(10,000원)." in text)
    }
}
