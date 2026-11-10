package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.domain.CareFixtures.log
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SummaryEvidenceTest {

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val codes = listOf(ActionCode.FEED, ActionCode.WATER, ActionCode.CLEAN_LITTER, ActionCode.PLAY, ActionCode.WALK_START)

    /** 일차별 (밥, 물, 배변 청소, 놀이, 산책) 횟수로 로그를 만든다 */
    private fun logs(vararg plan: List<Int>) = plan.flatMapIndexed { index, counts ->
        counts.flatMapIndexed { k, n -> List(n) { i -> log(codes[k], start.plusDays(index.toLong()).plusHours(1L + i * 3 + k)) } }
    }

    private val full = listOf(3, 3, 2, 2, 1)

    private fun evidence(
        care: CareReport,
        status: SessionStatus = SessionStatus.COMPLETED,
        expectedCost: Int = 150_000,
        spent: Map<String, Int> = mapOf("FOOD" to 100_000),
        actionCount: Int = 10,
    ) = SummaryEvidence.build(
        ReportSummaryInput(
            status = status, speciesType = SpeciesType.DOG, animalName = "초코", personality = Personality.FRIENDLY,
            endedDay = care.evaluatedDays, sessionDays = 7, expectedCost = expectedCost, totalSpent = spent.values.sum(),
            spentByCategory = ReportAggregator.CATEGORY_KEYS.associateWith { spent[it] ?: 0 },
            finalStats = CareFixtures.STATS.toMap(), actionCount = actionCount, eventCount = 0, eventResolvedCount = 0, walkCount = 0, care = care,
        ),
        care,
    )

    @Test
    fun `잘한 점은 해결한 이벤트·거의 매일 채운 행동·예산 안 지출 순이다`() {
        val care = CareFixtures.care(
            start, evaluatedDays = 3,
            logs = logs(full, listOf(3, 1, 2, 2, 1), listOf(2, 1, 2, 2, 1)),
            events = listOf(CareEventRecord("물그릇을 엎었어요", ActionCode.WATER, OccurrenceStatus.RESOLVED, start.plusHours(2), start.plusHours(2).plusMinutes(40))),
        )

        assertEquals(
            listOf(
                "1일차: '물그릇을 엎었어요'를 40분 만에 해결(물 주기)",
                "밥: 3일 중 2일 권장 횟수(3회)를 채움",
                "배변 청소: 3일 중 3일 권장 횟수(2회)를 채움",
                "놀이·간식: 3일 중 3일 권장 횟수(2회)를 채움",
                "산책: 3일 중 3일 권장 횟수(1회)를 채움",
                "예상 비용 150,000원보다 50,000원 덜 씀",
            ),
            evidence(care).good,
        )
    }

    @Test
    fun `아쉬운 점은 위험 구간·미해결 이벤트(그날 0회)·0회·권장 미달 묶음·예산 초과 순이다`() {
        val low = CareFixtures.STATS.copy(hydration = 12, cleanliness = 30)
        val care = CareFixtures.care(
            start, evaluatedDays = 3,
            logs = logs(full, listOf(3, 3, 1, 0, 1)) + log(ActionCode.FEED, start.plusDays(2).plusHours(1), low) + log(ActionCode.FEED, start.plusDays(2).plusHours(2)) +
                log(ActionCode.FEED, start.plusDays(2).plusHours(3)) + List(3) { log(ActionCode.WATER, start.plusDays(2).plusHours(4)) } +
                List(2) { log(ActionCode.PLAY, start.plusDays(2).plusHours(5)) } + log(ActionCode.WALK_START, start.plusDays(2).plusHours(6)),
            events = listOf(CareEventRecord("쿠션을 물어뜯어요", ActionCode.PLAY, OccurrenceStatus.EXPIRED, start.plusDays(1).plusHours(4), null)),
        )

        val lacking = evidence(care, expectedCost = 50_000, spent = mapOf("FOOD" to 20_000, "EVENT" to 62_000)).lacking

        assertEquals(
            listOf(
                "3일차: 수분이 12까지 떨어짐(20 미만이면 건강이 깎임)",
                "2일차: '쿠션을 물어뜯어요' 미해결(정답: 놀이), 그날 놀이·간식 0회",
                "2일차: 놀이·간식 0회(권장 2회)",
                "3일차: 배변 청소 0회(권장 2회)",
                "배변 청소 부족: 2일차(권장 2회)",
                "예상 비용보다 32,000원 더 씀, 가장 많이 쓴 분류는 병원 진료비(62,000원)",
            ),
            lacking,
        )
    }

    @Test
    fun `안정감 위험 구간은 이하 기준이고 청결도는 조사 가를 쓴다`() {
        val low = CareFixtures.STATS.copy(calmness = 20, cleanliness = 10)
        val care = CareFixtures.care(start, evaluatedDays = 1, logs = logs(full).dropLast(1) + log(ActionCode.WALK_START, start.plusHours(9), low))

        val lacking = evidence(care).lacking

        assertEquals("1일차: 청결도가 10까지 떨어짐(20 미만이면 건강이 깎임)", lacking[0])
        assertEquals("1일차: 안정감이 20까지 떨어짐(20 이하면 건강이 깎임)", lacking[1])
    }

    @Test
    fun `후보가 없으면 대체 한 줄이고 돌봄도 없으면 잘한 점은 빈 목록`() {
        val perfect = evidence(CareFixtures.care(start, evaluatedDays = 1, logs = logs(full)), expectedCost = 0, spent = emptyMap())
        assertEquals(listOf("권장 횟수에 못 미친 날과 미해결 돌발 상황이 없음"), perfect.lacking)

        val idle = CareFixtures.care(start, evaluatedDays = 2)
        assertEquals(listOf("돌봄 행동을 모두 4번 함"), evidence(idle, spent = mapOf("FOOD" to 200_000), actionCount = 4).good)
        assertEquals(emptyList(), evidence(idle, spent = mapOf("FOOD" to 200_000), actionCount = 0).good)
    }

    @Test
    fun `포기·중단이면 마지막 평가 일차는 0회·권장 미달 후보에서 뺀다`() {
        val care = CareFixtures.care(start, evaluatedDays = 2, logs = logs(full, listOf(1, 0, 0, 0, 0)))

        val abandoned = evidence(care, status = SessionStatus.ABANDONED).lacking
        val completed = evidence(care, status = SessionStatus.COMPLETED).lacking

        assertEquals(listOf("권장 횟수에 못 미친 날과 미해결 돌발 상황이 없음"), abandoned)
        assertTrue(completed.any { it.startsWith("2일차:") })
    }

    @Test
    fun `목록마다 최대 6개이고 심한 순서로 자른다`() {
        val care = CareFixtures.care(start, evaluatedDays = 7) // 7일 내내 아무것도 안 함 → 0회 후보 35개

        val lacking = evidence(care).lacking

        assertEquals(SummaryEvidence.LIMIT, lacking.size)
        assertEquals("1일차: 밥 0회(권장 3회)", lacking.first())
        assertEquals("2일차: 밥 0회(권장 3회)", lacking.last())
    }

    @Test
    fun `고양이 세션은 산책 후보가 없다`() {
        val care = CareFixtures.care(start, species = SpeciesType.CAT, evaluatedDays = 2)

        val all = evidence(care).let { it.good + it.lacking }

        assertFalse(all.any { "산책" in it })
    }

    @Test
    fun `결과 한 줄은 상태별로 다르다`() {
        val care = CareFixtures.care(start, evaluatedDays = 3)
        fun text(status: SessionStatus) = SummaryEvidence.statusText(
            ReportSummaryInput(status, SpeciesType.DOG, "초코", Personality.FRIENDLY, 3, 7, 0, 0, emptyMap(), emptyMap(), 0, 0, 0, 0, care = care),
        )

        assertEquals("7일 돌봄을 끝까지 마침", text(SessionStatus.COMPLETED))
        assertEquals("3일째에 그만둠", text(SessionStatus.ABANDONED))
        assertEquals("돌봄 부족으로 3일째에 중단됨", text(SessionStatus.FAILED))
    }
}
