package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.common.auth.Role

interface SimulationReportQueryUseCase {
    /**
     * 18.18 리포트 조회.
     * - 본인(USER): 종료 세션이면 저장 리포트, 진행 중이면 지금 시각까지 진행시킨 뒤(그 사이 끝나면 저장 리포트) 중간 리포트.
     * - SHELTER: 종료 세션의 저장 리포트를 누구 것이든(Q483). 진행 중 세션은 404.
     * - 그 외(남의 세션·없는 세션): 404 SIMULATION_NOT_FOUND.
     */
    fun getReport(userSeq: Long, role: Role, sessionSeq: Long): SessionReportView
}
