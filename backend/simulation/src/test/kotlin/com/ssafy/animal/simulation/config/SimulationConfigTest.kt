package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.adapter.out.summary.OpenAiReportSummaryGenerator
import com.ssafy.animal.simulation.adapter.out.summary.RuleReportSummaryGenerator
import com.ssafy.animal.simulation.application.service.FakeSimulationStore
import tools.jackson.databind.json.JsonMapper
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SimulationConfigTest {

    @Test
    fun `기준 시계는 서울 시간대이고 DB와 같은 마이크로초 단위로 잘린다`() {
        val clock = SimulationConfig().clock()

        assertEquals(ZoneId.of("Asia/Seoul"), clock.zone)
        repeat(100) { assertEquals(0, clock.instant().nano % 1_000, "마이크로초 아래 자리가 남음") }
    }

    @Test
    fun `요약 생성기는 LLM 키가 비어 있으면 규칙 문구, 있으면 OpenAI 하나만 만든다`() {
        val base = FakeSimulationStore.DEFAULT_PROPERTIES
        fun withKey(key: String) = base.copy(report = base.report.copy(llm = LlmConfig(apiKey = key)))

        assertIs<RuleReportSummaryGenerator>(SimulationConfig().reportSummaryGenerator(withKey(""), JsonMapper.builder().build()))
        assertIs<RuleReportSummaryGenerator>(SimulationConfig().reportSummaryGenerator(withKey("  "), JsonMapper.builder().build()))
        assertIs<OpenAiReportSummaryGenerator>(SimulationConfig().reportSummaryGenerator(withKey("k"), JsonMapper.builder().build()))
    }
}
