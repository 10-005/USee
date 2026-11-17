package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.adapter.out.summary.OpenAiReportSummaryGenerator
import com.ssafy.animal.simulation.adapter.out.summary.RuleReportSummaryGenerator
import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.domain.ReportWiki
import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.random.RandomGenerator

@Configuration(proxyBeanMethods = false)
class SimulationConfig {

    @Bean
    fun statCalculator(properties: SimulationProperties) = StatCalculator(properties.stats, properties.tick)

    /** 요약 생성기는 하나만 띄운다: LLM 키가 있으면 OpenAI(+리포트 위키, Q521), 비어 있으면 규칙 문구(Q517) */
    @Bean
    fun reportSummaryGenerator(properties: SimulationProperties, jsonMapper: JsonMapper): ReportSummaryGenerator {
        val llm = properties.report.llm
        return if (llm.apiKey.isBlank()) RuleReportSummaryGenerator() else OpenAiReportSummaryGenerator(llm, jsonMapper, loadReportWiki())
    }

    /**
     * 실시간 7일 세션(M2)의 기준 시계. 테스트는 고정 시계로 바꾼다.
     * PostgreSQL TIMESTAMPTZ가 마이크로초까지라 시계도 마이크로초 단위로 맞춘다.
     * 나노초 시각을 쓰면 응답(예: 포기 응답 endedDttm)과 저장 후 다시 읽은 값이 어긋난다.
     */
    /** 돌발 이벤트 RANDOM 발생 판정·시나리오 추첨용 난수. 테스트는 정해진 값을 돌려주는 구현으로 바꾼다 */
    @Bean
    // RandomGenerator.getDefault()(L32X64MixRandom)는 jdk.random 모듈에 있어 런타임 JRE 이미지에서 찾지 못하고 기동이 실패한다.
    // java.util.Random은 java.base에 있고 RandomGenerator를 구현한다
    fun eventRandom(): RandomGenerator = java.util.Random()

    @Bean
    fun clock(): Clock = Clock.tick(Clock.system(ZoneId.of("Asia/Seoul")), Duration.of(1, ChronoUnit.MICROS))

    companion object {
        const val REPORT_WIKI_LOCATION = "classpath*:report-wiki/*.md"

        /** 리포트 위키 마크다운을 모두 읽는다. 형식이 틀리거나 파일이 없으면 기동에 실패한다(조용히 맥락 없이 돌지 않게) */
        fun loadReportWiki(): ReportWiki {
            val resources = PathMatchingResourcePatternResolver().getResources(REPORT_WIKI_LOCATION).sortedBy { it.filename }
            check(resources.isNotEmpty()) { "리포트 위키 파일이 없습니다: $REPORT_WIKI_LOCATION" }
            return ReportWiki.of(resources.map { it.getContentAsString(Charsets.UTF_8) })
        }
    }
}
