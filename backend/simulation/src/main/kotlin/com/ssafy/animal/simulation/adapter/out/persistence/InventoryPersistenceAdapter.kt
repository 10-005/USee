package com.ssafy.animal.simulation.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.simulation.application.port.out.InventoryEntry
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.QSimulationInventoryItem.Companion.simulationInventoryItem
import com.ssafy.animal.simulation.domain.QSimulationShopItem.Companion.simulationShopItem
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface SimulationInventoryJpaRepository : JpaRepository<SimulationInventoryItem, Long> {
    fun findBySessionSeqAndItemSeq(sessionSeq: Long, itemSeq: Long): SimulationInventoryItem?
}

@Repository
class InventoryPersistenceAdapter(
    private val inventoryJpaRepository: SimulationInventoryJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : InventoryRepository {

    override fun find(sessionSeq: Long, itemSeq: Long): SimulationInventoryItem? =
        inventoryJpaRepository.findBySessionSeqAndItemSeq(sessionSeq, itemSeq)

    override fun save(inventoryItem: SimulationInventoryItem): SimulationInventoryItem = inventoryJpaRepository.save(inventoryItem)

    override fun findAllWithItems(sessionSeq: Long): List<InventoryEntry> =
        queryFactory.select(simulationInventoryItem, simulationShopItem)
            .from(simulationInventoryItem)
            .join(simulationShopItem).on(simulationShopItem.itemSeq.eq(simulationInventoryItem.itemSeq))
            .where(simulationInventoryItem.sessionSeq.eq(sessionSeq))
            .orderBy(simulationShopItem.displayOrder.asc(), simulationShopItem.itemSeq.asc())
            .fetch()
            .map { InventoryEntry(it.get(simulationInventoryItem)!!, it.get(simulationShopItem)!!) }

    override fun findOwnedByCategory(sessionSeq: Long, category: ItemCategory): List<InventoryEntry> =
        queryFactory.select(simulationInventoryItem, simulationShopItem)
            .from(simulationInventoryItem)
            .join(simulationShopItem).on(simulationShopItem.itemSeq.eq(simulationInventoryItem.itemSeq))
            .where(
                simulationInventoryItem.sessionSeq.eq(sessionSeq),
                simulationInventoryItem.quantity.gt(0),
                simulationShopItem.category.eq(category),
            )
            .orderBy(simulationShopItem.displayOrder.asc(), simulationShopItem.itemSeq.asc())
            .fetch()
            .map { InventoryEntry(it.get(simulationInventoryItem)!!, it.get(simulationShopItem)!!) }

    override fun findEquipment(sessionSeq: Long): Equipment {
        val owned = queryFactory.selectDistinct(simulationShopItem.category)
            .from(simulationInventoryItem)
            .join(simulationShopItem).on(simulationShopItem.itemSeq.eq(simulationInventoryItem.itemSeq))
            .where(
                simulationInventoryItem.sessionSeq.eq(sessionSeq),
                simulationInventoryItem.quantity.gt(0),
                simulationShopItem.isConsumable.isFalse,
            )
            .fetch()
            .toSet()
        return Equipment.of(owned)
    }
}
