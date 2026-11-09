package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.CareActionResult
import com.ssafy.animal.simulation.application.port.`in`.CareActionUseCase
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class CareActionController(
    private val careActionUseCase: CareActionUseCase,
) {

    /** 18.6 돌봄 행동. 본인만 하며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @PostMapping("/actions")
    fun act(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable sessionSeq: Long,
        @Valid @RequestBody request: CareActionRequest,
    ) = CareActionResponse.from(careActionUseCase.act(user.userSeq, sessionSeq, request.toCommand()))
}

/** actionCode는 DB action_code 값(FEED 등). 응답·18.7의 actionType(CARE 등 분류)과 다른 필드다 */
data class CareActionRequest(
    @field:NotNull
    val actionCode: ActionCode?,
    val itemSeq: Long?,
) {
    fun toCommand() = CareActionCommand(actionCode!!, itemSeq)
}

data class CareActionResponse(
    val actionLogSeq: Long,
    val actionType: ActionType,
    val actionCode: ActionCode,
    val consumedItem: ConsumedItemResponse?,
    /** 실제로 바뀐 스탯(클램프 반영, 변화 없는 스탯은 빠짐) */
    val statDelta: Map<String, Int>,
    val stats: StatsResponse,
    val balance: Int,
    /** PENDING 이벤트를 이 행동으로 해결했을 때만 채운다(256). 그 전까지 항상 null */
    val resolvedEvent: Any?,
) {
    data class ConsumedItemResponse(
        val itemSeq: Long,
        val itemName: String,
        val remainingQuantity: Int,
    )

    companion object {
        fun from(result: CareActionResult) = CareActionResponse(
            actionLogSeq = checkNotNull(result.log.actionLogSeq),
            actionType = result.log.actionType,
            actionCode = result.log.actionCode,
            consumedItem = result.consumedItem?.let { ConsumedItemResponse(it.item.itemSeq, it.item.itemName, it.remainingQuantity) },
            statDelta = result.statDelta.entries.associate { (stat, value) -> stat.key to value },
            stats = StatsResponse.of(result.session.stats(), result.session.statsCalculatedDttm),
            balance = result.session.balance,
            resolvedEvent = null,
        )
    }
}
