package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionSummary
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SimulationSessionQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val breedReader: BreedReader,
    private val detailReader: SessionDetailReader,
    private val detailAssembler: SimulationSessionDetailAssembler,
    private val statRefresher: SessionStatRefresher,
) : SimulationSessionQueryUseCase {

    // 조회지만 지연 계산 결과를 저장하므로 쓰기 트랜잭션이다. 행동과 겹치지 않도록 세션 행을 잠근다
    @Transactional
    override fun getActive(userSeq: Long): SimulationSessionDetail? {
        val session = sessionRepository.findActiveForUpdate(Math.toIntExact(userSeq)) ?: return null
        statRefresher.refresh(session)
        return detailAssembler.assemble(session)
    }

    @Transactional
    override fun getDetail(userSeq: Long, sessionSeq: Long): SimulationSessionDetail {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        statRefresher.refresh(session)
        return detailAssembler.assemble(session)
    }

    @Transactional(readOnly = true)
    override fun getMySessions(userSeq: Long, status: SessionStatus?, pageable: Pageable): Page<SimulationSessionSummary> {
        val page = sessionRepository.findPageByUser(Math.toIntExact(userSeq), status, pageable)
        // 품종·리포트 유무는 페이지 단위로 한 번에 읽는다
        val breeds = breedReader.findBreeds(page.content.map { it.breedSeq })
        val reported = detailReader.findSessionSeqsWithReport(page.content.mapNotNull { it.sessionSeq })
        return page.map { session ->
            SimulationSessionSummary(
                session = session,
                breed = checkNotNull(breeds[session.breedSeq]) { "세션의 품종 기준정보가 없습니다: breedSeq=${session.breedSeq}" },
                hasReport = session.sessionSeq in reported,
            )
        }
    }
}
