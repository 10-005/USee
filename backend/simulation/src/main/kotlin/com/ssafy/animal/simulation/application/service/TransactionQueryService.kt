package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.TransactionQueryUseCase
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val transactionRepository: TransactionRepository,
) : TransactionQueryUseCase {

    @Transactional(readOnly = true)
    override fun getTransactions(userSeq: Long, sessionSeq: Long, type: TransactionType?, pageable: Pageable): Page<TransactionEntry> {
        sessionRepository.findById(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        return transactionRepository.findPage(sessionSeq, type, pageable)
    }
}
