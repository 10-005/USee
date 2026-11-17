package com.ssafy.animal.core.report.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.report.application.port.`in`.ReportCommand
import com.ssafy.animal.core.report.application.port.out.ReportRepository
import com.ssafy.animal.core.report.domain.Report
import com.ssafy.animal.core.report.domain.ReportErrorCode
import com.ssafy.animal.core.report.domain.ReportReason
import com.ssafy.animal.core.report.domain.ReportStatus
import com.ssafy.animal.core.report.domain.ReportTargetType
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReportServiceTest {

    private val saved = mutableListOf<Report>()
    private val users = mapOf(5L to "USER", 1L to "SHELTER")
    private val targets = setOf(ReportTargetType.MISSING to 101L, ReportTargetType.FOUND_COMMENT to 44L)

    private val repository = object : ReportRepository {
        override fun exists(reporterUserSeq: Int, targetType: ReportTargetType, targetSeq: Long) =
            saved.any { it.reporterUserSeq == reporterUserSeq && it.targetType == targetType && it.targetSeq == targetSeq }
        override fun saveAndFlush(report: Report) = report.also {
            ReflectionTestUtils.setField(it, "reportSeq", 600L + saved.size)
            saved += it
        }
        override fun findActiveUserRole(userSeq: Long) = users[userSeq]
        override fun existsTarget(targetType: ReportTargetType, targetSeq: Long) = (targetType to targetSeq) in targets
    }

    private val service = ReportService(repository)

    private fun report(type: String, seq: Long, reason: String?) = service.report(42, ReportCommand(type, seq, reason))

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `대상별 허용 사유로 접수하면 PENDING, 같은 대상 재신고는 409`() {
        val receipt = report("MISSING", 101, "FALSE_INFO")

        assertEquals(600L, receipt.reportSeq)
        assertEquals(ReportStatus.PENDING, receipt.status)
        assertEquals(ReportReason.FALSE_INFO, saved.single().reasonCode)
        report("FOUND_COMMENT", 44, "HARASSMENT")
        report("USER", 5, "OBSCENE")
        assertCode(ReportErrorCode.REPORT_ALREADY_EXISTS) { report("MISSING", 101, "OTHER") }
    }

    @Test
    fun `명세 순서대로 검사한다 - 대상, 사유 누락, 정의 밖 사유, 조합, 존재`() {
        assertCode(ReportErrorCode.REPORT_TARGET_NOT_ALLOWED) { report("SHELTER_ANIMAL", 1, "OTHER") }
        assertCode(ReportErrorCode.REPORT_TARGET_NOT_ALLOWED) { report("SHELTER_ANIMAL_COMMENT", 1, null) }
        assertCode(ReportErrorCode.REPORT_REASON_REQUIRED) { report("MISSING", 101, null) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { report("MISSING", 101, "RUDE") }
        assertCode(ReportErrorCode.REPORT_REASON_NOT_ALLOWED) { report("MISSING", 101, "HARASSMENT") }
        assertCode(ReportErrorCode.REPORT_REASON_NOT_ALLOWED) { report("USER", 5, "FALSE_INFO") }
        assertCode(ReportErrorCode.REPORT_TARGET_NOT_FOUND) { report("FOUND", 999, "SPAM_AD") }
        assertCode(ReportErrorCode.REPORT_TARGET_NOT_FOUND) { report("USER", 999, "SPAM_AD") }
    }

    @Test
    fun `SHELTER 계정은 신고할 수 없다`() {
        assertCode(ReportErrorCode.REPORT_TARGET_NOT_ALLOWED) { report("USER", 1, "SPAM_AD") }
    }
}
