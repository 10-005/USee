package com.ssafy.animal.core.rescue.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.application.port.`in`.RescueSource
import com.ssafy.animal.core.rescue.application.port.`in`.RescueUseCase
import com.ssafy.animal.core.rescue.domain.RescueStatus
import jakarta.validation.Valid
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

/** 10장은 모두 SHELTER 전용(보호소 웹 "구조일지") */
@RestController
@RequestMapping("/api/core/v1/rescues")
@PreAuthorize("hasRole('SHELTER')")
class RescueController(
    private val rescueUseCase: RescueUseCase,
) {

    /** 10.1 우리 보호소 구조 기록 + 상태별 건수(size 기본 8) */
    @GetMapping
    fun getPage(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) status: RescueStatus?,
        @RequestParam(required = false) period: RecentPeriod?,
        @RequestParam(required = false) speciesSeq: Int?,
        @RequestParam(required = false) source: RescueSource?,
        @RequestParam(required = false) foundReportSeq: Int?,
        @PageableDefault(size = 8) pageable: Pageable,
    ) = RescuePageResponse.from(
        rescueUseCase.getPage(user.shelterSeq, RescueFilter(status, period, speciesSeq, source, foundReportSeq), pageable),
    )

    /** 10.2 */
    @GetMapping("/{rescueSeq}")
    fun get(@AuthenticationPrincipal user: AuthUser, @PathVariable rescueSeq: Long) =
        RescueResponse.from(rescueUseCase.get(user.shelterSeq, rescueSeq))

    /** 10.3 발견 신고 연계/직접 접수(PROCESSING) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("구조가 접수되었습니다.")
    fun create(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreateRescueRequest) =
        CreatedRescueResponse.from(rescueUseCase.create(user, request.toCommand()))

    /** 10.4 구조 진행 기록 수정 */
    @PatchMapping("/{rescueSeq}")
    fun update(@AuthenticationPrincipal user: AuthUser, @PathVariable rescueSeq: Long, @RequestBody request: UpdateRescueRequest) =
        RescueResponse.from(rescueUseCase.update(user, rescueSeq, request.toCommand()))

    /** 10.5 DONE/FAILED/CANCELED 처리 */
    @PatchMapping("/{rescueSeq}/status")
    fun changeStatus(@AuthenticationPrincipal user: AuthUser, @PathVariable rescueSeq: Long, @Valid @RequestBody request: ChangeRescueStatusRequest) =
        RescueStatusResponse.from(rescueUseCase.changeStatus(user, rescueSeq, request.status!!, request.resultReason))

    /** 10.6 논리 삭제 */
    @DeleteMapping("/{rescueSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable rescueSeq: Long) {
        rescueUseCase.delete(user, rescueSeq)
    }
}
