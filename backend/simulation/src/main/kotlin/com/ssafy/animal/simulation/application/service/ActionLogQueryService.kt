package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.ActionLogQueryUseCase
import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActionLogQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val actionLogRepository: SimulationActionLogRepository,
) : ActionLogQueryUseCase {

    @Transactional(readOnly = true)
    override fun getActionLogs(userSeq: Long, sessionSeq: Long, type: ActionType?, pageable: Pageable): Page<ActionLogEntry> {
        sessionRepository.findById(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        return actionLogRepository.findPage(sessionSeq, type, pageable)
    }
}
