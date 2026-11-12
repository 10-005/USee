package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.domain.KoreanLabels
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.ReportWiki
import com.ssafy.animal.simulation.domain.ReportWikiSelector
import com.ssafy.animal.simulation.domain.SummaryEvidence
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * OpenAI Chat Completions로 리포트 요약을 만든다(Q517·Q521). 서버가 집계에서 골라 만든 근거 문장 후보와
 * 리포트 위키에서 고른 실제 입양 맥락만 보내고(원본 이력·회원 정보 없음) `{ overall, good, lacking, guide }`를 받아 빈 줄로 잇는다.
 * 이름이 없거나 후보에 없는 일차·숫자를 쓰면 실패로 본다. 위키에는 숫자가 없어(ReportWikiTest) 숫자 검증은 후보만 기준으로 한다.
 * 호출·형식·검증에 실패하면 예외를 던지고, 재시도와 규칙 문구 대체는 ReportSummaryService가 한다. API 키는 로그·예외 메시지에 남기지 않는다.
 */
class OpenAiReportSummaryGenerator(
    private val config: LlmConfig,
    private val jsonMapper: JsonMapper,
    private val wiki: ReportWiki,
    restClientBuilder: RestClient.Builder = defaultBuilder(config.timeout),
) : ReportSummaryGenerator {

    private val restClient = restClientBuilder
        .baseUrl(config.baseUrl)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${config.apiKey}")
        .build()

    override fun generate(input: ReportSummaryInput): String {
        val payload = payload(input)
        val request = mapOf(
            "model" to config.model,
            "temperature" to TEMPERATURE,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to jsonMapper.writeValueAsString(payload)),
            ),
            "response_format" to mapOf("type" to "json_object"),
        )
        val body = restClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(jsonMapper.writeValueAsString(request))
            .retrieve()
            // 오류 본문에 키 일부가 담길 수 있어(401) 상태 코드만 남긴다
            .onStatus({ it.isError }) { _, response -> throw IllegalStateException("OpenAI 호출 실패: HTTP ${response.statusCode.value()}") }
            .body(String::class.java)
        @Suppress("UNCHECKED_CAST")
        val sent = listOf("good", "lacking", "advice").flatMap { payload.getValue(it) as List<String> } +
            ((payload.getValue("result") as Map<String, Any?>).getValue("statusText") as String)
        return parse(checkNotNull(body) { "OpenAI 응답 본문이 없습니다." }, input.animalName, sent)
    }

    /** choices[0].message.content의 JSON에서 네 문단을 꺼내 검증한다. candidates는 LLM에 보낸 근거 문장(후보·advice·statusText) */
    internal fun parse(body: String, animalName: String, candidates: List<String>): String {
        val content = jsonMapper.readTree(body).path("choices").path(0).path("message").path("content")
        check(content.isString) { "OpenAI 응답에 content가 없습니다." }
        val result = jsonMapper.readTree(content.stringValue())
        val paragraphs = PARAGRAPH_KEYS.map { key ->
            val value = result.path(key)
            check(value.isString && value.stringValue().isNotBlank()) { "요약 항목이 비었습니다: $key" }
            value.stringValue().trim()
        }
        val summary = paragraphs.joinToString("\n\n")
        check(summary.length <= MAX_LENGTH) { "요약이 너무 깁니다: ${summary.length}자" }
        FORBIDDEN.firstOrNull { it in summary }?.let { error("요약에 금지 표현이 있습니다: $it") }
        MARKDOWN.find(summary)?.let { error("요약에 마크다운 기호가 있습니다: ${it.value.trim()}") }
        check(animalName in summary) { "요약에 동물 이름이 없습니다." }
        // 후보에 없는 일차를 지어냈으면 실패. 후보에 일차가 하나도 없으면 건너뛴다
        val allowed = candidates.flatMap(::daysIn).toSet()
        if (allowed.isNotEmpty()) {
            (daysIn(summary) - allowed).firstOrNull()?.let { error("요약에 근거 없는 일차가 있습니다: ${it}일차") }
        }
        // 일차가 아닌 숫자(횟수·금액·분 등)는 보낸 근거 문장에 나온 숫자여야 한다(위키 맥락에는 숫자가 없다)
        val numbers = candidates.flatMap(::numbersIn).toSet()
        (numbersIn(DAY_PATTERN.replace(summary, " ")) - numbers).firstOrNull()?.let { error("요약에 근거 없는 숫자가 있습니다: $it") }
        // 예산 줄을 보냈으면 그 금액(차액, 맞춰 썼으면 예상 비용)이 요약에 있어야 한다
        budgetAmount(candidates)?.let { check(it in numbersIn(summary)) { "요약에 예산 금액이 없습니다: $it" } }
        return summary
    }

    /** LLM에 보내는 JSON: 동물·결과, 서버가 고른 근거 문장 후보, 위키에서 고른 실제 입양 맥락(Q521) */
    internal fun payload(input: ReportSummaryInput): Map<String, Any?> {
        val care = checkNotNull(input.care) { "일차별 집계가 없어 LLM 요약을 만들 수 없습니다." }
        val evidence = SummaryEvidence.build(input, care)
        return mapOf(
            "animal" to mapOf(
                "name" to input.animalName,
                "species" to KoreanLabels.species(input.speciesType),
                "breed" to input.breedName,
                "personality" to KoreanLabels.personality(input.personality),
            ),
            "result" to mapOf(
                "status" to input.status.name,
                "statusText" to SummaryEvidence.statusText(input),
                "evaluatedDays" to care.evaluatedDays,
            ),
            "good" to evidence.good,
            "lacking" to evidence.lacking,
            "advice" to evidence.advice,
            "context" to ReportWikiSelector.select(input, care, wiki).map { mapOf("key" to it.key, "title" to it.title, "text" to it.text) },
        )
    }

    companion object {
        private val PARAGRAPH_KEYS = listOf("overall", "good", "lacking", "guide")

        /** 사실을 바꾸지 않도록 낮게 둔다 */
        const val TEMPERATURE = 0.3

        /** "3일차", "2·5일차", "2, 5일차"의 일차 번호 */
        private val DAY_PATTERN = Regex("""((?:\d+\s*[·,]\s*)*\d+)\s*일차""")

        /** 굵게·제목·목록 기호(일반 텍스트로 저장한다) */
        private val MARKDOWN = Regex("""\*\*|__|(^|\n)\s*(#{1,6}\s|[-*]\s)""")

        /** 아라비아 숫자(쉼표 금액 포함, 끝의 쉼표는 뗀다) */
        fun numbersIn(text: String): Set<String> = Regex("""\d[\d,]*""").findAll(text).map { it.value.trimEnd(',') }.toSet()

        /** "예상 비용"으로 시작하는 후보 줄의 금액: "…X원 더 씀"·"…X원 덜 씀"은 X, "…X원에 맞춰 씀"은 X */
        private val BUDGET_AMOUNT = Regex("""([\d,]+)원(?:에 맞춰| 더| 덜) 씀""")

        fun budgetAmount(candidates: List<String>): String? =
            candidates.firstOrNull { it.startsWith("예상 비용") }?.let { BUDGET_AMOUNT.find(it)?.groupValues?.get(1) }

        fun daysIn(text: String): Set<Int> =
            DAY_PATTERN.findAll(text).flatMap { m -> Regex("""\d+""").findAll(m.groupValues[1]).map { it.value.toInt() } }.toSet()

        /** 전체 길이 상한(명세: 450~650자 권장, 900자 초과는 실패, Q521) */
        const val MAX_LENGTH = 900

        /** 죽음 표현(M6). 하나라도 있으면 실패로 보고 규칙 문구로 대체된다 */
        val FORBIDDEN = listOf("죽었", "죽음", "죽어", "죽을", "죽게", "죽는", "사망", "숨졌", "숨을 거", "세상을 떠", "무지개다리", "폐사")

        private fun defaultBuilder(timeout: Duration): RestClient.Builder = RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) },
        )

        val SYSTEM_PROMPT = """
            당신은 반려동물 모의 입양 체험(7일)의 결과 리포트를 쓰는 작가입니다.
            이 리포트는 체험한 본인과, 입양 신청을 검토하는 보호소가 함께 읽습니다.
            할 일은 두 가지입니다. 체험 기록을 정확하게 돌려주고, 그 기록이 실제 입양에서 무엇을 뜻하는지 이어 주는 것입니다.

            [입력]
            사용자 메시지로 JSON 하나가 옵니다.
            - animal: 동물의 이름, 종, 품종, 성격
            - result: status(COMPLETED 끝까지 마침, ABANDONED 중간에 그만둠, FAILED 돌봄 부족으로 중단)와 statusText(결과 한 줄)
            - good, lacking: 서버가 체험 기록에서 골라 만든 근거 문장 후보입니다. good은 잘한 점, lacking은 아쉬운 점이고 lacking은 심한 순서입니다. 숫자와 일차는 정확한 값입니다.
            - advice: 조언에 쓸 수 있는 하루 권장 횟수입니다.
            - context: 위키에서 고른 실제 입양 맥락입니다. key 앞부분이 종류입니다(status 체험 결과, personality 성격, species 종, event 돌발 상황, stat 상태 수치, care 돌봄, topic 주제). title은 이름, text는 내용입니다.
              event의 "해결했을 때:", "놓쳤을 때:"는 이번 체험에서 실제로 그렇게 된 경우만 들어 있습니다.

            [출력 형식]
            반드시 JSON 객체 하나만 출력합니다: {"overall": "...", "good": "...", "lacking": "...", "guide": "..."}
            - overall(1~2문장): 첫 문장에 animal.name과 result.statusText의 결과를 넣고, 전체 인상을 한 문장 덧붙입니다.
            - good(2~3문장): good 후보에서 2개 안팎을 골라 씁니다. 고른 후보와 같은 주제의 context(event, care, stat)가 있으면 그중 하나에 "실제로도 ~" 하는 의미를 한 절 덧붙일 수 있습니다.
            - lacking(2~4문장): lacking 후보에서 2~3개를 골라 씁니다. 고른 후보와 같은 주제의 context가 있으면 실제 생활에서 어떤 문제로 이어질 수 있는지 한 절 덧붙입니다.
            - guide(2~4문장): 실제 입양을 준비한다면 무엇을 챙기면 좋을지 씁니다. lacking에서 다룬 주제의 context를 먼저 쓰고, personality나 species context가 있으면 그 아이에게 맞는 조언을 한 문장 더할 수 있습니다.
              advice에서 하나를 골라 숫자 그대로 "~해 보세요"로 끝나는 문장을 꼭 넣습니다(예: "물은 하루 3회가 권장" → "물을 하루 3회 챙겨 보세요"). advice가 비어 있으면 숫자 없이 꾸준히 챙기라는 문장을 씁니다.
            - good이나 lacking 후보에 "예상 비용"으로 시작하는 줄이 있으면, 그 줄은 고르는 개수와 별도로 그 문단에 반드시 한 문장으로 씁니다. 금액은 후보 그대로 씁니다.
            - 후보가 대체 한 줄뿐이면(예: "권장 횟수에 못 미친 날과 미해결 돌발 상황이 없음") 그 내용만 짧게 씁니다. good 후보가 비어 있으면 good에는 돌봄 기록이 거의 없었다는 내용만 한 문장으로 씁니다.
            - 네 값을 합쳐 450~650자 정도로 씁니다. 900자를 넘기면 안 됩니다.

            [사실 규칙]
            - 체험에서 있었던 일은 good, lacking 후보에서만 씁니다. context의 내용을 이번 체험에서 있었던 일처럼 쓰지 않습니다.
            - 실제 입양에 관한 설명은 context에 있는 내용만 씁니다. 병명, 비용, 법, 기간처럼 context에 없는 사실을 덧붙이지 않습니다.
            - 숫자와 일차는 good, lacking, advice, statusText에 있는 것만 씁니다. 후보의 일차와 숫자를 바꾸지 않습니다. context에는 숫자가 없으니 context에서 숫자를 만들지 않습니다.
            - 후보 하나는 한 문장(또는 한 절)으로 씁니다. 서로 다른 후보를 "모두", "둘 다", "~와 ~에는"으로 합치지 않습니다. 같은 일차로 시작하는 후보만 이어 쓸 수 있습니다.
            - 서로 다른 일차의 후보를 원인과 결과로 잇지 않습니다.
            - context의 "해결했을 때:", "놓쳤을 때:" 같은 이름표와 key는 글에 그대로 옮기지 않습니다. context 문장을 통째로 베끼지 말고 짧게 풀어 씁니다.

            [말투와 금지 사항]
            - 모든 문장을 "~했어요", "~였어요"처럼 "~요"로 끝냅니다. 조언 문장은 "~해 보세요"로 씁니다.
            - 체험 기록과 실제 이야기를 섞지 않도록 실제 이야기는 "실제로는", "실제 입양에서는"처럼 구분해서 씁니다.
            - "~하였고", "~하였습니다", "~할 계획이에요"처럼 딱딱하거나 본인이 말하는 투를 쓰지 않습니다.
            - 읽는 사람을 부르는 호칭이나 주어(님, 당신, 보호자님, 사용자 등)를 쓰지 않습니다.
            - 마크다운(굵게, 목록, 제목 기호 등)과 이모지를 쓰지 않습니다.
            - result.status가 FAILED나 ABANDONED여도 비난하지 않습니다.
            - 죽음과 관련된 표현은 절대 쓰지 않습니다.

            [예시]
            입력:
            {"animal":{"name":"나비","species":"고양이","breed":"코리안 숏헤어","personality":"소심함"},
             "result":{"status":"COMPLETED","statusText":"7일 돌봄을 끝까지 마침","evaluatedDays":7},
             "good":["2일차: '헤어볼을 토했어요'를 25분 만에 해결(목욕)","밥: 7일 중 7일 권장 횟수(3회)를 채움"],
             "lacking":["5일차: 청결도가 15까지 떨어짐(20 미만이면 건강이 깎임)","5일차: 배변 청소 0회(권장 2회)","놀이·간식 부족: 1·4일차(권장 2회)","예상 비용보다 8,000원 더 씀, 가장 많이 쓴 분류는 사료(21,200원)"],
             "advice":["배변 청소는 하루 2회가 권장","놀이·간식은 하루 2회가 권장"],
             "context":[
              {"key":"status:COMPLETED","title":"끝까지 마침","text":"실제 입양은 체험 같은 하루가 오랜 세월 이어지는 일이에요. …"},
              {"key":"personality:TIMID","title":"소심함","text":"소심한 아이는 낯선 환경과 큰 소리에 쉽게 놀라요. 실제로는 서두르지 않고 조용히 곁에 있어 주는 시간이 쌓여야 마음을 열고, …"},
              {"key":"stat:CLEANLINESS","title":"청결도","text":"실제로는 배변 공간이나 몸 관리가 밀려 지저분해지면 피부 문제나 냄새, 배변 실수로 이어지기 쉬워요."},
              {"key":"care:CLEAN_LITTER","title":"배변 청소","text":"배변 공간이 더러우면 다른 곳에 실수하거나 참게 돼요. 실제로는 볼일을 본 뒤 바로 치우는 게 기본이고, …"},
              {"key":"topic:budget","title":"예산","text":"실제 입양 비용은 처음 준비물보다 사료, 배변 용품 같은 소모품과 예상하지 못한 병원비가 꾸준히 더 들어요. …"},
              {"key":"event:7","title":"헤어볼을 토했어요","text":"… 해결했을 때: 빗질로 빠진 털을 정리해 준 건 헤어볼을 줄이는 가장 기본적인 관리예요."}]}
            출력:
            {"overall":"나비의 7일 돌봄을 끝까지 마쳤어요. 끼니는 꾸준히 챙겼지만 화장실 관리에서 아쉬움이 남았어요.",
             "good":"2일차에 '헤어볼을 토했어요' 상황을 25분 만에 목욕으로 해결했어요. 실제로도 털을 정리해 주는 건 헤어볼을 줄이는 가장 기본적인 관리예요. 밥은 7일 내내 권장 횟수인 3회를 채웠어요.",
             "lacking":"5일차에는 배변 청소를 한 번도 하지 않았고, 그날 청결도가 15까지 떨어졌어요. 실제로는 지저분한 화장실이 피부 문제나 배변 실수로 이어지기 쉬워요. 놀이·간식은 1·4일차에 권장 2회보다 적었어요. 예상 비용보다 8,000원을 더 썼고, 가장 많이 쓴 분류는 사료(21,200원)였어요.",
             "guide":"실제 입양에서는 볼일을 본 뒤 바로 치우는 습관이 기본이에요. 배변 청소를 하루 2회 챙겨 보세요. 소심한 아이는 조용히 곁에 있어 주는 시간이 쌓여야 마음을 열어요. 사료 같은 소모품과 갑작스러운 병원비까지 생각해 예산을 여유 있게 잡아 두면 좋아요."}
        """.trimIndent()
    }
}
