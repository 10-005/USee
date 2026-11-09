package com.ssafy.animal.simulation.application.port.`in`

interface SimulationSessionQueryUseCase {
    /**
     * 18.2 요청자의 진행 중 세션. 조회 시점까지 스탯을 지연 계산해 저장한 뒤 돌려준다(M2). 없으면 null(404 아님).
     * 예정 종료가 지났거나 건강이 0이어도 종료 처리는 세션 상태 관리 작업(232)에서 하며, 그 전까지 스탯은 그 시점에서 멈춘다.
     */
    fun getActive(userSeq: Long): SimulationSessionDetail?
}
