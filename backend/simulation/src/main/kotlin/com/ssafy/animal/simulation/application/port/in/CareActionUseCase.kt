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
    /** 실제로 바뀐 스탯(클램프 반영). 이벤트를 해결했으면 물품 효과 + 해결 효과 합산 */
    val statDelta: Map<Stat, Int>,
    /** 이 행동이 대기 이벤트의 정답이었을 때만(Q486) */
    val resolvedEvent: ResolvedEvent? = null,
)

data class ConsumedItem(
    val item: SimulationShopItem,
    val remainingQuantity: Int,
)

/** 18.6 행동 응답 resolvedEvent의 원본. eventStatDelta는 해결 효과의 실제 변화(클램프 반영), eventCost는 진료비(병원만) */
data class ResolvedEvent(
    val occurrenceSeq: Long,
    val scenarioTitle: String,
    val resultText: String?,
    val eventStatDelta: Map<Stat, Int>,
    val eventCost: Int,
)
