package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.SimulationSession

/** 세션 상세(18.1·18.2·18.4 공통 응답의 원본). pendingEvent는 대기 중 돌발 이벤트(없으면 null) */
data class SimulationSessionDetail(
    val session: SimulationSession,
    val breed: BreedInfo,
    val equipment: Equipment,
    val activeWalkSeq: Long?,
    val pendingEvent: EventView? = null,
)
