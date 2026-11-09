package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.simulation.application.port.out.ReportListEntry
import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.domain.QSimulationReport.Companion.simulationReport
import com.ssafy.animal.simulation.domain.QSimulationSession.Companion.simulationSession
import com.ssafy.animal.simulation.domain.SimulationReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

interface SimulationReportJpaRepository : JpaRepository<SimulationReport, Long> {
    fun findBySessionSeq(sessionSeq: Long): SimulationReport?

    @Modifying
    @Query("update SimulationReport r set r.summary = :summary where r.sessionSeq = :sessionSeq and r.summary is null")
    fun updateSummaryIfNull(@Param("sessionSeq") sessionSeq: Long, @Param("summary") summary: String): Int
}

@Repository
class ReportPersistenceAdapter(
    private val reportJpaRepository: SimulationReportJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ReportRepository {

    override fun save(report: SimulationReport): SimulationReport = reportJpaRepository.save(report)

    override fun findBySessionSeq(sessionSeq: Long): SimulationReport? = reportJpaRepository.findBySessionSeq(sessionSeq)

    override fun findPage(userSeq: Int?, pageable: Pageable): Page<ReportListEntry> {
        // where(null)은 조건 없음으로 처리된다
        val condition = userSeq?.let { simulationSession.userSeq.eq(it) }
        val content = queryFactory.select(simulationReport, simulationSession)
            .from(simulationReport)
            .join(simulationSession).on(simulationSession.sessionSeq.eq(simulationReport.sessionSeq))
            .where(condition)
            .orderBy(*LIST_SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
            .map { ReportListEntry(session = it.get(simulationSession)!!, report = it.get(simulationReport)!!) }
        val total = queryFactory.select(simulationReport.count())
            .from(simulationReport)
            .join(simulationSession).on(simulationSession.sessionSeq.eq(simulationReport.sessionSeq))
            .where(condition)
            .fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    // 요약 생성기 호출과 분리된 짧은 쓰기 트랜잭션. 동시에 두 번 불려도 한 번만 채워진다
    @Transactional
    override fun updateSummaryIfNull(sessionSeq: Long, summary: String): Boolean =
        reportJpaRepository.updateSummaryIfNull(sessionSeq, summary) == 1

    override fun findSessionSeqsWithoutSummary(createdBefore: OffsetDateTime): List<Long> =
        queryFactory.select(simulationReport.sessionSeq)
            .from(simulationReport)
            .where(simulationReport.summary.isNull, simulationReport.regDttm.loe(createdBefore))
            .orderBy(simulationReport.reportSeq.asc())
            .fetch()
            .filterNotNull()

    companion object {
        /** 18.19·18.20 정렬 화이트리스트는 endedDttm뿐(기본 desc, 동률 세션 번호 desc) */
        val LIST_SORT_POLICY = SortPolicy(
            fields = mapOf("endedDttm" to simulationSession.endedDttm),
            default = listOf(simulationSession.endedDttm.desc()),
            tieBreaker = simulationSession.sessionSeq.desc(),
        )
    }
}
