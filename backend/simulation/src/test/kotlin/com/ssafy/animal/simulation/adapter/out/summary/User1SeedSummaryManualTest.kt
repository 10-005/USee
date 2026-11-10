package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.domain.ReportSummaryWriter
import com.ssafy.animal.simulation.seed.User1Seed
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test

/**
 * 회원 1번 시드(S15P31D106-254)로 실제 OpenAI 요약을 미리 뽑아 보는 수동 테스트. 빌드에서는 돌지 않는다.
 * 시드 SQL과 같은 생성기(User1Seed)의 메모리 행으로 요약 복구 스케줄러와 같은 입력을 만든다.
 * 실행: OPENAI_API_KEY=... LLM_MANUAL_TEST=true ./gradlew :simulation:test --tests '*User1SeedSummaryManualTest*' --rerun -i
 * 모델을 바꾸려면 LLM_MODEL=gpt-4o 처럼 준다. 키는 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "LLM_MANUAL_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class User1SeedSummaryManualTest {

    @Test
    fun `회원 1번 시드 데이터로 요약을 만들어 출력한다`() {
        val mapper = JsonMapper.builder().build()
        val config = LlmConfig(apiKey = System.getenv("OPENAI_API_KEY"), model = System.getenv("LLM_MODEL") ?: "gpt-4o-mini")
        val generator = OpenAiReportSummaryGenerator(config, mapper)
        val input = User1Seed.summaryInput(User1Seed.play())

        println("=== 입력 JSON ===")
        println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(generator.payload(input)))
        val summary = generator.generate(input)
        println("=== 요약 (${summary.length}자) ===")
        println(summary)
        println("=== 규칙 문구 ===")
        println(ReportSummaryWriter.write(input))
    }
}
