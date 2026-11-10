package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.AbandonSimulationUseCase
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AbandonSimulationService(
    private val sessionRepository: SimulationSessionRepository,
    private val breedReader: BreedReader,
    private val progress: SessionProgress,
    private val reportCalculator: ReportCalculator,
) : AbandonSimulationUseCase {

    /**
     * 세션 잠금 → 진행(이미 예정 종료·건강 0이면 그 상태로 끝내고 409) → ABANDONED 종료 → 응답(summary는 null, Q444).
     * 진행 중이 아니어서 나는 409도 그 사이 끝낸 종료는 저장해야 하므로 도메인 오류는 롤백하지 않는다.
     */
    @Transactional(noRollbackFor = [BusinessException::class])
    override fun abandon(userSeq: Long, sessionSeq: Long): SessionReportView {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        progress.catchUp(session)
        if (!session.isInProgress) throw BusinessException(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS)

        val report = progress.abandon(session)
        val breed = checkNotNull(breedReader.findBreed(session.breedSeq)) { "세션의 품종 기준정보가 없습니다: breedSeq=${session.breedSeq}" }
        return SessionReportView(session, breed, report, reportCalculator.care(session, report))
    }
}
