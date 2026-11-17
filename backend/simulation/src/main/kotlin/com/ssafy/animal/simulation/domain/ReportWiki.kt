package com.ssafy.animal.simulation.domain

/**
 * 리포트 위키 한 페이지(Q521). key는 "종류:키"(예: event:16, stat:CALMNESS)이고 조회는 key로만 한다.
 * onResolved·onMissed는 event 페이지에서 그 결과일 때만 보내는 줄, links는 "관련:" 줄의 [[키]]다.
 */
data class WikiPage(
    val key: String,
    val title: String,
    val body: String,
    val onResolved: String? = null,
    val onMissed: String? = null,
    val links: List<String> = emptyList(),
)

/** LLM에 보내는 위키 맥락 한 건. text는 본문(+이번 결과에 해당하는 줄) */
data class WikiContext(val key: String, val title: String, val text: String)

/**
 * 모의 입양 리포트 위키(Q521). 실제 입양 맥락을 마크다운(resources의 report-wiki 폴더)으로 두고,
 * 서버가 세션 기록에서 키를 골라 해당 페이지만 LLM에 보낸다(벡터 검색 없음).
 */
class ReportWiki(pages: List<WikiPage>) {

    val pages: Map<String, WikiPage> = pages.groupBy { it.key }.mapValues { (key, same) ->
        require(same.size == 1) { "위키 페이지 키가 중복됩니다: $key" }
        same.single()
    }

    operator fun get(key: String): WikiPage? = pages[key]

    companion object {
        private val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
        private val HEADING = Regex("""^## ([a-z]+:[A-Za-z0-9_]+)\s+(\S.*)$""")
        private val LINK = Regex("""\[\[([a-z]+:[A-Za-z0-9_]+)]]""")
        private const val RESOLVED = "해결했을 때:"
        private const val MISSED = "놓쳤을 때:"
        private const val RELATED = "관련:"

        /** 위키 마크다운 한 파일을 페이지로 나눈다. 형식이 틀리면 예외(기동 시 바로 드러나게) */
        fun parse(markdown: String): List<WikiPage> {
            val pages = mutableListOf<WikiPage>()
            var current: Builder? = null
            COMMENT.replace(markdown.replace("\r\n", "\n"), "").lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                val heading = HEADING.matchEntire(line)
                when {
                    heading != null -> {
                        current?.let { pages += it.build() }
                        current = Builder(heading.groupValues[1], heading.groupValues[2].trim())
                    }
                    line.startsWith("#") -> error("위키 제목 형식이 틀렸습니다(## 종류:키 제목): $line")
                    else -> checkNotNull(current) { "위키 페이지 제목 앞에 본문이 있습니다: $line" }.add(line)
                }
            }
            current?.let { pages += it.build() }
            return pages
        }

        fun of(markdowns: List<String>): ReportWiki = ReportWiki(markdowns.flatMap(::parse))
    }

    private class Builder(val key: String, val title: String) {
        val body = mutableListOf<String>()
        var onResolved: String? = null
        var onMissed: String? = null
        val links = mutableListOf<String>()

        fun add(line: String) {
            when {
                line.startsWith(RESOLVED) -> onResolved = line.removePrefix(RESOLVED).trim()
                line.startsWith(MISSED) -> onMissed = line.removePrefix(MISSED).trim()
                line.startsWith(RELATED) -> links += LINK.findAll(line).map { it.groupValues[1] }
                else -> body += line
            }
        }

        fun build(): WikiPage {
            check(body.isNotEmpty()) { "위키 페이지 본문이 없습니다: $key" }
            return WikiPage(key, title, body.joinToString(" "), onResolved, onMissed, links.toList())
        }
    }
}

