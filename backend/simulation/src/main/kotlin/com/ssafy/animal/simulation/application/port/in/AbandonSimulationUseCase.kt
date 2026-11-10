package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.domain.CareReport
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession

interface AbandonSimulationUseCase {
    /**
     * 18.5 중도 포기(본인 전용). 지금까지 스탯을 반영한 뒤 ABANDONED로 끝내고 리포트를 만든다(summary는 null).
     * 없거나 남의 세션은 404 SIMULATION_NOT_FOUND, 진행 중이 아니면(이번에 예정 종료·건강 0으로 끝난 경우 포함) 409.
     */
    fun abandon(userSeq: Long, sessionSeq: Long): SessionReportView
}

/**
 * 리포트 응답(18.5·18.18)의 원본. 세션 스냅샷·품종·리포트·일차별 돌봄 집계(조회 시 계산, Q517).
 * isFinal=true면 저장된 종료 리포트, false면 진행 중 세션의 중간 리포트(계산만 하고 저장하지 않음)
 */
data class SessionReportView(
    val session: SimulationSession,
    val breed: BreedInfo,
    val report: SimulationReport,
    val care: CareReport,
    val isFinal: Boolean = true,
)
