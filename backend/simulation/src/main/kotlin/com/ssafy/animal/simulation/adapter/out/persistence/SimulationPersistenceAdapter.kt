package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.QSimulationSession.Companion.simulationSession
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import jakarta.persistence.LockModeType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
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
    private val queryFactory: JPAQueryFactory,
) : SimulationSessionRepository, SimulationActionLogRepository {

    override fun findActiveForUpdate(userSeq: Int): SimulationSession? =
        sessionJpaRepository.findFirstByUserSeqAndStatus(userSeq, SessionStatus.IN_PROGRESS)

    override fun findByIdForUpdate(sessionSeq: Long): SimulationSession? = sessionJpaRepository.findByIdForUpdate(sessionSeq)

    override fun findPageByUser(userSeq: Int, status: SessionStatus?, pageable: Pageable): Page<SimulationSession> {
        val condition = simulationSession.userSeq.eq(userSeq)
            .let { base -> status?.let { base.and(simulationSession.status.eq(it)) } ?: base }
        val content = queryFactory.selectFrom(simulationSession)
            .where(condition)
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(simulationSession.count()).from(simulationSession).where(condition).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    // IDENTITY라 save 시점에 INSERT가 나가므로 부분 UNIQUE 위반도 여기서 잡힌다
    override fun save(session: SimulationSession): SimulationSession = try {
        sessionJpaRepository.save(session)
    } catch (e: DataIntegrityViolationException) {
        throw translate(e) ?: e
    }

    override fun save(log: SimulationActionLog): SimulationActionLog = actionLogJpaRepository.save(log)

    companion object {
        /** 18.3 정렬 화이트리스트: startedDttm(기본 desc)·endedDttm, 동률은 PK desc */
        val SORT_POLICY = SortPolicy(
            fields = mapOf("startedDttm" to simulationSession.startedDttm, "endedDttm" to simulationSession.endedDttm),
            default = listOf(simulationSession.startedDttm.desc()),
            tieBreaker = simulationSession.sessionSeq.desc(),
        )

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
