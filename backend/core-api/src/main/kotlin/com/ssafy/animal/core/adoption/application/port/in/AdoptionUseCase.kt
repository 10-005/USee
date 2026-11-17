package com.ssafy.animal.core.adoption.application.port.`in`

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.adoption.domain.QuestionType
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSummary
import io.swagger.v3.oas.annotations.media.Schema
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

data class QuestionList(
    @field:Schema(description = "현재 공통 문항 전체. displayOrder 오름차순")
    val questions: List<QuestionView>,
)

data class QuestionView(
    @field:Schema(description = "문항 번호. 모든 환경에서 1~20 고정. answers의 키(문자열)로 쓴다", example = "1")
    val questionSeq: Int,
    @field:Schema(description = "화면 표시 순서", example = "1")
    val displayOrder: Int,
    @field:Schema(description = "문항 문구", example = "주거 형태")
    val questionText: String,
    @field:Schema(description = QUESTION_TYPE_DESCRIPTION, example = "SINGLE_CHOICE")
    val questionType: QuestionType,
    @get:Schema(name = "isRequired", description = "필수 여부. 조건부 문항은 condition.required_when_matched가 우선", example = "true")
    val isRequired: Boolean,
    @field:Schema(description = "선택형 보기 문자열 배열. TEXT·DATE는 null", nullable = true, example = "[\"아파트\", \"단독주택\", \"다세대\", \"오피스텔\", \"기타\"]")
    val options: List<String>?,
    @field:Schema(
        description = "조건부 노출 규칙. 일반 문항은 null. depends_on: 기준 questionSeq, operator: EQ·NE·IN, value: 비교 값, " +
            "required_when_matched: 조건이 맞을 때 필수 여부. 조건이 맞지 않으면 숨김이고 서버가 그 답변을 지운다",
        nullable = true,
        example = "{\"depends_on\": 17, \"operator\": \"EQ\", \"value\": \"이수\", \"required_when_matched\": true}",
    )
    val condition: Map<String, Any?>?,
)

data class MyApplicationItem(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = APPLICATION_STATUS_DESCRIPTION, example = "SUBMITTED")
    val status: ApplicationStatus,
    @field:Schema(description = "카드 날짜·정렬 기준. DRAFT는 마지막 임시저장 시각(없으면 생성 시각), 그 외는 제출 시각", example = "2026-10-27T10:00:00+09:00")
    val displayDttm: OffsetDateTime?,
    @field:Schema(description = "제출 시각. 제출 전(DRAFT, 제출 전에 자동 거절·취소된 건)은 null", nullable = true, example = "2026-10-27T10:00:00+09:00")
    val submittedDttm: OffsetDateTime?,
    @field:Schema(description = "거절 사유. REJECTED일 때만 채운다", nullable = true, example = "null")
    val rejectionReason: String?,
    @field:Schema(description = "신청 대상 보호동물 요약. 보호동물 행을 찾지 못하면 null", nullable = true)
    val animalSummary: AnimalSummary?,
)

data class QuestionBrief(
    @field:Schema(description = "문항 번호(answers의 키)", example = "1")
    val questionSeq: Int,
    @field:Schema(description = "현재 문항 문구. 과거 신청서도 현재 문구로 보여 준다", example = "주거 형태")
    val questionText: String,
    @field:Schema(description = QUESTION_TYPE_DESCRIPTION, example = "SINGLE_CHOICE")
    val questionType: QuestionType,
    @field:Schema(description = "화면 표시 순서", example = "1")
    val displayOrder: Int,
)

data class ApplicantView(
    @field:Schema(description = "신청자 실명. 제출 전에는 현재 회원 정보, 제출 후에는 제출 시점 스냅샷. 정보가 없으면 null", nullable = true, example = "김가람")
    val name: String?,
    @field:Schema(description = "생년월일. 정보가 없으면 null", nullable = true, example = "1997-05-12")
    val birthDate: LocalDate?,
    @field:Schema(description = "전화번호(숫자만). 정보가 없으면 null", nullable = true, example = "01012345678")
    val phone: String?,
)

/** 심사자. 명세에 형식이 없어 처리자 userSeq와 보호소명으로 채운다(보고서 '명세 확인 필요') */
data class ReviewerView(
    @field:Schema(description = "심사한 보호소 계정 userSeq", example = "55")
    val userSeq: Int,
    @field:Schema(description = "표시 이름. 대상 보호동물의 보호소명", nullable = true, example = "구미보호소")
    val displayName: String?,
)

