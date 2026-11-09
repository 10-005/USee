package com.ssafy.animal.core.adoption.application.port.`in`

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.adoption.domain.QuestionType
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSummary
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional

/** 12장 입양. 반환값은 응답 형태 그대로 */
interface AdoptionUseCase {
    /** 12.1 */
    fun getQuestions(): QuestionList

    /** 12.2 */
    fun getMyApplications(userSeq: Long, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<MyApplicationItem>

    /** 12.3 신청자 본인 또는 담당 SHELTER */
    fun getApplication(user: AuthUser, applicationSeq: Long): ApplicationDetail

    /** 12.4 */
    fun createDraft(userSeq: Long, animalSeq: Int, form: FormInput): DraftCreated

    /** 12.5 */
    fun saveDraft(userSeq: Long, applicationSeq: Long, patch: FormPatch): DraftSaved

    /** 12.6 */
    fun submit(userSeq: Long, animalSeq: Int, form: FormInput): Submitted

    /** 12.7 */
    fun deleteDraft(userSeq: Long, applicationSeq: Long)

    /** 12.8 */
    fun cancel(userSeq: Long, applicationSeq: Long): StatusChanged

    /** 12.9 */
    fun getReceived(shelter: AuthUser, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<ReceivedItem>

    /** 12.16 */
    fun countReceived(shelter: AuthUser, animalSeq: Int?): Map<String, Long>

    /** 12.10 */
    fun review(shelter: AuthUser, applicationSeq: Long, decision: ReviewDecision, rejectionReason: String?): Reviewed

    /** 12.11 */
    fun complete(shelter: AuthUser, applicationSeq: Long): Completed

    /** 12.13 */
    fun fail(shelter: AuthUser, applicationSeq: Long, rejectionReason: String?): Failed

    /** 2.6 탈퇴: 본인 DRAFT/SUBMITTED/APPROVED → CANCELED. 탈퇴 트랜잭션 안에서 부른다 */
    fun cancelAllOfWithdrawnUser(userSeq: Long)
}

enum class ReviewDecision { APPROVE, REJECT }

/** 12.4·12.6 본문. answers 값은 문자열 또는 문자열 배열 */
data class FormInput(
    val answers: Map<String, Any?> = emptyMap(),
    val careAddress: String? = null,
    val careAddressDetail: String? = null,
    val email: String? = null,
    val visitHopeDate: LocalDate? = null,
)

/** 12.5: answers는 보내면 객체 전체 교체, 고정 필드는 미전달(null)이면 유지·명시적 null(Optional.empty)이면 비움 */
data class FormPatch(
    val answers: Map<String, Any?>? = null,
    val careAddress: Optional<String>? = null,
    val careAddressDetail: Optional<String>? = null,
    val email: Optional<String>? = null,
    val visitHopeDate: Optional<LocalDate>? = null,
)

data class QuestionList(val questions: List<QuestionView>)

data class QuestionView(
    val questionSeq: Int,
    val displayOrder: Int,
    val questionText: String,
    val questionType: QuestionType,
    val isRequired: Boolean,
    val options: List<String>?,
    val condition: Map<String, Any?>?,
)

data class MyApplicationItem(
    val applicationSeq: Long,
    val status: ApplicationStatus,
    val displayDttm: OffsetDateTime?,
    val submittedDttm: OffsetDateTime?,
    val rejectionReason: String?,
    val animalSummary: AnimalSummary?,
)

data class QuestionBrief(val questionSeq: Int, val questionText: String, val questionType: QuestionType, val displayOrder: Int)

data class ApplicantView(val name: String?, val birthDate: LocalDate?, val phone: String?)

/** 심사자. 명세에 형식이 없어 처리자 userSeq와 보호소명으로 채운다(보고서 '명세 확인 필요') */
data class ReviewerView(val userSeq: Int, val displayName: String?)

data class ApplicationDetail(
    val applicationSeq: Long,
    val userSeq: Int,
    val status: ApplicationStatus,
    val animalSummary: AnimalSummary?,
    val careAddress: String?,
    val careAddressDetail: String?,
    val email: String?,
    val visitHopeDate: LocalDate?,
    val answers: Map<String, Any>,
    val questions: List<QuestionBrief>,
    val applicant: ApplicantView,
    val submittedDttm: OffsetDateTime?,
    val reviewedDttm: OffsetDateTime?,
    val reviewer: ReviewerView?,
    val rejectionReason: String?,
)

data class ReceivedItem(
    val applicationSeq: Long,
    val status: ApplicationStatus,
    val applicantName: String?,
    val submittedDttm: OffsetDateTime?,
    val animalSummary: AnimalSummary?,
)

data class DraftCreated(val applicationSeq: Long, val status: ApplicationStatus, val animalSeq: Int)

data class DraftSaved(val applicationSeq: Long, val status: ApplicationStatus, val savedQuestionCount: Int)

data class Submitted(val applicationSeq: Long, val status: ApplicationStatus, val submittedDttm: OffsetDateTime?)

data class StatusChanged(val applicationSeq: Long, val status: ApplicationStatus)

data class Reviewed(val applicationSeq: Long, val status: ApplicationStatus, val reviewedDttm: OffsetDateTime?, val reviewedUsrSeq: Int?)

data class Completed(
    val applicationSeq: Long,
    val applicationStatus: ApplicationStatus,
    val animalSeq: Int,
    val protectionStatus: String,
    val adoptionStatus: String,
    val completedApplicationSeq: Long,
    val otherApplicationsAutoRejected: Int,
)

data class Failed(val applicationSeq: Long, val status: ApplicationStatus, val rejectionReason: String?)
