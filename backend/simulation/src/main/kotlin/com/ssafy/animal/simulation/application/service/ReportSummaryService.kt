package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.`in`.ReportSummaryUseCase
import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.ReportSummaryWriter
import com.ssafy.animal.simulation.domain.SimulationReportSummarized
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime

/**
 * 리포트 요약(Q444·Q487). 생성기를 부르는 동안 DB 트랜잭션을 잡지 않는다(LLM은 수십 초 걸릴 수 있다).
 * 읽기 → 생성(실패하면 1회 재시도, 그래도 실패하면 규칙 문구) → UPDATE ... WHERE summary IS NULL →
 * 이번에 채웠을 때만 SimulationReportSummarized 발행(SESSION_END 푸시는 260이 받는다).
 */
@Service
class ReportSummaryService(
    private val reportRepository: ReportRepository,
    private val sessionRepository: SimulationSessionRepository,
    private val generator: ReportSummaryGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val properties: SimulationProperties,
    private val clock: Clock,
) : ReportSummaryUseCase {

    override fun summarize(sessionSeq: Long): Boolean {
        val report = reportRepository.findBySessionSeq(sessionSeq) ?: return false
        if (report.summary != null) return false
        val session = checkNotNull(sessionRepository.findById(sessionSeq)) { "리포트의 세션이 없습니다: sessionSeq=$sessionSeq" }

        val input = ReportSummaryInput.of(session, report, properties.sessionDuration.toDays().toInt())
        if (!reportRepository.updateSummaryIfNull(sessionSeq, generate(input))) return false
        eventPublisher.publishEvent(SimulationReportSummarized(sessionSeq, session.userSeq, session.status))
        return true
    }

    override fun summaryMissingSessionSeqs(): List<Long> =
        reportRepository.findSessionSeqsWithoutSummary(OffsetDateTime.now(clock).minus(properties.report.summaryRecoveryDelay))

    private fun generate(input: ReportSummaryInput): String {
        repeat(ATTEMPTS) { attempt ->
            runCatching { generator.generate(input).trim() }
                .onSuccess { if (it.isNotEmpty()) return it }
                .onFailure { log.warn("[simulation-summary] 요약 생성 실패 {}/{}", attempt + 1, ATTEMPTS, it) }
        }
        // 생성기가 모두 실패하거나 빈 문구면 규칙 문구로 저장한다. 그래서 요약·푸시는 반드시 한 번 나간다
        return ReportSummaryWriter.write(input)
    }

    companion object {
        /** 처음 1회 + 재시도 1회(Q444) */
        private const val ATTEMPTS = 2
        private val log = LoggerFactory.getLogger(ReportSummaryService::class.java)
    }
}
