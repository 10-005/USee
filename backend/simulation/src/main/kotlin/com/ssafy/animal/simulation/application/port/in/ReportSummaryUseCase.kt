package com.ssafy.animal.simulation.application.port.`in`

/** 종료 리포트 요약 생성(Q444·Q487). 세션 종료 커밋 후 비동기로, 놓친 것은 스케줄러가 다시 부른다 */
interface ReportSummaryUseCase {
    /**
     * summary가 비어 있는 리포트에 요약을 만들어 저장한다. 이번에 채웠으면 true이고 SimulationReportSummarized를 발행한다.
     * 리포트가 없거나 이미 요약이 있으면 아무것도 하지 않고 false(중복 호출·재전달 무해).
     */
    fun summarize(sessionSeq: Long): Boolean

    /** 요약이 아직 없고 종료 처리 후 복구 대기 시간이 지난 세션 번호(고아 복구 대상) */
    fun summaryMissingSessionSeqs(): List<Long>
}
