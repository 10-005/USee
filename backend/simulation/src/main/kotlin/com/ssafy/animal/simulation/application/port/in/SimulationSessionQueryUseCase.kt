package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface SimulationSessionQueryUseCase {
    /**
     * 18.2 요청자의 진행 중 세션. 조회 시점까지 스탯을 지연 계산해 저장한 뒤 돌려준다(M2). 없으면 null(404 아님).
     * 건강이 0이거나 예정 종료가 지났으면 그 자리에서 FAILED·COMPLETED로 끝내고, 끝난 상세를 이번 한 번만 돌려준다.
     */
    fun getActive(userSeq: Long): SimulationSessionDetail?

    /**
     * 18.4 세션 상세(본인 전용, Q483). 없거나 남의 세션이면 404 SIMULATION_NOT_FOUND로 존재를 감춘다(SHELTER 포함).
     * 진행 중이면 18.2와 같이 스탯을 지연 계산해 저장하고, 종료 조건이면 그 자리에서 끝낸다.
     */
    fun getDetail(userSeq: Long, sessionSeq: Long): SimulationSessionDetail

    /**
     * 18.22 세션 스탯만 조회(본인 전용, Q504). 권한·지연 계산·종료 판정은 18.4와 같고, 상세 조립(품종·기구·이벤트)은 하지 않는다.
     * 끝난 세션도 409 없이 종료 시점 스탯과 상태를 돌려준다(앱이 상태로 리포트 화면에 간다).
     */
    fun getStats(userSeq: Long, sessionSeq: Long): SimulationSession

    /** 18.3 내 세션 목록(종료 포함). status가 null이면 전체. 목록에는 스탯이 없어 지연 계산을 하지 않는다 */
    fun getMySessions(userSeq: Long, status: SessionStatus?, pageable: Pageable): Page<SimulationSessionSummary>
}

/** 18.3 목록 행. hasReport는 종료 리포트 유무 */
data class SimulationSessionSummary(
    val session: SimulationSession,
    val breed: BreedInfo,
    val hasReport: Boolean,
)
