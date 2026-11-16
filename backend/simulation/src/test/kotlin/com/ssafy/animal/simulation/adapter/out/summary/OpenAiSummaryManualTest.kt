package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.config.SimulationConfig
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.CareEventRecord
import com.ssafy.animal.simulation.domain.CareFixtures
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SpeciesType
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test

/**
 * 실제 OpenAI를 부르는 수동 테스트(프롬프트 다듬기용). 빌드에서는 돌지 않는다.
 * 실행: OPENAI_API_KEY=... LLM_MANUAL_TEST=true ./gradlew :simulation:test --tests '*OpenAiSummaryManualTest*' --rerun -i
 * 모델을 바꾸려면 LLM_MODEL=gpt-4o 처럼 준다. 키는 출력하지 않는다.
 */
@EnabledIfEnvironmentVariable(named = "LLM_MANUAL_TEST", matches = "true")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiSummaryManualTest {

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `7일치 픽스처로 요약을 만들어 출력한다`() {
        val mapper = JsonMapper.builder().build()
        val config = LlmConfig(apiKey = System.getenv("OPENAI_API_KEY"), model = System.getenv("LLM_MODEL") ?: "gpt-6-luna")
        val generator = OpenAiReportSummaryGenerator(config, mapper, SimulationConfig.loadReportWiki())
        val input = sevenDayInput()

        println("=== 입력 JSON ===")
        println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(generator.payload(input)))
        val summary = generator.generate(input)
        println("=== 요약 (${summary.length}자) ===")
        println(summary)
        println("=== 규칙 문구 ===")
        println(com.ssafy.animal.simulation.domain.ReportSummaryWriter.write(input))
    }

    /** 개 7일 완료: 3일차 물 없음·놀이 부족, 4일차 산책 없음, 6일차 수분 위험 구간, 이벤트 3건 중 1건 만료 */
    private fun sevenDayInput(): ReportSummaryInput {
        // 일차별 (밥, 물, 배변 청소, 놀이, 산책)
        val plan = listOf(
            listOf(3, 3, 2, 2, 1), listOf(4, 2, 2, 3, 1), listOf(2, 0, 1, 0, 1), listOf(3, 3, 2, 0, 0),
            listOf(3, 1, 2, 2, 1), listOf(3, 3, 1, 0, 1), listOf(3, 3, 2, 2, 1),
        )
        val codes = listOf(ActionCode.FEED, ActionCode.WATER, ActionCode.CLEAN_LITTER, ActionCode.PLAY, ActionCode.WALK_START)
        val logs = mutableListOf<SimulationActionLog>()
        plan.forEachIndexed { index, counts ->
            val dayStart = start.plusDays(index.toLong())
            counts.forEachIndexed { k, n ->
                repeat(n) { i ->
                    val after = if (index == 5 && k == 1 && i == 0) CareFixtures.STATS.copy(hydration = 62) else CareFixtures.STATS
                    val delta = if (index == 5 && k == 1 && i == 0) mapOf("hydration" to 50) else null // 직전 수분 12
                    logs += CareFixtures.log(codes[k], dayStart.plusHours(2L + i * 5 + k), after, delta)
                }
            }
        }
        val events = listOf(
            CareEventRecord(1L, "물그릇을 엎었어요", ActionCode.WATER, OccurrenceStatus.RESOLVED, start.plusHours(5), start.plusHours(5).plusMinutes(40)),
            CareEventRecord(5L, "쿠션을 물어뜯어요", ActionCode.PLAY, OccurrenceStatus.EXPIRED, start.plusDays(2).plusHours(4), null),
            CareEventRecord(8L, "축 처져 있어요", ActionCode.VET_VISIT, OccurrenceStatus.RESOLVED, start.plusDays(5).plusHours(3), start.plusDays(5).plusHours(4)),
        )
        val spent = mapOf("FOOD_BOWL" to 10_000, "WATER_BOWL" to 9_000, "TOY" to 18_000, "LEASH" to 15_000, "FOOD" to 21_200, "SNACK" to 15_000, "EVENT" to 30_000)
        val dailySpent = mapOf("1" to 73_200, "2" to 0, "3" to 15_000, "4" to 0, "5" to 0, "6" to 30_000, "7" to 0)
        return ReportSummaryInput(
            status = SessionStatus.COMPLETED, speciesType = SpeciesType.DOG, animalName = "초코", personality = Personality.FRIENDLY,
            endedDay = 7, sessionDays = 7, expectedCost = 150_000, totalSpent = spent.values.sum(),
            spentByCategory = ReportAggregator.CATEGORY_KEYS.associateWith { spent[it] ?: 0 },
            finalStats = mapOf("satiety" to 55, "hydration" to 60, "cleanliness" to 70, "bond" to 62, "calmness" to 72, "health" to 84, "energy" to 58),
            actionCount = logs.size, eventCount = 3, eventResolvedCount = 2, walkCount = 6,
            breedName = "말티즈", referenceCost = 93_300,
            care = CareFixtures.care(start, evaluatedDays = 7, logs = logs, events = events, dailySpent = dailySpent),
        )
    }
}
