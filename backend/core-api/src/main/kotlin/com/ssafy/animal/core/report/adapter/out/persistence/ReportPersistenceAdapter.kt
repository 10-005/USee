package com.ssafy.animal.core.report.adapter.out.persistence

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.report.application.port.out.ReportRepository
import com.ssafy.animal.core.report.domain.Report
import com.ssafy.animal.core.report.domain.ReportErrorCode
import com.ssafy.animal.core.report.domain.ReportTargetType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ReportJpaRepository : JpaRepository<Report, Long> {
    fun existsByReporterUserSeqAndTargetTypeAndTargetSeq(reporterUserSeq: Int, targetType: ReportTargetType, targetSeq: Long): Boolean

    // 대상 테이블은 각 도메인 몫이라 네이티브로 존재만 확인한다
    @Query(nativeQuery = true, value = "select role from users where user_seq = :userSeq and is_deleted = false")
    fun findActiveUserRole(@Param("userSeq") userSeq: Long): String?

    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from missing_reports r join users u on u.user_seq = r.user_seq
                      where r.missing_report_seq = :seq and r.is_deleted = false and u.is_deleted = false)
        """,
    )
    fun existsMissing(@Param("seq") seq: Long): Boolean

    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from found_reports r join users u on u.user_seq = r.user_seq
                      where r.found_report_seq = :seq and r.is_deleted = false and u.is_deleted = false)
        """,
    )
    fun existsFound(@Param("seq") seq: Long): Boolean

    @Query(
        nativeQuery = true,
        value = "select exists(select 1 from missing_report_comments where missing_report_comment_seq = :seq and is_deleted = false)",
    )
    fun existsMissingComment(@Param("seq") seq: Long): Boolean

    @Query(
        nativeQuery = true,
        value = "select exists(select 1 from found_report_comments where found_report_comment_seq = :seq and is_deleted = false)",
    )
    fun existsFoundComment(@Param("seq") seq: Long): Boolean
}

@Repository
class ReportPersistenceAdapter(
    private val reportJpaRepository: ReportJpaRepository,
) : ReportRepository {

    override fun exists(reporterUserSeq: Int, targetType: ReportTargetType, targetSeq: Long) =
        reportJpaRepository.existsByReporterUserSeqAndTargetTypeAndTargetSeq(reporterUserSeq, targetType, targetSeq)

    override fun saveAndFlush(report: Report): Report = try {
        reportJpaRepository.saveAndFlush(report)
    } catch (e: DataIntegrityViolationException) {
        // 사전 조회를 지나 동시에 들어온 같은 신고
        if ("uq_reports_reporter_target" in e.mostSpecificCause.message.orEmpty()) {
            throw BusinessException(ReportErrorCode.REPORT_ALREADY_EXISTS)
        }
        throw e
    }

    override fun findActiveUserRole(userSeq: Long) = reportJpaRepository.findActiveUserRole(userSeq)

    override fun existsTarget(targetType: ReportTargetType, targetSeq: Long) = when (targetType) {
        ReportTargetType.MISSING -> reportJpaRepository.existsMissing(targetSeq)
        ReportTargetType.FOUND -> reportJpaRepository.existsFound(targetSeq)
        ReportTargetType.MISSING_COMMENT -> reportJpaRepository.existsMissingComment(targetSeq)
        ReportTargetType.FOUND_COMMENT -> reportJpaRepository.existsFoundComment(targetSeq)
        ReportTargetType.USER -> findActiveUserRole(targetSeq) != null
    }
}
