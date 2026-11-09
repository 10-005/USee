package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.`in`.SimulationReportQueryUseCase
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SimulationReportQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val reportRepository: ReportRepository,
    private val breedReader: BreedReader,
    private val progress: SessionProgress,
    private val reportCalculator: ReportCalculator,
) : SimulationReportQueryUseCase {

    /** 본인 진행 중 세션은 지연 계산·종료 처리를 저장하므로 쓰기 트랜잭션이다(18.2와 같은 원칙) */
    @Transactional
    override fun getReport(userSeq: Long, role: Role, sessionSeq: Long): SessionReportView = when (role) {
        Role.SHELTER -> endedReport(sessionRepository.findById(sessionSeq)?.takeUnless { it.isInProgress })
        Role.USER -> ownReport(userSeq, sessionSeq)
    }

    private fun ownReport(userSeq: Long, sessionSeq: Long): SessionReportView {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw notFound()
        progress.catchUp(session)
        if (!session.isInProgress) return endedReport(session)
        // 중간 리포트: 같은 형식으로 계산만 하고 저장하지 않는다(summary 항상 null)
        return SessionReportView(session, breedOf(session), reportCalculator.calculate(session), isFinal = false)
    }

    private fun endedReport(session: SimulationSession?): SessionReportView {
        if (session == null) throw notFound()
        val report = checkNotNull(reportRepository.findBySessionSeq(checkNotNull(session.sessionSeq))) {
            "종료된 세션에 리포트가 없습니다: sessionSeq=${session.sessionSeq}"
        }
        return SessionReportView(session, breedOf(session), report)
    }

    private fun breedOf(session: SimulationSession) =
        checkNotNull(breedReader.findBreed(session.breedSeq)) { "세션의 품종 기준정보가 없습니다: breedSeq=${session.breedSeq}" }

    private fun notFound() = BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
}
