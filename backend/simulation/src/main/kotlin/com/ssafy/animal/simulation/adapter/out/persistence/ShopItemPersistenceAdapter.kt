package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.domain.QSimulationShopItem.Companion.simulationShopItem
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

interface SimulationShopItemJpaRepository : JpaRepository<SimulationShopItem, Long>

@Repository
class ShopItemPersistenceAdapter(
    private val shopItemJpaRepository: SimulationShopItemJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ShopItemRepository {

    override fun findActive(speciesType: SpeciesType?): List<SimulationShopItem> {
        var condition = simulationShopItem.isActive.isTrue
        if (speciesType != null) {
            condition = condition.and(simulationShopItem.speciesScope.`in`(SpeciesScope.ALL, SpeciesScope.valueOf(speciesType.name)))
        }
        return queryFactory.selectFrom(simulationShopItem)
            .where(condition)
            .orderBy(simulationShopItem.displayOrder.asc(), simulationShopItem.itemSeq.asc())
            .fetch()
    }

    override fun findByItemSeq(itemSeq: Long): SimulationShopItem? = shopItemJpaRepository.findByIdOrNull(itemSeq)
}
