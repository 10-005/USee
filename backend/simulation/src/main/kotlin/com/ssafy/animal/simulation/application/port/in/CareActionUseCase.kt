package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.Stat

interface CareActionUseCase {
    /**
     * 18.6 돌봄 행동(본인 전용). 세션을 잠그고 스탯을 지연 계산한 뒤 필요 기구·소모품을 확인해
     * 소모품 1개를 쓰고 효과를 반영한다. 돈은 쓰지 않는다(VET_VISIT 제외, 254).
     */
    fun act(userSeq: Long, sessionSeq: Long, command: CareActionCommand): CareActionResult
}

data class CareActionCommand(
    val actionCode: ActionCode,
    /** 소모품을 쓰는 행동(FEED·CLEAN_LITTER 등)은 필수, 나머지는 무시 */
    val itemSeq: Long?,
)

data class CareActionResult(
    val log: SimulationActionLog,
    val session: SimulationSession,
    val consumedItem: ConsumedItem?,
    /** 실제로 바뀐 스탯(클램프 반영) */
    val statDelta: Map<Stat, Int>,
)

data class ConsumedItem(
    val item: SimulationShopItem,
    val remainingQuantity: Int,
)
