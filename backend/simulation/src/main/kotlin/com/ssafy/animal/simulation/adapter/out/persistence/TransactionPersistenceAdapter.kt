package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.QSimulationShopItem.Companion.simulationShopItem
import com.ssafy.animal.simulation.domain.QSimulationTransaction.Companion.simulationTransaction
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface SimulationTransactionJpaRepository : JpaRepository<SimulationTransaction, Long>

@Repository
class TransactionPersistenceAdapter(
    private val transactionJpaRepository: SimulationTransactionJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : TransactionRepository {

    override fun save(transaction: SimulationTransaction): SimulationTransaction = transactionJpaRepository.save(transaction)

    override fun findPage(sessionSeq: Long, type: TransactionType?, pageable: Pageable): Page<TransactionEntry> {
        val condition = simulationTransaction.sessionSeq.eq(sessionSeq)
            .let { base -> type?.let { base.and(simulationTransaction.transactionType.eq(it)) } ?: base }
        // EVENT 지출은 물품이 없으므로 LEFT JOIN
        val content = queryFactory.select(simulationTransaction, simulationShopItem)
            .from(simulationTransaction)
            .leftJoin(simulationShopItem).on(simulationShopItem.itemSeq.eq(simulationTransaction.itemSeq))
            .where(condition)
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
            .map { TransactionEntry(it.get(simulationTransaction)!!, it.get(simulationShopItem)) }
        val total = queryFactory.select(simulationTransaction.count()).from(simulationTransaction).where(condition).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    companion object {
        /** 18.11 정렬 화이트리스트는 regDttm뿐(기본 desc, 동률 PK desc) */
        val SORT_POLICY = SortPolicy.regDttmOnly(simulationTransaction.regDttm, simulationTransaction.transactionSeq)
    }
}
