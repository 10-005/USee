package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.domain.KoreanLabels
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.SummaryEvidence
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * OpenAI Chat Completions로 리포트 요약을 만든다(Q517). 서버가 집계에서 골라 만든 근거 문장 후보만 보내고(원본 이력·회원 정보 없음)
 * `{ overall, good, lacking }`을 받아 빈 줄로 잇는다. 이름이 없거나 후보에 없는 일차를 쓰면 실패로 본다. 호출·형식·검증에 실패하면 예외를 던지고,
 * 재시도와 규칙 문구 대체는 ReportSummaryService가 한다. API 키는 로그·예외 메시지에 남기지 않는다.
 */
class OpenAiReportSummaryGenerator(
    private val config: LlmConfig,
    private val jsonMapper: JsonMapper,
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
        return parse(
            checkNotNull(body) { "OpenAI 응답 본문이 없습니다." },
            input.animalName,
            (payload.getValue("good") as List<String>) + (payload.getValue("lacking") as List<String>),
        )
    }

    /** choices[0].message.content의 JSON에서 세 문단을 꺼내 검증한다. candidates는 LLM에 보낸 근거 문장 후보 */
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
        check(animalName in summary) { "요약에 동물 이름이 없습니다." }
        // 후보에 없는 일차를 지어냈으면 실패. 후보에 일차가 하나도 없으면 건너뛴다
        val allowed = candidates.flatMap(::daysIn).toSet()
        if (allowed.isNotEmpty()) {
            (daysIn(summary) - allowed).firstOrNull()?.let { error("요약에 근거 없는 일차가 있습니다: ${it}일차") }
        }
        return summary
    }

    /** LLM에 보내는 JSON(2차 수정): 동물·결과와 서버가 고른 근거 문장 후보만 보낸다 */
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
        )
    }

    companion object {
        private val PARAGRAPH_KEYS = listOf("overall", "good", "lacking")

        /** 사실을 바꾸지 않도록 낮게 둔다 */
        const val TEMPERATURE = 0.3

        /** "3일차", "2·5일차", "2, 5일차"의 일차 번호 */
        private val DAY_PATTERN = Regex("""((?:\d+\s*[·,]\s*)*\d+)\s*일차""")

        fun daysIn(text: String): Set<Int> =
            DAY_PATTERN.findAll(text).flatMap { m -> Regex("""\d+""").findAll(m.groupValues[1]).map { it.value.toInt() } }.toSet()

        /** 전체 길이 상한(명세: 300~400자 권장, 600자 초과는 실패) */
        const val MAX_LENGTH = 600

        /** 죽음 표현(M6). 하나라도 있으면 실패로 보고 규칙 문구로 대체된다 */
        val FORBIDDEN = listOf("죽었", "죽음", "죽어", "죽을", "죽게", "죽는", "사망", "숨졌", "숨을 거", "세상을 떠", "무지개다리", "폐사")

        private fun defaultBuilder(timeout: Duration): RestClient.Builder = RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) },
        )

        val SYSTEM_PROMPT = """
            당신은 반려동물 모의 입양 체험(7일)의 결과 리포트 요약을 쓰는 도우미입니다.
            사용자 메시지로 JSON 하나가 옵니다. animal은 동물 정보, result.statusText는 체험 결과입니다.
            good(잘한 점)과 lacking(아쉬운 점)은 서버가 집계에서 골라 만든 근거 문장 후보입니다. lacking은 심한 순서입니다.
            당신은 후보를 골라 자연스러운 문장으로 잇기만 합니다.

            [출력 형식]
            - 반드시 JSON 객체 하나만 출력합니다: {"overall": "...", "good": "...", "lacking": "..."}
            - overall: 첫 문장에 animal.name과 result.statusText의 결과를 넣고, 전체 인상을 한 줄 덧붙입니다.
            - good: good 후보에서 2개 안팎을 골라 씁니다.
            - lacking: lacking 후보에서 2~3개를 골라 쓰고, 마지막 문장은 "~해 보세요"로 끝나는 조언 하나로 마칩니다.
            - 후보가 대체 한 줄뿐이면(예: "권장 횟수에 못 미친 날과 미해결 돌발 상황이 없음") 그 내용만 짧게 씁니다. good 후보가 비어 있으면 good에는 돌봄 기록이 거의 없었다는 내용만 한 문장으로 씁니다.
            - 세 값을 합쳐 300~400자 정도로 씁니다. 600자를 넘기면 안 됩니다.

            [사실 규칙]
            - 후보에 없는 사실, 숫자, 일차를 쓰지 않습니다. 후보의 일차와 숫자를 바꾸지 않습니다.
            - 서로 다른 일차의 후보를 원인과 결과로 잇지 않습니다. 같은 일차의 후보만 한 문장으로 묶을 수 있습니다.

            [말투와 금지 사항]
            - 모든 문장을 "~했어요", "~였어요"처럼 "~요"로 끝냅니다. lacking의 마지막 조언만 "~해 보세요"로 씁니다.
            - "~하였고", "~하였습니다", "~할 계획이에요"처럼 딱딱하거나 본인이 말하는 투를 쓰지 않습니다.
            - 읽는 사람을 부르는 호칭이나 주어(님, 당신, 보호자님, 사용자 등)를 쓰지 않습니다. 본인과 보호소가 같은 글을 봅니다.
            - 마크다운(굵게, 목록, 제목 기호 등)과 이모지를 쓰지 않습니다.
            - result.status가 FAILED(돌봄 부족으로 중단)나 ABANDONED(중도 포기)여도 비난하지 않습니다.
            - 죽음과 관련된 표현은 절대 쓰지 않습니다.

            [예시]
            입력:
            {"animal":{"name":"나비","species":"고양이","breed":"코리안 숏헤어","personality":"소심함"},
             "result":{"status":"COMPLETED","statusText":"7일 돌봄을 끝까지 마침","evaluatedDays":7},
             "good":["2일차: '헤어볼을 토했어요'를 25분 만에 해결(목욕)","밥: 7일 중 7일 권장 횟수(3회)를 채움"],
             "lacking":["5일차: 청결도가 15까지 떨어짐(20 미만이면 건강이 깎임)","5일차: 배변 청소 0회(권장 2회)","놀이·간식 부족: 1·4일차(권장 2회)"]}
            출력:
            {"overall":"나비의 7일 돌봄을 끝까지 마쳤어요. 끼니는 꾸준히 챙겼지만 화장실 관리에서 아쉬움이 남았어요.",
             "good":"2일차에 '헤어볼을 토했어요' 상황을 25분 만에 목욕으로 해결했어요. 밥은 7일 내내 권장 횟수인 3회를 채웠어요.",
             "lacking":"5일차에는 배변 청소를 한 번도 하지 않았고, 그날 청결도가 15까지 떨어졌어요. 1일차와 4일차에는 놀이·간식이 권장 2회보다 적었어요. 화장실은 하루 두 번 치워 주는 습관을 들여 보세요."}
        """.trimIndent()
    }
}
