package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction

interface PurchaseUseCase {
    /**
     * 18.9 펫샵 구매(본인 전용). 금액 = 가격 × 수량을 잔액에서 빼고(음수 허용, M5) 지출을 기록한 뒤
     * 인벤토리를 수량 × 묶음 수만큼 늘린다. 기구는 1개만, 이미 있으면 409.
     */
    fun purchase(userSeq: Long, sessionSeq: Long, command: PurchaseCommand): PurchaseResult
}

data class PurchaseCommand(
    val itemSeq: Long,
    val quantity: Int,
)

data class PurchaseResult(
    val transaction: SimulationTransaction,
    val item: SimulationShopItem,
    /** 구매 후 보유 수량(묶음 물품은 남은 사용 횟수) */
    val inventoryQuantity: Int,
)
