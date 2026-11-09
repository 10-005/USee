package com.ssafy.animal.core.missing.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.missing.application.port.`in`.MissingReportUseCase
import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingStatus
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
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

/** 7장 실종 신고. 조회는 로그인 사용자, 작성은 USER(Q421), 수정·상태·삭제는 작성자 본인(타인은 404, Q434) */
@RestController
@RequestMapping("/api/core/v1/missing-reports")
class MissingReportController(
    private val missingReportUseCase: MissingReportUseCase,
) {

    /** 7.1 */
    @GetMapping
    fun search(
        @RequestParam(required = false) status: MissingStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredTo: LocalDate?,
        @RequestParam(required = false) districtCode: String?,
        @RequestParam(required = false) speciesSeq: Int?,
        @RequestParam(required = false) gender: MissingGender?,
        @RequestParam(required = false) isNeutered: Boolean?,
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<MissingSummaryResponse> {
        val filter = MissingSearchFilter(status, occurredFrom, occurredTo, districtCode, speciesSeq, gender, isNeutered, keyword)
        return PageResponse.of(missingReportUseCase.search(filter, pageable).map(MissingSummaryResponse::from))
    }

    /** 7.3. 페이지 없이 배열(Q464) */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun getMine(@AuthenticationPrincipal user: AuthUser) =
        missingReportUseCase.getMine(user.userSeq).map(MissingSummaryResponse::from)

    /** 7.2 */
    @GetMapping("/{missingReportSeq}")
    fun get(@AuthenticationPrincipal user: AuthUser, @PathVariable missingReportSeq: Long) =
        MissingDetailResponse.from(missingReportUseCase.get(user.userSeq, missingReportSeq))

    /** 7.4 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @SuccessMessage("실종 신고가 등록되었습니다.")
    fun create(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreateMissingRequest) =
        CreatedMissingResponse.from(missingReportUseCase.create(user.userSeq, request.toCommand()))

    /** 7.5 */
    @PatchMapping("/{missingReportSeq}")
    fun update(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable missingReportSeq: Long,
        @RequestBody request: UpdateMissingRequest,
    ) = MissingDetailResponse.from(missingReportUseCase.update(user.userSeq, missingReportSeq, request.toCommand()))

    /** 7.6 */
    @PatchMapping("/{missingReportSeq}/status")
    fun changeStatus(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable missingReportSeq: Long,
        @Valid @RequestBody request: ChangeMissingStatusRequest,
    ) = MissingStatusResponse.from(missingReportUseCase.changeStatus(user.userSeq, missingReportSeq, request.status!!))

    /** 7.7 */
    @DeleteMapping("/{missingReportSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable missingReportSeq: Long) {
        missingReportUseCase.delete(user.userSeq, missingReportSeq)
    }
}
