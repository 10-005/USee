package com.ssafy.animal.core.report.domain

import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.http.HttpStatus

/** 15.1 신고 대상 5종(Q449·Q477). DB CHECK ck_report_target_type과 같다 */
enum class ReportTargetType(val reasons: Set<ReportReason>) {
    USER(ReportReason.USER_OR_COMMENT),
    MISSING(ReportReason.POST),
    FOUND(ReportReason.POST),
    MISSING_COMMENT(ReportReason.USER_OR_COMMENT),
    FOUND_COMMENT(ReportReason.USER_OR_COMMENT),
}

/** 15.1 사유 코드. 대상별 허용 세트는 DB CHECK ck_report_reason_by_target과 같다 */
enum class ReportReason {
    FALSE_INFO, SPAM_AD, OFFENSIVE, OTHER, HARASSMENT, OBSCENE;

    companion object {
        val POST = setOf(FALSE_INFO, SPAM_AD, OFFENSIVE, OTHER)
        val USER_OR_COMMENT = setOf(HARASSMENT, SPAM_AD, OBSCENE, OTHER)
    }
}

enum class ReportStatus { PENDING }

/** API 명세 15장·19장 REPORT_ 코드 */
enum class ReportErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    REPORT_TARGET_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "신고할 수 없는 대상입니다."),
    REPORT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "신고 사유를 선택해 주세요."),
    REPORT_REASON_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "이 대상에는 선택할 수 없는 신고 사유입니다."),
    REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 신고한 대상입니다."),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상을 찾을 수 없습니다."),
}

/** DB 명세 31. reports. 접수만 하고 열람·심사 API는 없다(Q428) */
@Entity
@Table(name = "reports")
class Report(
    reporterUserSeq: Int,
    targetType: ReportTargetType,
    targetSeq: Long,
    reasonCode: ReportReason,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var reportSeq: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var reporterUserSeq: Int = reporterUserSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    var targetType: ReportTargetType = targetType
        protected set

    @Column(nullable = false, updatable = false)
    var targetSeq: Long = targetSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    var reasonCode: ReportReason = reasonCode
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReportStatus = ReportStatus.PENDING
        protected set

    var modUsrSeq: Int? = null
        protected set
}
