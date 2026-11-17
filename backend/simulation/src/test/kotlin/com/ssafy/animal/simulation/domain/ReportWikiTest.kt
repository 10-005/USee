package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.adapter.out.summary.OpenAiReportSummaryGenerator
import com.ssafy.animal.simulation.config.SimulationConfig
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 리포트 위키(resources/report-wiki) 검사(Q521). 시나리오·스탯·돌봄을 추가하고 위키를 빠뜨리면 여기서 잡는다.
 * 위키에는 숫자를 쓰지 않는다: LLM 요약의 숫자 검증이 근거 문장 후보만 기준으로 하기 때문이다.
 */
class ReportWikiTest {

    private val wiki = SimulationConfig.loadReportWiki()

    /** 시드 SQL의 시나리오 행: (scenario_seq, title) */
    private val scenarios: Map<Long, String> = PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql")
        .flatMap { it.getContentAsString(Charsets.UTF_8).lines() }
        .mapNotNull { SCENARIO_ROW.find(it.trim()) }
        .associate { it.groupValues[1].toLong() to it.groupValues[2].replace("''", "'") }

    @Test
    fun `시드의 모든 시나리오에 event 페이지가 있고 제목이 시나리오 제목과 같으며 결과별 줄이 둘 다 있다`() {
        // 시드 행을 제대로 읽었는지(정규식이 깨져 빈 목록으로 통과하지 않게). 시나리오가 늘면 아래 검사가 위키 누락을 잡는다
        assertTrue(scenarios.keys.containsAll((1L..16L).toList()), "시드 시나리오를 읽지 못했습니다: ${scenarios.keys}")
        scenarios.forEach { (seq, title) ->
            val page = checkNotNull(wiki["event:$seq"]) { "위키에 event:$seq 가 없습니다" }
            assertEquals(title, page.title, "event:$seq 제목")
            assertTrue(!page.onResolved.isNullOrBlank() && !page.onMissed.isNullOrBlank(), "event:$seq 결과별 줄")
        }
        assertEquals(scenarios.keys.map { "event:$it" }.toSet(), wiki.pages.keys.filter { it.startsWith("event:") }.toSet(), "시드에 없는 event 페이지")
    }

    @Test
    fun `결과·성격·종·스탯·돌봄 키와 예산 주제마다 페이지가 있다`() {
        val expected = SessionStatus.entries.filter { it != SessionStatus.IN_PROGRESS }.map { "status:${it.name}" } +
            Personality.entries.map { "personality:${it.name}" } +
            SpeciesType.entries.map { "species:${it.name}" } +
            Stat.entries.map { "stat:${it.name}" } +
            CareKey.entries.map { "care:${it.name}" } +
            "topic:budget"
        expected.forEach { assertTrue(it in wiki.pages, "위키에 $it 가 없습니다") }
    }

    @Test
    fun `링크는 있는 페이지만 가리킨다`() {
        wiki.pages.values.forEach { page ->
            page.links.forEach { assertTrue(it in wiki.pages, "${page.key}의 링크 $it 가 없습니다") }
        }
    }

    @Test
    fun `숫자와 죽음 표현을 쓰지 않는다`() {
        wiki.pages.values.forEach { page ->
            val text = listOfNotNull(page.body, page.onResolved, page.onMissed).joinToString(" ")
            assertTrue(Regex("""\d""").find(text) == null, "${page.key}에 숫자가 있습니다")
            OpenAiReportSummaryGenerator.FORBIDDEN.forEach { assertTrue(it !in text, "${page.key}에 금지 표현 $it") }
        }
    }

    @Test
    fun `제목·결과별 줄·관련 줄·주석을 나눠 읽는다`() {
        val pages = ReportWiki.parse(
            """
            <!-- 머리말
            ## 주석 안 제목은 무시 -->

            ## event:3 화장실 밖에 실수했어요
            첫 줄이에요.
            둘째 줄이에요.
            해결했을 때: 잘했어요.
            놓쳤을 때: 아쉬워요.
            관련: [[care:CLEAN_LITTER]] [[stat:CLEANLINESS]]

            ## topic:budget 예산
            돈 이야기예요.
            """.trimIndent().replace("\n", "\r\n"),
        )

        assertEquals(
            listOf(
                WikiPage("event:3", "화장실 밖에 실수했어요", "첫 줄이에요. 둘째 줄이에요.", "잘했어요.", "아쉬워요.", listOf("care:CLEAN_LITTER", "stat:CLEANLINESS")),
                WikiPage("topic:budget", "예산", "돈 이야기예요."),
            ),
            pages,
        )
    }

    @Test
    fun `형식이 틀리거나 키가 겹치면 예외`() {
        assertFailsWith<IllegalStateException> { ReportWiki.parse("제목 없는 본문") }
        assertFailsWith<IllegalStateException> { ReportWiki.parse("## 잘못된 제목\n본문") }
        assertFailsWith<IllegalStateException> { ReportWiki.parse("## topic:budget 예산\n관련: [[care:FEED]]") }
        assertFailsWith<IllegalArgumentException> { ReportWiki.of(listOf("## topic:a 가\n본문", "## topic:a 나\n본문")) }
    }

    companion object {
        /** "(16, '길에서 똥을 쌌어요', '...', 'WALK', ..." 형태의 시나리오 INSERT 행 */
        private val SCENARIO_ROW = Regex("""^\((\d+), '((?:[^']|'')*)', '(?:[^']|'')*', '(?:HOME|WALK)'""")
    }
}
