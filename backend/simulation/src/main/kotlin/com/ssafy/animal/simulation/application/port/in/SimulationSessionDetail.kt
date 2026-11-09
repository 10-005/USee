package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.SimulationSession

/** 세션 상세(18.1·18.2·18.4 공통 응답의 원본). PENDING 이벤트는 이벤트 작업에서 붙인다 */
data class SimulationSessionDetail(
    val session: SimulationSession,
    val breed: BreedInfo,
    val equipment: Equipment,
    val activeWalkSeq: Long?,
)
