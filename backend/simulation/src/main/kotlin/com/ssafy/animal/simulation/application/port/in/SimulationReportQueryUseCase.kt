package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface SimulationReportQueryUseCase {
    /**
     * 18.18 리포트 조회.
     * - 본인(USER): 종료 세션이면 저장 리포트, 진행 중이면 지금 시각까지 진행시킨 뒤(그 사이 끝나면 저장 리포트) 중간 리포트.
     * - SHELTER: 종료 세션의 저장 리포트를 누구 것이든(Q483). 진행 중 세션은 404.
     * - 그 외(남의 세션·없는 세션): 404 SIMULATION_NOT_FOUND.
     */
    fun getReport(userSeq: Long, role: Role, sessionSeq: Long): SessionReportView

    /** 18.19 내 종료 리포트 목록(USER). 진행 중 세션은 리포트가 없어 나오지 않는다 */
    fun getMyReports(userSeq: Long, pageable: Pageable): Page<ReportListItem>

    /** 18.20 종료 리포트 전체 목록(SHELTER, Q483). userSeq가 있으면 그 회원 것만. 항목에 회원 정보를 붙인다 */
    fun getReportsForShelter(userSeq: Long?, pageable: Pageable): Page<ReportListItem>
}

/** 리포트 목록 한 줄. user는 18.20에서만 채운다(core에 회원 행이 없으면 null) */
data class ReportListItem(
    val session: SimulationSession,
    val breed: BreedInfo,
    val report: SimulationReport,
    val user: UserInfo? = null,
)
