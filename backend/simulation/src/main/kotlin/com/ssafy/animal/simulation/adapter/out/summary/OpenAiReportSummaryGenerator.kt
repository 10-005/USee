package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.ReportSummaryWriter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.Duration

/**
 * OpenAI Chat Completions로 리포트 요약을 만든다(Q517). 서버가 계산한 일차별 집계 JSON만 보내고(원본 이력·회원 정보 없음)
 * `{ overall, good, lacking }`을 받아 빈 줄로 잇는다. 호출·형식·검증에 실패하면 예외를 던지고,
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
        val request = mapOf(
            "model" to config.model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to SYSTEM_PROMPT),
                mapOf("role" to "user", "content" to jsonMapper.writeValueAsString(payload(input))),
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
        return parse(checkNotNull(body) { "OpenAI 응답 본문이 없습니다." })
    }

    /** choices[0].message.content의 JSON에서 세 문단을 꺼내 검증한다 */
    internal fun parse(body: String): String {
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
        return summary
    }

    /** LLM에 보내는 집계 JSON(명세 18.18 Q517 입력). 평가 일차까지만 보낸다 */
    internal fun payload(input: ReportSummaryInput): Map<String, Any?> {
        val care = checkNotNull(input.care) { "일차별 집계가 없어 LLM 요약을 만들 수 없습니다." }
        val top = ReportAggregator.CATEGORY_KEYS
            .map { it to (input.spentByCategory[it] ?: 0) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
        return mapOf(
            "animal" to mapOf(
                "name" to input.animalName,
                "species" to input.speciesType.name,
                "breed" to input.breedName,
                "personality" to input.personality.name,
            ),
            "result" to mapOf(
                "status" to input.status.name,
                "endedDay" to input.endedDay,
                "sessionDays" to input.sessionDays,
                "evaluatedDays" to care.evaluatedDays,
            ),
            "careGuideline" to care.guideline.mapKeys { it.key.name },
            "budget" to mapOf(
                "expectedCost" to input.expectedCost,
                "totalSpent" to input.totalSpent,
                "referenceCost" to input.referenceCost,
                "topCategory" to top?.let { mapOf("name" to ReportSummaryWriter.categoryLabel(it.first), "amount" to it.second) },
            ),
            "days" to care.days.take(care.evaluatedDays).map { day ->
                mapOf(
                    "day" to day.day,
                    "care" to day.counts.mapKeys { it.key.name },
                    "spent" to day.spent,
                    "lowestStats" to day.lowestStats,
                    "events" to day.events.map {
                        mapOf(
                            "title" to it.title,
                            "answerAction" to it.resolveActionCode.name,
                            "result" to it.status.name,
                            "minutesToResolve" to it.minutesToResolve,
                        )
                    },
                )
            },
            "deviations" to mapOf(
                "belowGuidelineDays" to care.belowGuidelineDays.mapKeys { it.key.name },
                "dangerZoneDays" to care.dangerZoneDays,
                "unresolvedEventCount" to care.unresolvedEventCount,
            ),
            "finalStats" to input.finalStats,
        )
    }

    companion object {
        private val PARAGRAPH_KEYS = listOf("overall", "good", "lacking")

        /** 전체 길이 상한(명세: 300~400자 권장, 600자 초과는 실패) */
        const val MAX_LENGTH = 600

        /** 죽음 표현(M6). 하나라도 있으면 실패로 보고 규칙 문구로 대체된다 */
        val FORBIDDEN = listOf("죽었", "죽음", "죽어", "죽을", "죽게", "죽는", "사망", "숨졌", "숨을 거", "세상을 떠", "무지개다리", "폐사")

        private fun defaultBuilder(timeout: Duration): RestClient.Builder = RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) },
        )

        val SYSTEM_PROMPT = """
            당신은 반려동물 모의 입양 체험(7일)의 결과 리포트 요약을 쓰는 도우미입니다.
            사용자 메시지로 서버가 계산한 집계 JSON 하나가 옵니다. 계산과 기준 이탈 판정은 서버가 이미 했으니, 당신은 그 결과를 설명만 합니다.

            [출력 형식]
            - 반드시 JSON 객체 하나만 출력합니다: {"overall": "...", "good": "...", "lacking": "..."}
            - overall: 체험 전체 평가, good: 잘한 점, lacking: 부족한 점과 다음에 해 볼 것. 각각 1~3문장입니다.
            - 세 값을 합쳐 300~400자 정도로 씁니다. 600자를 넘기면 안 됩니다.

            [말투와 금지 사항]
            - "~했어요"체로 씁니다. 읽는 사람을 부르는 호칭(님, 당신, 보호자님 등)을 쓰지 않습니다. 본인과 보호소가 같은 글을 봅니다.
            - 마크다운(굵게, 목록, 제목 기호 등)과 이모지를 쓰지 않습니다. 일반 문장만 씁니다.
            - 입력에 없는 숫자를 만들지 않습니다. 숫자는 입력 값을 그대로 인용합니다.
            - 화면에 이미 있는 합계(총지출, 총 돌봄 횟수)를 늘어놓지 말고, "3일차에 물을 한 번도 주지 않았어요"처럼 일차별 근거를 인용합니다.
            - status가 FAILED(돌봄 부족으로 중단)나 ABANDONED(중도 포기)여도 비난하지 않습니다.
            - 죽음과 관련된 표현은 절대 쓰지 않습니다. FAILED는 "돌봄이 부족해 체험이 중단됐어요"처럼 표현합니다.

            [정답 기준]
            1. 하루 권장 횟수 careGuideline: deviations.belowGuidelineDays에 키별로 권장 횟수에 못 미친 일차가 있습니다.
            2. 위험 구간: 포만감·수분·청결도가 20 미만이거나 안정감이 20 이하이면 건강이 깎입니다. deviations.dangerZoneDays에 스탯별로 위험 구간에 들어간 일차가 있습니다.
            3. 돌발 이벤트의 정답 행동: days[].events의 answerAction이 정답이고, result가 RESOLVED면 해결, EXPIRED면 시간 안에 해결하지 못한 것입니다. minutesToResolve는 해결까지 걸린 분입니다.
            4. 예산: budget.expectedCost(예상 비용)와 budget.referenceCost(필수 기구와 7일치 소모품 최소 예산)를 비교합니다. topCategory는 가장 많이 쓴 분류입니다.

            [입력 읽는 법]
            - result.evaluatedDays까지의 일차만 days에 있습니다. 마지막 일차는 하루를 다 채우지 못했을 수 있으니 그날 횟수가 적은 것을 크게 탓하지 않습니다.
            - lowestStats는 그날 행동 기록에서 본 가장 낮은 스탯입니다. null이면 그날은 돌봄 행동이 없었다는 뜻입니다.
            - 고양이(species CAT)는 산책이 없어 WALK 키가 없습니다.
            - 이름 대응: FEED=밥, WATER=물, CLEAN_LITTER=배변 청소, PLAY=놀이·간식, WALK=산책, BATHE=목욕, MEDICINE=약, VET_VISIT=병원 진료,
              GIVE_WATER=산책 중 물 주기, GIVE_SNACK=산책 중 간식, GIVE_CATNIP=캣닢, CLEAN_UP_POOP=산책 중 배변 치우기,
              satiety=포만감, hydration=수분, cleanliness=청결도, bond=친밀도, calmness=안정감, health=건강, energy=활력,
              DOG=개, CAT=고양이, ACTIVE=활발함, TIMID=소심함, AGGRESSIVE=예민함, FRIENDLY=다정함.
              출력에는 영문 키 대신 한국어 이름을 씁니다.
        """.trimIndent()
    }
}
