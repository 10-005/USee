package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.ReportSummaryWriter

/**
 * 규칙 문구 요약 생성기. LLM 키가 없을 때의 생성기이고(SimulationConfig에서 고른다),
 * 규칙 문구는 LLM 실패 시 대체 문구로도 쓰인다(ReportSummaryService).
 */
class RuleReportSummaryGenerator : ReportSummaryGenerator {
    override fun generate(input: ReportSummaryInput): String = ReportSummaryWriter.write(input)
}
