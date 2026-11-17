package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.domain.ActionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ActionLogQueryUseCase {
    /**
     * 18.7 행동 이력(본인 전용, Q435·Q483). 없거나 남의 세션이면 404 SIMULATION_NOT_FOUND(보호소 포함).
     * type이 null이면 CARE·WALK·EVENT·SYSTEM 전체. 종료된 세션도 조회할 수 있다.
     */
    fun getActionLogs(userSeq: Long, sessionSeq: Long, type: ActionType?, pageable: Pageable): Page<ActionLogEntry>
}
