package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.InventoryEntry
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.SimulationSession

interface InventoryQueryUseCase {
    /**
     * 18.10 보유 물품과 잔액(본인 전용, Q483). 없거나 남의 세션이면 404 SIMULATION_NOT_FOUND(보호소 포함).
     * 수량 0인 소모품도 포함하고, 종료된 세션도 조회할 수 있다.
     */
    fun getInventory(userSeq: Long, sessionSeq: Long): InventoryView
}

data class InventoryView(
    val session: SimulationSession,
    val equipment: Equipment,
    val items: List<InventoryItemView>,
)

data class InventoryItemView(
    val entry: InventoryEntry,
    val imageUrl: String?,
)
