package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.ReportListItem
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.`in`.SimulationReportQueryUseCase
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.UserReader
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SimulationReportQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val reportRepository: ReportRepository,
    private val breedReader: BreedReader,
    private val progress: SessionProgress,
    private val reportCalculator: ReportCalculator,
    private val userReader: UserReader,
) : SimulationReportQueryUseCase {

    /** 본인 진행 중 세션은 지연 계산·종료 처리를 저장하므로 쓰기 트랜잭션이다(18.2와 같은 원칙) */
    @Transactional
    override fun getReport(userSeq: Long, role: Role, sessionSeq: Long): SessionReportView = when (role) {
        Role.SHELTER -> endedReport(sessionRepository.findById(sessionSeq)?.takeUnless { it.isInProgress })
        Role.USER -> ownReport(userSeq, sessionSeq)
    }

    @Transactional(readOnly = true)
    override fun getMyReports(userSeq: Long, pageable: Pageable): Page<ReportListItem> {
        val page = reportRepository.findPage(Math.toIntExact(userSeq), pageable)
        val breeds = breedReader.findBreeds(page.content.map { it.session.breedSeq })
        return page.map { ReportListItem(it.session, breedFrom(breeds, it.session), it.report) }
    }

    @Transactional(readOnly = true)
    override fun getReportsForShelter(userSeq: Long?, pageable: Pageable): Page<ReportListItem> {
        // 회원 번호는 INT. 범위 밖 값은 해당 회원이 없으므로 빈 목록
        if (userSeq != null && userSeq !in 1..Int.MAX_VALUE.toLong()) return Page.empty(pageable)
        val page = reportRepository.findPage(userSeq?.toInt(), pageable)
        // 품종·회원은 페이지 단위로 한 번에 읽는다
        val breeds = breedReader.findBreeds(page.content.map { it.session.breedSeq })
        val users = userReader.findUsers(page.content.map { it.session.userSeq })
        return page.map { ReportListItem(it.session, breedFrom(breeds, it.session), it.report, users[it.session.userSeq]) }
    }

    private fun breedFrom(breeds: Map<Int, BreedInfo>, session: SimulationSession) =
        checkNotNull(breeds[session.breedSeq]) { "세션의 품종 기준정보가 없습니다: breedSeq=${session.breedSeq}" }

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
