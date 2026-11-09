package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.application.port.`in`.ActionLogQueryUseCase
import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class ActionLogController(
    private val actionLogQueryUseCase: ActionLogQueryUseCase,
) {

    /** 18.7 행동 이력. 본인만 보며 그 외(보호소 포함)는 404로 감추므로 역할 제한을 두지 않는다(Q483). 정렬 regDttm만 */
    @GetMapping("/action-logs")
    fun getActionLogs(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable sessionSeq: Long,
        @RequestParam(required = false) actionType: ActionType?,
        @PageableDefault(size = 50) pageable: Pageable,
    ) = PageResponse.of(actionLogQueryUseCase.getActionLogs(user.userSeq, sessionSeq, actionType, pageable).map(ActionLogResponse::from))
}

data class ActionLogResponse(
    val actionLogSeq: Long,
    val actionType: ActionType,
    val actionCode: ActionCode,
    /** 쓴 물품. 물품을 쓰지 않은 행동이면 null */
    val item: LoggedItem?,
    val walkSeq: Long?,
    /** CARE·WALK 행은 이 행동이 해결한 이벤트, EVENT 행(EVENT_EXPIRE)은 만료된 이벤트(Q486) */
    val occurrenceSeq: Long?,
    /** 실제로 바뀐 스탯(SYSTEM 로그는 null) */
    val statDelta: Map<String, Int>?,
    /** 행동 직후 스탯 7종 */
    val statsAfter: Map<String, Int>,
    val regDttm: OffsetDateTime?,
) {
    data class LoggedItem(
        val itemSeq: Long,
        val itemName: String,
    )

    companion object {
        fun from(entry: ActionLogEntry) = with(entry.log) {
            ActionLogResponse(
                actionLogSeq = checkNotNull(actionLogSeq),
                actionType = actionType,
                actionCode = actionCode,
                item = entry.item?.let { LoggedItem(it.itemSeq, it.itemName) },
                walkSeq = walkSeq,
                occurrenceSeq = occurrenceSeq,
                statDelta = statDeltaJsonb,
                statsAfter = statsAfterJsonb,
                regDttm = regDttm,
            )
        }
    }
}
