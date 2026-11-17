package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface EventQueryUseCase {
    /**
     * 18.12 세션 돌발 이벤트 목록(본인 전용, 그 외는 404). 조회 전에 세션을 지금 시각까지 진행시켜
     * 시한이 지난 대기 이벤트를 만료한다(Q441·Q486, GET도 쓰기). status가 null이면 전체.
     */
    fun getEvents(userSeq: Long, sessionSeq: Long, status: OccurrenceStatus?, pageable: Pageable): Page<EventView>
}

/** 18.12 SimulationEvent·18.2 pendingEvent의 원본 */
data class EventView(
    val occurrence: EventOccurrence,
    val scenario: EventScenario,
)
