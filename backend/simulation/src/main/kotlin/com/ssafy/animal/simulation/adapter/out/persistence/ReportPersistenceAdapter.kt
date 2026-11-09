package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.domain.SimulationReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface SimulationReportJpaRepository : JpaRepository<SimulationReport, Long> {
    fun findBySessionSeq(sessionSeq: Long): SimulationReport?
}

@Repository
class ReportPersistenceAdapter(
    private val reportJpaRepository: SimulationReportJpaRepository,
) : ReportRepository {

    override fun save(report: SimulationReport): SimulationReport = reportJpaRepository.save(report)

    override fun findBySessionSeq(sessionSeq: Long): SimulationReport? = reportJpaRepository.findBySessionSeq(sessionSeq)
}
