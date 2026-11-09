package com.ssafy.animal.core.adoption.domain

import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate
import java.time.OffsetDateTime

enum class ApplicationStatus {
    DRAFT, SUBMITTED, APPROVED, REJECTED, CANCELED, ADOPTED;

    companion object {
        /** 같은 회원·동물에 1건만(uq_app_active_user_animal) */
        val ACTIVE = listOf(DRAFT, SUBMITTED, APPROVED)

        /** 보호소에 보이는 상태(Q422·Q460). DRAFT·CANCELED는 비노출 */
        val RECEIVED = listOf(SUBMITTED, APPROVED, ADOPTED, REJECTED)
    }
}

/** 신청서 고정 필드 4개 + 문항 답변(Q458) */
data class ApplicationForm(
    val answers: Map<String, Any>,
    val careAddress: String?,
    val careAddressDetail: String?,
    val email: String?,
    val visitHopeDate: LocalDate?,
)

/** 제출 시점 신청자 스냅샷 */
data class Applicant(val name: String?, val birthDate: LocalDate?, val phone: String?)

/** DB 명세 24. adoption_applications. 상태 이력 테이블 없음, 별도 프로세스 없음 */
@Entity
@Table(name = "adoption_applications")
class AdoptionApplication(
    userSeq: Int,
    animalSeq: Int,
    shelterSeq: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var applicationSeq: Long? = null
        protected set

    @Column(nullable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false)
    var animalSeq: Int = animalSeq
        protected set

    /** 신청 당시 보호소 스냅샷 */
    @Column(nullable = false)
    var shelterSeq: Int = shelterSeq
        protected set

    /** 키는 문자열 questionSeq */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var answersJsonb: Map<String, Any> = emptyMap()
        protected set

    @Column(length = 200)
    var careAddress: String? = null
        protected set

    @Column(length = 100)
    var careAddressDetail: String? = null
        protected set

    @Column(length = 254)
    var email: String? = null
        protected set

    var visitHopeDate: LocalDate? = null
        protected set

    @Column(length = 100)
    var applicantName: String? = null
        protected set

    var applicantBirthDate: LocalDate? = null
        protected set

    @Column(length = 30)
    var applicantPhone: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: ApplicationStatus = ApplicationStatus.DRAFT
        protected set

    var submittedDttm: OffsetDateTime? = null
        protected set

    var reviewedDttm: OffsetDateTime? = null
        protected set

    var reviewedUsrSeq: Int? = null
        protected set

    var rejectionReason: String? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var modUsrSeq: Int? = null
        protected set

    /** 12.2 카드 날짜·정렬(Q459): DRAFT는 마지막 임시저장, 그 외는 제출일 */
    val displayDttm: OffsetDateTime?
        get() = if (status == ApplicationStatus.DRAFT) modDttm ?: regDttm else submittedDttm ?: modDttm ?: regDttm

    /** 보호소가 볼 수 있는가: 제출된 적 있고 DRAFT·CANCELED가 아님(제출 전 자동 거절된 DRAFT도 비노출) */
    val isVisibleToShelter: Boolean get() = status in ApplicationStatus.RECEIVED && submittedDttm != null

    /** 새 행이면 actorSeq=null(mod_* 컬럼은 수정 때만 채운다) */
    fun saveDraft(form: ApplicationForm, actorSeq: Int?) {
        applyForm(form)
        modUsrSeq = actorSeq
    }

    /** DRAFT를 덮어써 제출하거나 새 행을 바로 SUBMITTED로 만든다(새 행이면 actorSeq=null) */
    fun submit(form: ApplicationForm, applicant: Applicant, now: OffsetDateTime, actorSeq: Int?) {
        applyForm(form)
        applicantName = applicant.name
        applicantBirthDate = applicant.birthDate
        applicantPhone = applicant.phone
        status = ApplicationStatus.SUBMITTED
        submittedDttm = now
        modUsrSeq = actorSeq
    }

    fun approve(now: OffsetDateTime, actorSeq: Int) = review(ApplicationStatus.APPROVED, null, now, actorSeq)

    /** 수동 거절·승인 후 실패는 actor, 자동 거절은 null(reviewedUsrSeq=null) */
    fun reject(reason: String, now: OffsetDateTime, actorSeq: Int?) = review(ApplicationStatus.REJECTED, reason, now, actorSeq)

    fun cancel(actorSeq: Int) {
        status = ApplicationStatus.CANCELED
        modUsrSeq = actorSeq
    }

    fun complete(actorSeq: Int) {
        status = ApplicationStatus.ADOPTED
        modUsrSeq = actorSeq
    }

    private fun review(status: ApplicationStatus, reason: String?, now: OffsetDateTime, actorSeq: Int?) {
        this.status = status
        rejectionReason = reason
        reviewedDttm = now
        reviewedUsrSeq = actorSeq
        modUsrSeq = actorSeq
    }

    private fun applyForm(form: ApplicationForm) {
        answersJsonb = form.answers
        careAddress = form.careAddress
        careAddressDetail = form.careAddressDetail
        email = form.email
        visitHopeDate = form.visitHopeDate
    }
}
