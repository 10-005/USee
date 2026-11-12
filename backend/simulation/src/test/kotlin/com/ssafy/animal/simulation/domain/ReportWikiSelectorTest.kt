package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.domain.CareFixtures.log
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ReportWikiSelectorTest {

    private val start = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.ofHours(9))

    /** 키마다 본문이 "{key} 본문"인 시험용 위키. 링크는 실제 위키와 비슷하게 둔다 */
    private val wiki = ReportWiki(
        listOf(
            page("status:COMPLETED"), page("status:ABANDONED"), page("personality:TIMID", "stat:CALMNESS"), page("species:DOG", "care:WALK"),
            page("event:4", "stat:CALMNESS", resolved = "해결 줄", missed = "놓침 줄"),
            page("event:16", "care:WALK", resolved = "해결 줄", missed = "놓침 줄"),
            page("stat:CALMNESS"), page("stat:SATIETY"), page("stat:HYDRATION"),
            page("care:FEED", "stat:SATIETY"), page("care:WATER", "stat:HYDRATION"), page("care:WALK"),
            page("topic:budget"),
        ),
    )

    private fun page(key: String, vararg links: String, resolved: String? = null, missed: String? = null) =
        WikiPage(key, "$key 제목", "$key 본문", resolved, missed, links.toList())

    private fun input(status: SessionStatus = SessionStatus.COMPLETED, expectedCost: Int = 50_000, care: CareReport) = ReportSummaryInput(
        status = status, speciesType = SpeciesType.DOG, animalName = "초코", personality = Personality.TIMID,
        endedDay = 7, sessionDays = 7, expectedCost = expectedCost, totalSpent = 60_000,
        spentByCategory = ReportAggregator.CATEGORY_KEYS.associateWith { if (it == "FOOD") 60_000 else 0 },
        finalStats = CareFixtures.STATS.toMap(), actionCount = 1, eventCount = 3, eventResolvedCount = 1, walkCount = 0, care = care,
    )

    @Test
    fun `결과·성격·놓친 이벤트·위험 스탯·조언 돌봄·예산·해결 이벤트 순으로 8개를 고르고 링크로 10개까지 채운다`() {
        val care = CareFixtures.care(
            start,
            logs = listOf(log(ActionCode.PLAY, start.plusHours(1), CareFixtures.STATS.copy(calmness = 10))),
            events = listOf(
                CareEventRecord(4L, "천둥소리에 숨었어요", ActionCode.PLAY, OccurrenceStatus.RESOLVED, start.plusDays(1), start.plusDays(1).plusMinutes(5)),
                CareEventRecord(16L, "길에서 똥을 쌌어요", ActionCode.CLEAN_UP_POOP, OccurrenceStatus.EXPIRED, start.plusDays(2), null),
                CareEventRecord(4L, "천둥소리에 숨었어요", ActionCode.PLAY, OccurrenceStatus.EXPIRED, start.plusDays(4), null),
            ),
        )

        val context = ReportWikiSelector.select(input(care = care), care, wiki)

        // 놓친 이벤트는 일차 순(16 → 4), 조언 돌봄은 미달 일수가 같아 선언 순(FEED → WATER), species:DOG는 8개에서 잘린다.
        // 링크: event:16 → care:WALK, care:FEED → stat:SATIETY (stat:HYDRATION은 10개에서 잘린다)
        assertEquals(
            listOf(
                "status:COMPLETED", "personality:TIMID", "event:16", "event:4", "stat:CALMNESS", "care:FEED", "care:WATER", "topic:budget",
                "care:WALK", "stat:SATIETY",
            ),
            context.map { it.key },
        )
        // 같은 시나리오가 해결·놓침 둘 다 있으면 두 줄을 모두, 한쪽만 있으면 그 줄만 붙인다
        assertEquals("event:4 본문 해결했을 때: 해결 줄 놓쳤을 때: 놓침 줄", context.single { it.key == "event:4" }.text)
        assertEquals("event:16 본문 놓쳤을 때: 놓침 줄", context.single { it.key == "event:16" }.text)
        assertEquals("status:COMPLETED 제목", context.first().title)
    }

    @Test
    fun `예산 안이고 이벤트·위험 구간이 없으면 결과·성격·조언 돌봄·종만 고르고, 위키에 없는 키는 건너뛴다`() {
        val full = ActionCode.entries.filter { CareKey.of(it) != null && it != ActionCode.WATER && it != ActionCode.GIVE_WATER }
        val logs = (0L until 7L).flatMap { day -> full.flatMap { code -> List(3) { log(code, start.plusDays(day).plusHours(1L + it)) } } }
        val care = CareFixtures.care(start, logs = logs)

        val context = ReportWikiSelector.select(input(status = SessionStatus.FAILED, expectedCost = 90_000, care = care), care, wiki)

        // status:FAILED 페이지가 없어 빠지고, 물만 권장에 못 미쳐 care:WATER 하나
        assertEquals(listOf("personality:TIMID", "care:WATER", "species:DOG", "stat:CALMNESS", "stat:HYDRATION", "care:WALK"), context.map { it.key })
    }
}
