package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.CareEventRecord
import com.ssafy.animal.simulation.domain.CareFixtures
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SpeciesType
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class OpenAiReportSummaryGeneratorTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val generator = OpenAiReportSummaryGenerator(LlmConfig(apiKey = "test-key", model = "gpt-4o-mini"), JsonMapper.builder().build(), builder)

    private fun respond(content: String) {
        val body = JsonMapper.builder().build().writeValueAsString(
            mapOf("id" to "chatcmpl-1", "choices" to listOf(mapOf("index" to 0, "message" to mapOf("role" to "assistant", "content" to content), "finish_reason" to "stop"))),
        )
        server.expect(requestTo("https://api.openai.com/v1/chat/completions")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
    }

    @Test
    fun `모델·response_format·키 헤더로 호출하고 세 문단을 빈 줄로 잇는다`() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].role").value("user"))
            // 사용자 메시지는 집계 JSON뿐(회원 번호 등 원본 정보 없음)
            .andExpect(jsonPath("$.messages[1].content", containsString("\"belowGuidelineDays\"")))
            .andExpect(content().string(not(containsString("userSeq"))))
            .andRespond(
                withSuccess(
                    """{"choices":[{"index":0,"message":{"role":"assistant","content":"{\"overall\":\"7일을 마쳤어요.\",\"good\":\" 밥을 잘 챙겼어요. \",\"lacking\":\"3일차에 물이 부족했어요.\"}"},"finish_reason":"stop"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals("7일을 마쳤어요.\n\n밥을 잘 챙겼어요.\n\n3일차에 물이 부족했어요.", generator.generate(input()))
        server.verify()
    }

    @Test
    fun `content가 JSON이 아니거나 항목이 비었으면 예외`() {
        respond("요약입니다")
        assertFailsWith<Exception> { generator.generate(input()) }

        server.reset()
        respond("""{"overall":"좋았어요.","good":"  ","lacking":"물이 부족했어요."}""")
        assertFailsWith<IllegalStateException> { generator.generate(input()) }

        server.reset()
        respond("""{"overall":"좋았어요.","lacking":"물이 부족했어요."}""")
        assertFailsWith<IllegalStateException> { generator.generate(input()) }
    }

    @Test
    fun `600자를 넘기거나 죽음 표현이 있으면 예외`() {
        respond("""{"overall":"${"가".repeat(300)}","good":"${"나".repeat(200)}","lacking":"${"다".repeat(100)}"}""")
        assertFailsWith<IllegalStateException> { generator.generate(input()) }

        server.reset()
        respond("""{"overall":"돌봄이 부족해 초코가 죽었어요.","good":"밥을 줬어요.","lacking":"물을 더 주세요."}""")
        assertFailsWith<IllegalStateException> { generator.generate(input()) }
    }

    @Test
    fun `5xx·401이면 상태 코드만 담은 예외를 던지고 키를 남기지 않는다`() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE))
        val unavailable = assertFailsWith<IllegalStateException> { generator.generate(input()) }
        assertEquals("OpenAI 호출 실패: HTTP 503", unavailable.message)

        server.reset()
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("""{"error":{"message":"Incorrect API key provided: test-key"}}""").contentType(MediaType.APPLICATION_JSON))
        val unauthorized = assertFailsWith<IllegalStateException> { generator.generate(input()) }
        assertFalse("test-key" in unauthorized.message.orEmpty())
        assertEquals(null, unauthorized.cause)
    }

    @Test
    fun `일차별 집계가 없으면 호출하지 않고 예외`() {
        assertFailsWith<IllegalStateException> { generator.generate(input().copy(care = null)) }
        server.verify()
    }

    @Test
    fun `입력 JSON은 평가 일차까지만 담고 이벤트·이탈·예산을 싣는다`() {
        val payload = generator.payload(input())

        @Suppress("UNCHECKED_CAST")
        val days = payload["days"] as List<Map<String, Any?>>
        assertEquals(listOf(1, 2, 3), days.map { it["day"] })
        assertEquals(mapOf("title" to "물그릇을 엎었어요", "answerAction" to "WATER", "result" to "RESOLVED", "minutesToResolve" to 30L), (days[0]["events"] as List<*>).single())
        assertEquals(mapOf("name" to "사료", "amount" to 21_200), (payload["budget"] as Map<*, *>)["topCategory"])
        assertEquals(93_300, (payload["budget"] as Map<*, *>)["referenceCost"])
        assertEquals(listOf(1, 2, 3), ((payload["deviations"] as Map<*, *>)["belowGuidelineDays"] as Map<*, *>)["WALK"])
    }

    @Test
    fun `LlmConfig 문자열에는 키가 나오지 않는다`() {
        assertFalse("test-key" in LlmConfig(apiKey = "test-key").toString())
    }

    companion object {
        private val START = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

        fun input(): ReportSummaryInput {
            val spent = mapOf("FOOD_BOWL" to 10_000, "FOOD" to 21_200)
            return ReportSummaryInput(
                status = SessionStatus.ABANDONED, speciesType = SpeciesType.DOG, animalName = "초코", personality = Personality.FRIENDLY,
                endedDay = 3, sessionDays = 7, expectedCost = 150_000, totalSpent = spent.values.sum(),
                spentByCategory = ReportAggregator.CATEGORY_KEYS.associateWith { spent[it] ?: 0 },
                finalStats = CareFixtures.STATS.toMap(), actionCount = 5, eventCount = 1, eventResolvedCount = 1, walkCount = 0,
                breedName = "말티즈", referenceCost = 93_300,
                care = CareFixtures.care(
                    START, evaluatedDays = 3,
                    logs = listOf(CareFixtures.log(ActionCode.FEED, START.plusHours(1)), CareFixtures.log(ActionCode.WATER, START.plusDays(2))),
                    events = listOf(CareEventRecord("물그릇을 엎었어요", ActionCode.WATER, OccurrenceStatus.RESOLVED, START.plusHours(2), START.plusHours(2).plusMinutes(30))),
                    dailySpent = mapOf("1" to 31_200),
                ),
            )
        }
    }
}
