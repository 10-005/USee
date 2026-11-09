package com.ssafy.animal.simulation.adapter.out.summary

import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.ReportSummaryWriter
import org.springframework.stereotype.Component

/**
 * 규칙 문구 요약 생성기(기본). LLM 생성기를 붙이면 그 빈을 쓰도록 바꾸고,
 * 이 규칙 문구는 LLM 실패 시 대체 문구로 남는다(ReportSummaryService).
 */
@Component
class RuleReportSummaryGenerator : ReportSummaryGenerator {
    override fun generate(input: ReportSummaryInput): String = ReportSummaryWriter.write(input)
}
