package com.ssafy.animal.core.report.application.port.out

import com.ssafy.animal.core.report.domain.Report
import com.ssafy.animal.core.report.domain.ReportTargetType

interface ReportRepository {
    fun exists(reporterUserSeq: Int, targetType: ReportTargetType, targetSeq: Long): Boolean

    /** UNIQUE(uq_reports_reporter_target) 위반은 409 REPORT_ALREADY_EXISTS로 바꿔 던진다 */
    fun saveAndFlush(report: Report): Report

    /** 비탈퇴 회원의 role(USER/SHELTER). 없거나 탈퇴면 null */
    fun findActiveUserRole(userSeq: Long): String?

    /** 게시물(MISSING/FOUND)·댓글(MISSING_COMMENT/FOUND_COMMENT)이 비삭제로 있는지 */
    fun existsTarget(targetType: ReportTargetType, targetSeq: Long): Boolean
}
