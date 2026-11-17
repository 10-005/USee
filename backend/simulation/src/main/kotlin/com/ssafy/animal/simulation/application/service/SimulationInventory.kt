package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationShopItem
import org.springframework.stereotype.Component

/**
 * 보유 수량 규칙(S15P31D106-244). 구매(242)가 receive를, 돌봄·산책 행동(238·246·252)이 consumeOne을 부른다.
 * 부르는 쪽 트랜잭션이 세션 행을 잠근 상태에서 쓴다고 보고 여기서는 따로 잠그지 않는다.
 */
@Component
class SimulationInventory(
    private val inventoryRepository: InventoryRepository,
    private val properties: SimulationProperties,
) {

    /**
     * 사기 전에 보유 규칙만 본다(쓰지 않음). 장바구니 구매가 모든 물품을 검증한 뒤에 쓰도록 한다.
     * 이미 가진 기구면 409 SIMULATION_EQUIPMENT_ALREADY_OWNED.
     */
    fun checkReceivable(sessionSeq: Long, item: SimulationShopItem) {
        if (!item.category.isEquipment) return
        val existing = inventoryRepository.find(sessionSeq, item.itemSeq)
        if (existing != null && !existing.isEmpty) throw BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED)
    }

    /**
     * 구매한 물품을 보유에 더한다. 기구는 1개만 사며 이미 있으면 409 SIMULATION_EQUIPMENT_ALREADY_OWNED.
     * 소모품은 구매 수량 × 묶음 수(simulation.shop.uses-per-purchase)만큼 늘린다.
     */
    fun receive(sessionSeq: Long, item: SimulationShopItem, purchaseQuantity: Int): SimulationInventoryItem {
        require(purchaseQuantity >= 1) { "구매 수량은 1 이상이어야 합니다: $purchaseQuantity" }
        val existing = inventoryRepository.find(sessionSeq, item.itemSeq)
        val entry = existing ?: SimulationInventoryItem(sessionSeq, item.itemSeq)
        if (item.category.isEquipment) {
            require(purchaseQuantity == 1) { "기구는 1개만 살 수 있습니다: $purchaseQuantity" }
            if (existing != null && !existing.isEmpty) throw BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED)
            entry.add(1)
        } else {
            entry.add(Math.multiplyExact(purchaseQuantity, properties.shop.usesPerPurchase(item.itemSeq)))
        }
        return inventoryRepository.save(entry)
    }

    /** 소모품 1개를 쓴다. 보유하지 않았거나 0개면 409 SIMULATION_ITEM_NOT_IN_INVENTORY */
    fun consumeOne(sessionSeq: Long, item: SimulationShopItem): SimulationInventoryItem {
        require(item.isConsumable) { "기구는 소모하지 않습니다: ${item.category}" }
        val entry = inventoryRepository.find(sessionSeq, item.itemSeq)?.takeUnless { it.isEmpty }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY)
        entry.consumeOne()
        return inventoryRepository.save(entry)
    }
}
