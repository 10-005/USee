package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.simulation.application.port.out.WalkRepository
import com.ssafy.animal.simulation.domain.QSimulationWalk.Companion.simulationWalk
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.WalkStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface SimulationWalkJpaRepository : JpaRepository<SimulationWalk, Long>

/**
 * 산책(252). 종전 JDBC 직접 쿼리(세션 종료 시 산책 닫기·산책 수·진행 중 산책 번호)를 대신한다.
 * JPQL 조회는 같은 트랜잭션의 변경을 먼저 flush하므로 같은 요청에서 끝낸 산책도 바로 반영된다.
 */
@Repository
class WalkPersistenceAdapter(
    private val walkJpaRepository: SimulationWalkJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : WalkRepository {

    override fun save(walk: SimulationWalk): SimulationWalk = walkJpaRepository.save(walk)

    override fun findActive(sessionSeq: Long): SimulationWalk? =
        queryFactory.selectFrom(simulationWalk)
            .where(simulationWalk.sessionSeq.eq(sessionSeq), simulationWalk.status.eq(WalkStatus.IN_PROGRESS))
            .fetchFirst()

    override fun findByWalkSeq(walkSeq: Long): SimulationWalk? = walkJpaRepository.findById(walkSeq).orElse(null)

    override fun countWalks(sessionSeq: Long): Int =
        (queryFactory.select(simulationWalk.count()).from(simulationWalk).where(simulationWalk.sessionSeq.eq(sessionSeq)).fetchOne() ?: 0L).toInt()
}
