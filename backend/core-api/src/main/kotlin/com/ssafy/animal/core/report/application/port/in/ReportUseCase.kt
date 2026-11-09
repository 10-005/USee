package com.ssafy.animal.core.report.application.port.`in`

import com.ssafy.animal.core.report.domain.ReportStatus

interface ReportUseCase {
    /** 15.1 신고 접수 */
    fun report(userSeq: Long, command: ReportCommand): ReportReceipt
}

/** 대상·사유는 문자열로 받아 서비스가 명세 순서대로 검사한다(허용 밖 대상 → 사유 누락 → 정의 밖 사유 → 조합) */
data class ReportCommand(
    val targetType: String,
    val targetSeq: Long,
    val reasonCode: String?,
)

data class ReportReceipt(
    val reportSeq: Long,
    val status: ReportStatus,
)
