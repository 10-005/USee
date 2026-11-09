package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionQueryUseCase
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class SimulationSessionQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val detailAssembler: SimulationSessionDetailAssembler,
    private val statCalculator: StatCalculator,
    private val clock: Clock,
) : SimulationSessionQueryUseCase {

    // 조회지만 지연 계산 결과를 저장하므로 쓰기 트랜잭션이다. 행동과 겹치지 않도록 세션 행을 잠근다
    @Transactional
    override fun getActive(userSeq: Long): SimulationSessionDetail? {
        val session = sessionRepository.findActiveForUpdate(Math.toIntExact(userSeq)) ?: return null
        refreshStats(session)
        return detailAssembler.assemble(session)
    }

    @Transactional
    override fun getDetail(userSeq: Long, sessionSeq: Long): SimulationSessionDetail {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        refreshStats(session)
        return detailAssembler.assemble(session)
    }

    /** 마지막 계산 시각부터 min(지금, 예정 종료)까지 지난 tick을 반영한다. 반영할 tick이 없으면 쓰지 않는다 */
    private fun refreshStats(session: SimulationSession) {
        if (session.status != SessionStatus.IN_PROGRESS) return
        val until = minOf(OffsetDateTime.now(clock), session.endsDttm)
        val result = statCalculator.advance(session.stats(), session.personality, session.startedDttm, session.statsCalculatedDttm, until)
        if (result.ticksApplied > 0) session.applyStats(result.stats, result.calculatedAt)
    }
}
