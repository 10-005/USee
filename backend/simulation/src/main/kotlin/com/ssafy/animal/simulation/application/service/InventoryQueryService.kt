package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.InventoryItemView
import com.ssafy.animal.simulation.application.port.`in`.InventoryQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.InventoryView
import com.ssafy.animal.simulation.application.port.out.ImageUrlResolver
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class InventoryQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val inventoryRepository: InventoryRepository,
    private val imageUrlResolver: ImageUrlResolver,
) : InventoryQueryUseCase {

    // 스탯을 다루지 않는 읽기라 세션을 잠그지 않는다
    @Transactional(readOnly = true)
    override fun getInventory(userSeq: Long, sessionSeq: Long): InventoryView {
        val session = sessionRepository.findById(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        val entries = inventoryRepository.findAllWithItems(sessionSeq)
        // 기구 여부는 이미 읽은 목록에서 판정해 따로 조회하지 않는다
        val equipment = Equipment.of(
            entries.filter { it.inventoryItem.quantity > 0 && it.shopItem.category.isEquipment }.mapTo(mutableSetOf()) { it.shopItem.category },
        )
        return InventoryView(
            session = session,
            equipment = equipment,
            items = entries.map { InventoryItemView(it, it.shopItem.imageKey?.let(imageUrlResolver::publicUrl)) },
        )
    }
}
