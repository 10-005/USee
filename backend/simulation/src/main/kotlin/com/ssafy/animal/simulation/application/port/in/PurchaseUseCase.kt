package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction

interface PurchaseUseCase {
    /**
     * 18.9 펫샵 장바구니 구매(본인 전용, Q503). 여러 물품을 한 번에 사며 전부 사거나 하나도 사지 않는다.
     * 물품마다 금액 = 가격 × 수량을 잔액에서 빼고(음수 허용, M5) 지출을 1건씩 기록한 뒤
     * 인벤토리를 수량 × 묶음 수만큼 늘린다. 기구는 1개만, 이미 있으면 409. 같은 물품을 두 줄로 담으면 400.
     */
    fun purchase(userSeq: Long, sessionSeq: Long, command: PurchaseCommand): PurchaseResult
}

data class PurchaseCommand(
    /** 장바구니 순서. 지출 기록·잔액 차감도 이 순서다 */
    val lines: List<PurchaseLine>,
)

data class PurchaseLine(
    val itemSeq: Long,
    val quantity: Int,
)

data class PurchaseResult(
    /** 요청 순서대로 산 결과 */
    val lines: List<PurchasedLine>,
    /** 이번 구매 총액 */
    val totalAmount: Int,
    /** 모두 산 뒤 잔액(음수 가능) */
    val balanceAfter: Int,
)

data class PurchasedLine(
    val transaction: SimulationTransaction,
    val item: SimulationShopItem,
    /** 구매 후 보유 수량(묶음 물품은 남은 사용 횟수) */
    val inventoryQuantity: Int,
)
