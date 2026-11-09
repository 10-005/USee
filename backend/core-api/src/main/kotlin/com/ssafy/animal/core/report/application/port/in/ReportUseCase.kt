package com.ssafy.animal.core.report.application.port.`in`

import com.ssafy.animal.core.report.domain.ReportStatus
import io.swagger.v3.oas.annotations.media.Schema

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
    @field:Schema(description = "신고 번호", example = "601")
    val reportSeq: Long,
    @field:Schema(description = "처리 상태. 현재는 항상 PENDING(접수됨)", example = "PENDING")
    val status: ReportStatus,
)
