package com.ssafy.animal.core.rescue.domain

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
import java.time.OffsetDateTime

enum class RescueStatus {
    PROCESSING, DONE, FAILED, CANCELED;

    val isTerminal: Boolean get() = this != PROCESSING
}

/** 10장 구조 사유 코드 4종(Q461) */
enum class RescueReasonCode { WANDERING, INJURY_ILLNESS, DANGEROUS_PLACE, OTHER }

/** 접수 정보 묶음. 접수(10.3)와 수정(10.4)이 같이 쓴다 */
data class RescueContent(
    val rescueReasonCode: RescueReasonCode,
    val rescueAddress: String,
    val rescueAddressDetail: String?,
    val rescuedDttm: OffsetDateTime,
    val rescueMemo: String?,
)

/** DB 명세 19. rescue_records. 항상 PROCESSING으로 만들고 종료 3종으로 한 번만 바꾼다(10.5) */
@Entity
@Table(name = "rescue_records")
class RescueRecord(
    shelterSeq: Int,
    foundReportSeq: Int?,
    content: RescueContent,
    regUsrSeq: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var rescueSeq: Int? = null
        protected set

    @Column(nullable = false)
    var shelterSeq: Int = shelterSeq
        protected set

    var foundReportSeq: Int? = foundReportSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RescueStatus = RescueStatus.PROCESSING
        protected set

    var completedDttm: OffsetDateTime? = null
        protected set

    @Column(nullable = false)
    var rescuedDttm: OffsetDateTime = content.rescuedDttm
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var rescueReasonCode: RescueReasonCode = content.rescueReasonCode
        protected set

    @Column(nullable = false, length = 255)
    var rescueAddress: String = content.rescueAddress
        protected set

    @Column(length = 200)
    var rescueAddressDetail: String? = content.rescueAddressDetail
        protected set

    var rescueMemo: String? = content.rescueMemo
        protected set

    var resultReason: String? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var regUsrSeq: Int? = regUsrSeq
        protected set

    var modUsrSeq: Int? = null
        protected set

    val content: RescueContent
        get() = RescueContent(rescueReasonCode, rescueAddress, rescueAddressDetail, rescuedDttm, rescueMemo)

    /** 10.4. 종료 상태에서 메모 외 변경 금지는 서비스가 먼저 검사한다(R4 A) */
    fun update(content: RescueContent, modUsrSeq: Int) {
        rescueReasonCode = content.rescueReasonCode
        rescueAddress = content.rescueAddress
        rescueAddressDetail = content.rescueAddressDetail
        rescuedDttm = content.rescuedDttm
        rescueMemo = content.rescueMemo
        this.modUsrSeq = modUsrSeq
    }

    /** 10.5. PROCESSING → 종료 3종만, 되돌릴 수 없다. 완료 시각은 서버가 기록 */
    fun complete(status: RescueStatus, resultReason: String?, now: OffsetDateTime, modUsrSeq: Int) {
        check(this.status == RescueStatus.PROCESSING && status.isTerminal) { "잘못된 구조 상태 전이 ${this.status} → $status" }
        this.status = status
        this.resultReason = if (status == RescueStatus.DONE) null else resultReason
        this.completedDttm = now
        this.modUsrSeq = modUsrSeq
    }

    /** 10.6 논리 삭제. 연결 보호동물 검사는 서비스가 먼저 한다 */
    fun delete(modUsrSeq: Int) {
        isDeleted = true
        this.modUsrSeq = modUsrSeq
    }
}

/** API 명세 19장 RESCUE_ */
enum class RescueErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    RESCUE_NOT_FOUND(HttpStatus.NOT_FOUND, "구조 기록을 찾을 수 없습니다."),
    RESCUE_ACTIVE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 발견 신고에는 이미 진행 중이거나 완료된 구조 기록이 있습니다."),
    RESCUE_INVALID_TRANSITION(HttpStatus.CONFLICT, "변경할 수 없는 구조 상태입니다."),
    RESCUE_RESULT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "실패·취소 사유를 입력해 주세요."),
    RESCUE_HAS_ANIMALS(HttpStatus.CONFLICT, "연결된 보호동물이 있어 삭제할 수 없습니다."),

    /** 11.4 보호동물 등록에서 DONE이 아닌 구조를 참조(Q442). 보호동물 쪽이 쓴다 */
    RESCUE_NOT_DONE(HttpStatus.CONFLICT, "구조가 완료된 기록만 연결할 수 있습니다."),
}
