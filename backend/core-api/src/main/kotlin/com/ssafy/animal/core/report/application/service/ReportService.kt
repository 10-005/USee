package com.ssafy.animal.core.report.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.report.application.port.`in`.ReportCommand
import com.ssafy.animal.core.report.application.port.`in`.ReportReceipt
import com.ssafy.animal.core.report.application.port.`in`.ReportUseCase
import com.ssafy.animal.core.report.application.port.out.ReportRepository
import com.ssafy.animal.core.report.domain.Report
import com.ssafy.animal.core.report.domain.ReportErrorCode
import com.ssafy.animal.core.report.domain.ReportReason
import com.ssafy.animal.core.report.domain.ReportTargetType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReportService(
    private val reportRepository: ReportRepository,
) : ReportUseCase {

    @Transactional
    override fun report(userSeq: Long, command: ReportCommand): ReportReceipt {
        // 보호동물 게시글·보호동물 댓글 등 5종 밖은 400 REPORT_TARGET_NOT_ALLOWED(Q449·Q477)
        val targetType = ReportTargetType.entries.firstOrNull { it.name == command.targetType }
            ?: throw BusinessException(ReportErrorCode.REPORT_TARGET_NOT_ALLOWED)
        val rawReason = command.reasonCode?.takeIf { it.isNotBlank() }
            ?: throw BusinessException(ReportErrorCode.REPORT_REASON_REQUIRED)
        val reason = ReportReason.entries.firstOrNull { it.name == rawReason } ?: throw BusinessException(
            CommonErrorCode.COMMON_INVALID_INPUT,
            errors = listOf(ErrorDetail("reasonCode", "정의되지 않은 사유입니다: $rawReason")),
        )
        if (reason !in targetType.reasons) throw BusinessException(ReportErrorCode.REPORT_REASON_NOT_ALLOWED)

        if (targetType == ReportTargetType.USER) {
            val role = reportRepository.findActiveUserRole(command.targetSeq)
                ?: throw BusinessException(ReportErrorCode.REPORT_TARGET_NOT_FOUND)
            // 일반회원만 신고 가능. SHELTER 계정은 400(Q449)
            if (role != USER_ROLE) throw BusinessException(ReportErrorCode.REPORT_TARGET_NOT_ALLOWED)
        } else if (!reportRepository.existsTarget(targetType, command.targetSeq)) {
            throw BusinessException(ReportErrorCode.REPORT_TARGET_NOT_FOUND)
        }

        val reporter = Math.toIntExact(userSeq)
        if (reportRepository.exists(reporter, targetType, command.targetSeq)) {
            throw BusinessException(ReportErrorCode.REPORT_ALREADY_EXISTS)
        }
        val report = reportRepository.saveAndFlush(Report(reporter, targetType, command.targetSeq, reason))
        return ReportReceipt(report.reportSeq!!, report.status)
    }

    companion object {
        private const val USER_ROLE = "USER"
    }
}
