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
import kotlin.test.assertTrue

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
    fun `모델·temperature·response_format·키 헤더와 근거 후보만 담은 입력으로 호출하고 세 문단을 빈 줄로 잇는다`() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.temperature").value(0.3))
            .andExpect(jsonPath("$.response_format.type").value("json_object"))
            .andExpect(jsonPath("$.messages[0].role").value("system"))
            .andExpect(jsonPath("$.messages[1].role").value("user"))
            // 사용자 메시지는 근거 후보 JSON(일차별 표·이탈 표시·최종 스탯·원본 정보 없음)
            .andExpect(jsonPath("$.messages[1].content", containsString("\"lacking\"")))
            .andExpect(jsonPath("$.messages[1].content", containsString("\"statusText\"")))
            .andExpect(jsonPath("$.messages[1].content", containsString("\"advice\":[\"밥은 하루 3회가 권장\"")))
            .andExpect(jsonPath("$.messages[1].content", not(containsString("\"days\""))))
            .andExpect(jsonPath("$.messages[1].content", not(containsString("\"deviations\""))))
            .andExpect(jsonPath("$.messages[1].content", not(containsString("\"finalStats\""))))
            .andExpect(content().string(not(containsString("userSeq"))))
            .andRespond(
                withSuccess(
                    """{"choices":[{"index":0,"message":{"role":"assistant","content":"{\"overall\":\"3일째에 초코의 돌봄을 그만뒀어요.\",\"good\":\" 1일차에 물그릇 상황을 30분 만에 해결했어요. 예상보다 118,800원 덜 썼어요. \",\"lacking\":\"2일차에 물을 주지 않았어요.\"}"},"finish_reason":"stop"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals("3일째에 초코의 돌봄을 그만뒀어요.\n\n1일차에 물그릇 상황을 30분 만에 해결했어요. 예상보다 118,800원 덜 썼어요.\n\n2일차에 물을 주지 않았어요.", generator.generate(input()))
        server.verify()
    }

    @Test
    fun `동물 이름이 없거나 후보에 없는 일차를 쓰면 예외`() {
        respond("""{"overall":"돌봄을 그만뒀어요.","good":"밥을 챙겼어요.","lacking":"물이 부족했어요."}""")
        assertFailsWith<IllegalStateException> { generator.generate(input()) }

        server.reset()
        respond("""{"overall":"초코의 돌봄을 그만뒀어요.","good":"밥을 챙겼어요.","lacking":"5일차에 물이 부족했어요."}""")
        val invented = assertFailsWith<IllegalStateException> { generator.generate(input()) }
        assertEquals("요약에 근거 없는 일차가 있습니다: 5일차", invented.message)
    }

    @Test
    fun `보낸 문장에 없는 숫자를 쓰면 예외이고 쉼표 금액·일차는 통과한다`() {
        respond("""{"overall":"3일째에 초코의 돌봄을 그만뒀어요.","good":"예상보다 118,800원 덜 썼어요.","lacking":"2일차에 밥을 주지 않았어요. 밥을 하루 5회 챙겨 보세요."}""")
        val invented = assertFailsWith<IllegalStateException> { generator.generate(input()) }
        assertEquals("요약에 근거 없는 숫자가 있습니다: 5", invented.message)

        server.reset()
        respond("""{"overall":"3일째에 초코의 돌봄을 그만뒀어요.","good":"예상보다 118,800원 덜 썼어요.","lacking":"1·2일차에 밥이 부족했어요. 밥을 하루 3회 챙겨 보세요."}""")
        assertEquals(
            "3일째에 초코의 돌봄을 그만뒀어요.\n\n예상보다 118,800원 덜 썼어요.\n\n1·2일차에 밥이 부족했어요. 밥을 하루 3회 챙겨 보세요.",
            generator.generate(input()),
        )
    }

    @Test
    fun `예산 줄을 보냈는데 요약에 그 금액이 없으면 예외이고 지출이 없으면 검사하지 않는다`() {
        respond("""{"overall":"3일째에 초코의 돌봄을 그만뒀어요.","good":"1일차에 물그릇 상황을 30분 만에 해결했어요.","lacking":"1·2일차에 밥이 부족했어요. 밥을 하루 3회 챙겨 보세요."}""")
        val missing = assertFailsWith<IllegalStateException> { generator.generate(input()) }
        assertEquals("요약에 예산 금액이 없습니다: 118,800", missing.message)

        // 지출 0원이면 예산 줄이 없어 같은 응답이 통과한다
        server.reset()
        respond("""{"overall":"3일째에 초코의 돌봄을 그만뒀어요.","good":"1일차에 물그릇 상황을 30분 만에 해결했어요.","lacking":"1·2일차에 밥이 부족했어요. 밥을 하루 3회 챙겨 보세요."}""")
        val noSpend = input().let { it.copy(totalSpent = 0, spentByCategory = it.spentByCategory.mapValues { 0 }) }
        generator.generate(noSpend)
    }

    @Test
    fun `예산 금액은 더 씀·덜 씀이면 차액, 맞춰 씀이면 예상 비용이다`() {
        assertEquals("15,300", OpenAiReportSummaryGenerator.budgetAmount(listOf("3일차: 물 0회", "예상 비용보다 15,300원 더 씀, 가장 많이 쓴 분류는 병원 진료비(30,000원)")))
        assertEquals("50,000", OpenAiReportSummaryGenerator.budgetAmount(listOf("예상 비용 150,000원보다 50,000원 덜 씀")))
        assertEquals("120,000", OpenAiReportSummaryGenerator.budgetAmount(listOf("예상 비용 120,000원에 맞춰 씀")))
        assertEquals(null, OpenAiReportSummaryGenerator.budgetAmount(listOf("밥은 하루 3회가 권장")))
    }

    @Test
    fun `프롬프트에 예산 문장을 반드시 쓰라는 규칙이 있다`() {
        assertTrue("\"예상 비용\"으로 시작하는 줄이 있으면, 그 줄은 고르는 개수와 별도로 반드시 한 문장으로 씁니다" in OpenAiReportSummaryGenerator.SYSTEM_PROMPT)
        assertTrue("예상 비용보다 8,000원을 더 썼고" in OpenAiReportSummaryGenerator.SYSTEM_PROMPT)
    }

    @Test
    fun `일차 번호는 묶음 표기도 읽는다`() {
        assertEquals(setOf(2, 5, 7), OpenAiReportSummaryGenerator.daysIn("물 부족: 2·5일차(권장 3회), 7일차에도"))
        assertEquals(setOf(1, 4), OpenAiReportSummaryGenerator.daysIn("1일차와 4일차에는"))
        assertEquals(emptySet(), OpenAiReportSummaryGenerator.daysIn("7일 중 6일"))
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
    fun `입력 JSON은 동물·결과와 근거 후보만 담는다`() {
        val payload = generator.payload(input())

        assertEquals(setOf("animal", "result", "good", "lacking", "advice"), payload.keys)
        assertEquals(mapOf("name" to "초코", "species" to "개", "breed" to "말티즈", "personality" to "다정함"), payload["animal"])
        assertEquals(mapOf("status" to "ABANDONED", "statusText" to "3일째에 그만둠", "evaluatedDays" to 3), payload["result"])
        assertEquals(listOf("1일차: '물그릇을 엎었어요'를 30분 만에 해결(물 주기)", "예상 비용 150,000원보다 118,800원 덜 씀"), payload["good"])
        // 포기 세션이라 마지막(3일차)은 0회 후보에서 빠진다
        assertEquals("1일차: 물 0회(권장 3회), 배변 청소 0회(권장 2회), 놀이·간식 0회(권장 2회), 산책 0회(권장 1회)", (payload["lacking"] as List<*>).first())
        assertEquals(listOf("밥은 하루 3회가 권장", "물은 하루 3회가 권장"), payload["advice"])
        assertFalse((payload["lacking"] as List<*>).any { "3일차" in it.toString() })
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