/**
 * 세션 기록에서 위키 페이지를 고른다(Q521). 순서가 우선순위이고 LIMIT까지만 고른 뒤, 고른 페이지의 링크를 한 단계 따라
 * TOTAL_LIMIT까지 채운다. 위키에 없는 키는 건너뛴다(빠진 페이지는 ReportWikiTest가 잡는다).
 *
 * 우선순위: 결과 → 성격 → 놓친 이벤트 → 위험 구간 스탯 → 첫 해결 이벤트 → 권장 미달 돌봄(조언 근거와 같은 순서) → 예산 초과
 * → 나머지 해결 이벤트 → 종. 아쉬운 점이 많아도 잘한 점 문단에 쓸 맥락이 하나는 남도록 첫 해결 이벤트를 앞에 둔다(Q522).
 *
 * 이벤트·위험 구간 스탯은 **근거 문장 후보(SummaryEvidence)에 실린 것만** 고른다(Q522). 후보는 목록마다 잘리므로(LIMIT),
 * 잘려 나간 이벤트의 맥락을 보내면 LLM이 후보에 없는 체험 사실을 지어낼 수 있다.
 */
object ReportWikiSelector {

    const val LIMIT = 8
    const val TOTAL_LIMIT = 10

    fun select(input: ReportSummaryInput, care: CareReport, wiki: ReportWiki): List<WikiContext> {
        val evidence = SummaryEvidence.build(input, care)
        // (일차, 이벤트) 중 후보에 실린 것: 해결은 good의 "n일차: '제목'…", 놓침은 lacking의 "n일차: '제목' 미해결"
        val events = care.days.take(care.evaluatedDays).flatMap { day ->
            day.events.filter { event ->
                when (event.status) {
                    OccurrenceStatus.RESOLVED -> evidence.good.any { it.startsWith("${day.day}일차: '${event.title}'") }
                    OccurrenceStatus.EXPIRED -> evidence.lacking.any { it.startsWith("${day.day}일차: '${event.title}' 미해결") }
                    OccurrenceStatus.PENDING -> false
                }
            }
        }
        // 같은 시나리오가 여러 번 나왔으면 한 페이지에 결과 줄을 모은다
        val outcomes = events.groupBy { it.scenarioSeq }.mapValues { (_, same) -> same.map { it.status }.toSet() }
        val missed = events.filter { it.status == OccurrenceStatus.EXPIRED }.map { eventKey(it.scenarioSeq) }
        val resolved = events.filter { it.status == OccurrenceStatus.RESOLVED }.map { eventKey(it.scenarioSeq) }
        // 위험 구간 줄: "n일차: 수분이 12까지 떨어짐(20 미만이면 건강이 깎임)". 괄호 안 "건강이"와 헷갈리지 않게 일차 바로 뒤만 본다
        val danger = Stat.entries
            .filter { stat -> evidence.lacking.any { "일차: ${KoreanLabels.josa(KoreanLabels.stat(stat), "이", "가")} " in it && "까지 떨어짐" in it } }
            .map { "stat:${it.name}" }
        val advice = SummaryEvidence.adviceKeys(input, care).map { "care:${it.first.name}" }
        val budget = if (input.difference < 0) listOf("topic:budget") else emptyList()

        val primary = (
            listOf("status:${input.status.name}", "personality:${input.personality.name}") +
                missed + danger + resolved.take(1) + advice + budget + resolved.drop(1) + "species:${input.speciesType.name}"
            ).distinct().filter { it in wiki.pages }.take(LIMIT)
        val linked = primary.flatMap { wiki.getValue(it).links }.filter { it in wiki.pages && it !in primary }.distinct()
        val keys = (primary + linked).take(TOTAL_LIMIT)

        return keys.map { key ->
            val page = wiki.getValue(key)
            val seq = key.removePrefix("event:").takeIf { key.startsWith("event:") }?.toLongOrNull()
            val result = seq?.let { outcomes[it] }.orEmpty()
            val lines = listOfNotNull(
                page.body,
                page.onResolved?.takeIf { OccurrenceStatus.RESOLVED in result }?.let { "해결했을 때: $it" },
                page.onMissed?.takeIf { OccurrenceStatus.EXPIRED in result }?.let { "놓쳤을 때: $it" },
            )
            WikiContext(key, page.title, lines.joinToString(" "))
        }
    }

    private fun eventKey(scenarioSeq: Long) = "event:$scenarioSeq"

    private fun ReportWiki.getValue(key: String) = checkNotNull(this[key]) { "위키 페이지가 없습니다: $key" }
}
