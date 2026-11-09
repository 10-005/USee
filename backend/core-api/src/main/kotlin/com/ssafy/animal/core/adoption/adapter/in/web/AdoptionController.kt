package com.ssafy.animal.core.adoption.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.adoption.application.port.`in`.AdoptionUseCase
import com.ssafy.animal.core.adoption.application.port.`in`.FormInput
import com.ssafy.animal.core.adoption.application.port.`in`.FormPatch
import com.ssafy.animal.core.adoption.application.port.`in`.ReviewDecision
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.animal.adapter.`in`.web.toEnum
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.Optional

/** 명세 12장 입양 문항·신청·심사·완료. 12.15는 [보류]라 없다 */
@RestController
@RequestMapping("/api/core/v1")
class AdoptionController(
    private val adoptionUseCase: AdoptionUseCase,
) {

    /** 12.1 현재 공통 문항 */
    @GetMapping("/adoption/questions")
    fun getQuestions() = adoptionUseCase.getQuestions()

    /** 12.2 내 입양 신청(정렬은 displayDttm desc 고정이라 sort는 무시) */
    @GetMapping("/adoption-applications")
    @PreAuthorize("hasRole('USER')")
    fun getMine(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) status: ApplicationStatus?,
        @RequestParam(required = false) animalSeq: Int?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(adoptionUseCase.getMyApplications(user.userSeq, status, animalSeq, PageRequest.of(pageable.pageNumber, pageable.pageSize)))

    /** 12.9 소속 보호소 수신 신청 */
    @GetMapping("/adoption-applications/received")
    @PreAuthorize("hasRole('SHELTER')")
    fun getReceived(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) status: ApplicationStatus?,
        @RequestParam(required = false) animalSeq: Int?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(adoptionUseCase.getReceived(user, status, animalSeq, pageable))

    /** 12.16 상태별 건수 */
    @GetMapping("/adoption-applications/received/status-counts")
    @PreAuthorize("hasRole('SHELTER')")
    fun countReceived(@AuthenticationPrincipal user: AuthUser, @RequestParam(required = false) animalSeq: Int?) =
        adoptionUseCase.countReceived(user, animalSeq)

    /** 12.3 상세(신청자·담당 보호소) */
    @GetMapping("/adoption-applications/{applicationSeq}")
    fun get(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long) = adoptionUseCase.getApplication(user, applicationSeq)

    /** 12.4 DRAFT 생성 */
    @PostMapping("/adoption-applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    fun createDraft(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: ApplicationFormRequest) =
        adoptionUseCase.createDraft(user.userSeq, request.animalSeq!!, request.toInput())

    /** 12.6 최종 저장 + 제출(animalSeq 기준, Q458) */
    @PostMapping("/adoption-applications/submit")
    @PreAuthorize("hasRole('USER')")
    fun submit(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: ApplicationFormRequest) =
        adoptionUseCase.submit(user.userSeq, request.animalSeq!!, request.toInput())

    /** 12.5 DRAFT 저장 */
    @PatchMapping("/adoption-applications/{applicationSeq}")
    @PreAuthorize("hasRole('USER')")
    fun saveDraft(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long, @RequestBody request: SaveDraftRequest) =
        adoptionUseCase.saveDraft(user.userSeq, applicationSeq, request.toPatch())

    /** 12.7 DRAFT 물리 삭제 */
    @DeleteMapping("/adoption-applications/{applicationSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    fun deleteDraft(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long) {
        adoptionUseCase.deleteDraft(user.userSeq, applicationSeq)
    }

    /** 12.8 제출 취소 */
    @PostMapping("/adoption-applications/{applicationSeq}/cancel")
    @PreAuthorize("hasRole('USER')")
    fun cancel(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long) = adoptionUseCase.cancel(user.userSeq, applicationSeq)

    /** 12.10 서류 심사 */
    @PostMapping("/adoption-applications/{applicationSeq}/review")
    @PreAuthorize("hasRole('SHELTER')")
    fun review(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long, @Valid @RequestBody request: ReviewRequest) =
        adoptionUseCase.review(user, applicationSeq, request.decision.toEnum<ReviewDecision>("decision")!!, request.rejectionReason)

    /** 12.11 실제 입양 완료 */
    @PostMapping("/adoption-applications/{applicationSeq}/complete")
    @PreAuthorize("hasRole('SHELTER')")
    fun complete(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long) = adoptionUseCase.complete(user, applicationSeq)

    /** 12.13 승인 후 진행 실패 */
    @PostMapping("/adoption-applications/{applicationSeq}/fail")
    @PreAuthorize("hasRole('SHELTER')")
    fun fail(@AuthenticationPrincipal user: AuthUser, @PathVariable applicationSeq: Long, @RequestBody request: FailRequest) =
        adoptionUseCase.fail(user, applicationSeq, request.rejectionReason)
}

/** 12.4·12.6 본문 */
data class ApplicationFormRequest(
    @field:NotNull val animalSeq: Int? = null,
    val answers: Map<String, Any?>? = null,
    val careAddress: String? = null,
    val careAddressDetail: String? = null,
    val email: String? = null,
    val visitHopeDate: LocalDate? = null,
) {
    fun toInput() = FormInput(answers.orEmpty(), careAddress?.trim(), careAddressDetail?.trim(), email?.trim(), visitHopeDate)
}

/** 12.5: 필드 미전달은 null(유지), 명시적 null은 Optional.empty()(비움) */
data class SaveDraftRequest(
    val answers: Map<String, Any?>? = null,
    val careAddress: Optional<String>? = null,
    val careAddressDetail: Optional<String>? = null,
    val email: Optional<String>? = null,
    val visitHopeDate: Optional<LocalDate>? = null,
) {
    fun toPatch() = FormPatch(answers, careAddress, careAddressDetail, email, visitHopeDate)
}

data class ReviewRequest(
    @field:NotNull val decision: String? = null,
    val rejectionReason: String? = null,
)

data class FailRequest(val rejectionReason: String? = null)
