package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import jakarta.persistence.LockModeType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface SimulationSessionJpaRepository : JpaRepository<SimulationSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstByUserSeqAndStatus(userSeq: Int, status: SessionStatus): SimulationSession?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SimulationSession s where s.sessionSeq = :sessionSeq")
    fun findByIdForUpdate(@Param("sessionSeq") sessionSeq: Long): SimulationSession?
}

interface SimulationActionLogJpaRepository : JpaRepository<SimulationActionLog, Long>

@Repository
class SimulationPersistenceAdapter(
    private val sessionJpaRepository: SimulationSessionJpaRepository,
    private val actionLogJpaRepository: SimulationActionLogJpaRepository,
) : SimulationSessionRepository, SimulationActionLogRepository {

    override fun findActiveForUpdate(userSeq: Int): SimulationSession? =
        sessionJpaRepository.findFirstByUserSeqAndStatus(userSeq, SessionStatus.IN_PROGRESS)

    override fun findByIdForUpdate(sessionSeq: Long): SimulationSession? = sessionJpaRepository.findByIdForUpdate(sessionSeq)

    // IDENTITY라 save 시점에 INSERT가 나가므로 부분 UNIQUE 위반도 여기서 잡힌다
    override fun save(session: SimulationSession): SimulationSession = try {
        sessionJpaRepository.save(session)
    } catch (e: DataIntegrityViolationException) {
        throw translate(e) ?: e
    }

    override fun save(log: SimulationActionLog): SimulationActionLog = actionLogJpaRepository.save(log)

    companion object {
        /** 부분 UNIQUE 인덱스 이름(V1) → 사전 조회와 같은 오류. PostgreSQL 오류 메시지에 인덱스 이름이 들어 있다 */
        fun translate(e: DataIntegrityViolationException): BusinessException? {
            val message = e.mostSpecificCause.message.orEmpty()
            return when {
                "uq_sim_session_user_active" in message -> BusinessException(SimulationErrorCode.SIMULATION_ACTIVE_ALREADY_EXISTS)
                else -> null
            }
        }
    }
}