data class ApplicationDetail(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "신청자 userSeq", example = "17")
    val userSeq: Int,
    @field:Schema(description = APPLICATION_STATUS_DESCRIPTION, example = "SUBMITTED")
    val status: ApplicationStatus,
    @field:Schema(description = "신청 대상 보호동물 요약. 보호동물 행을 찾지 못하면 null", nullable = true)
    val animalSummary: AnimalSummary?,
    @field:Schema(description = "양육 장소 주소. DRAFT에서는 비어 있을 수 있다", nullable = true, example = "경상북도 구미시 구미로 123길 45-6")
    val careAddress: String?,
    @field:Schema(description = "양육 장소 상세 주소. 선택 항목", nullable = true, example = "구미아파트 7층 809호")
    val careAddressDetail: String?,
    @field:Schema(description = "연락 이메일. 선택 항목", nullable = true, example = "example@email.com")
    val email: String?,
    @field:Schema(description = "방문 희망일. DRAFT에서는 비어 있을 수 있다", nullable = true, example = "2026-11-20")
    val visitHopeDate: LocalDate?,
    @field:Schema(
        description = "문항 답변. 키는 문자열 questionSeq, 값은 문자열(TEXT·SINGLE_CHOICE·DATE) 또는 문자열 배열(MULTI_CHOICE). 답하지 않은 문항·숨김 문항은 키가 없다",
        example = "{\"1\": \"아파트\", \"2\": \"가능\"}",
    )
    val answers: Map<String, Any>,
    @field:Schema(description = "현재 문항 목록(답변 표시용). displayOrder 오름차순")
    val questions: List<QuestionBrief>,
    @field:Schema(description = "신청자 인적 사항")
    val applicant: ApplicantView,
    @field:Schema(description = "제출 시각. DRAFT는 null", nullable = true, example = "2026-10-29T17:50:00+09:00")
    val submittedDttm: OffsetDateTime?,
    @field:Schema(description = "심사(승인·거절·승인 후 실패·자동 거절) 시각. 심사 전이면 null", nullable = true, example = "null")
    val reviewedDttm: OffsetDateTime?,
    @field:Schema(description = "심사자. 심사 전이거나 자동 거절이면 null", nullable = true)
    val reviewer: ReviewerView?,
    @field:Schema(description = "거절 사유. REJECTED일 때만 채운다", nullable = true, example = "null")
    val rejectionReason: String?,
)

data class ReceivedItem(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "상태. 보호소 목록에는 SUBMITTED(심사 대기)·APPROVED(서류 승인)·ADOPTED(최종 승인)·REJECTED(거절)만 나온다", example = "SUBMITTED")
    val status: ApplicationStatus,
    @field:Schema(description = "제출 시점 신청자 실명 스냅샷", example = "김가람")
    val applicantName: String?,
    @field:Schema(description = "제출 시각", example = "2026-10-10T10:00:00+09:00")
    val submittedDttm: OffsetDateTime?,
    @field:Schema(description = "신청 대상 보호동물 요약. 보호동물 행을 찾지 못하면 null", nullable = true)
    val animalSummary: AnimalSummary?,
)

data class DraftCreated(
    @field:Schema(description = "만든 입양 신청 번호. 이후 12.5 저장·12.7 삭제에 쓴다", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "항상 DRAFT", example = "DRAFT")
    val status: ApplicationStatus,
    @field:Schema(description = "신청 대상 보호동물 번호", example = "700")
    val animalSeq: Int,
)

data class DraftSaved(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "항상 DRAFT", example = "DRAFT")
    val status: ApplicationStatus,
    @field:Schema(description = "저장된 답변 수. null 값과 숨김 문항 답변을 뺀 뒤의 개수", example = "2")
    val savedQuestionCount: Int,
)

data class Submitted(
    @field:Schema(description = "입양 신청 번호. DRAFT가 있었으면 그 번호, 없었으면 새 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "항상 SUBMITTED", example = "SUBMITTED")
    val status: ApplicationStatus,
    @field:Schema(description = "제출 시각", example = "2026-10-29T17:50:00+09:00")
    val submittedDttm: OffsetDateTime?,
)

data class StatusChanged(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "항상 CANCELED", example = "CANCELED")
    val status: ApplicationStatus,
)

data class Reviewed(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "APPROVE면 APPROVED, REJECT면 REJECTED", example = "APPROVED")
    val status: ApplicationStatus,
    @field:Schema(description = "심사 시각", example = "2026-10-29T17:50:00+09:00")
    val reviewedDttm: OffsetDateTime?,
    @field:Schema(description = "심사한 보호소 계정 userSeq", example = "55")
    val reviewedUsrSeq: Int?,
)

data class Completed(
    @field:Schema(description = "채택한 입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "항상 ADOPTED", example = "ADOPTED")
    val applicationStatus: ApplicationStatus,
    @field:Schema(description = "보호동물 번호", example = "700")
    val animalSeq: Int,
    @field:Schema(description = "보호동물 보호 상태. 항상 ADOPTED", example = "ADOPTED")
    val protectionStatus: String,
    @field:Schema(description = "보호동물 입양 상태. 항상 ADOPTED", example = "ADOPTED")
    val adoptionStatus: String,
    @field:Schema(description = "보호동물에 연결된 채택 신청 번호(applicationSeq와 같다)", example = "901")
    val completedApplicationSeq: Long,
    @field:Schema(description = "같은 동물의 다른 활성 신청(DRAFT·SUBMITTED·APPROVED) 중 자동 거절된 수", example = "3")
    val otherApplicationsAutoRejected: Int,
)

data class Failed(
    @field:Schema(description = "입양 신청 번호", example = "901")
    val applicationSeq: Long,
    @field:Schema(description = "항상 REJECTED", example = "REJECTED")
    val status: ApplicationStatus,
    @field:Schema(description = "저장된 실패 사유(앞뒤 공백 제거)", example = "승인 이후 입양 진행 과정이 완료되지 못했습니다.")
    val rejectionReason: String?,
)

private const val APPLICATION_STATUS_DESCRIPTION =
    "신청 상태. DRAFT: 임시저장(제출 전), SUBMITTED: 서류 제출(심사 대기), APPROVED: 서류 승인, " +
        "ADOPTED: 최종 입양 완료, REJECTED: 거절(서류 거절·승인 후 실패·자동 거절), CANCELED: 신청자 제출 취소 또는 탈퇴로 자동 취소"

private const val QUESTION_TYPE_DESCRIPTION =
    "문항 유형. TEXT: 주관식(답변은 문자열), SINGLE_CHOICE: 단일 선택(보기 중 하나의 문자열), " +
        "MULTI_CHOICE: 복수 선택(보기 문자열 배열), DATE: 날짜(\"YYYY-MM-DD\" 문자열)"
