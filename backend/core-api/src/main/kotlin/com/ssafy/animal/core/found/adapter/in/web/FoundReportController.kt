package com.ssafy.animal.core.found.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.FoundReportUseCase
import com.ssafy.animal.core.found.application.port.`in`.FoundSearchFilter
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.found.domain.Gender
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

@RestController
@RequestMapping("/api/core/v1/found-reports")
class FoundReportController(
    private val foundReportUseCase: FoundReportUseCase,
) {

    /** 8.1 발견 신고 검색(모든 필터 선택, 생략 시 조건 없음) */
    @GetMapping
    fun search(
        @RequestParam(required = false) status: FoundStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredTo: LocalDate?,
        @RequestParam(required = false) districtCode: String?,
        @RequestParam(required = false) speciesSeq: Int?,
        @RequestParam(required = false) gender: Gender?,
        @RequestParam(required = false) isNeutered: Boolean?,
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(
        foundReportUseCase.search(
            FoundSearchFilter(status, occurredFrom, occurredTo, districtCode, speciesSeq, gender, isNeutered, keyword),
            pageable,
        ).map(FoundReportSummaryResponse::from),
    )

    /** 8.3 내 발견 신고 전량(배열) */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun getMine(@AuthenticationPrincipal user: AuthUser) =
        foundReportUseCase.getMine(user.userSeq).map(FoundReportSummaryResponse::from)

    /** 8.8 내 보호소 담당 발견 신고(size 기본 8) */
    @GetMapping("/assigned")
    @PreAuthorize("hasRole('SHELTER')")
    fun getAssigned(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) status: FoundStatus?,
        @RequestParam(required = false) period: RecentPeriod?,
        @RequestParam(required = false) districtCode: String?,
        @RequestParam(required = false) speciesSeq: Int?,
        @RequestParam(required = false) rescueStatus: RescueProgress?,
        @PageableDefault(size = 8) pageable: Pageable,
    ) = PageResponse.of(
        foundReportUseCase.getAssigned(user.shelterSeq, AssignedFilter(status, period, districtCode, speciesSeq, rescueStatus), pageable)
            .map(AssignedFoundReportResponse::from),
    )

    /** 8.2 상세 */
    @GetMapping("/{foundReportSeq}")
    fun get(@AuthenticationPrincipal user: AuthUser, @PathVariable foundReportSeq: Long) =
        FoundReportResponse.from(foundReportUseCase.get(user, foundReportSeq))

    /** 8.4 등록·담당 보호소 자동 배정 */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("발견 신고가 등록되었습니다.")
    fun register(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: RegisterFoundReportRequest) =
        foundReportUseCase.register(user.userSeq, request.toCommand()).let {
            RegisteredFoundReportResponse(it.foundReportSeq, it.status, it.districtCode, it.assignedShelterSeq, it.assignmentMethod.name)
        }

    /** 8.5 작성자 부분 수정 */
    @PatchMapping("/{foundReportSeq}")
    @PreAuthorize("hasRole('USER')")
    fun update(@AuthenticationPrincipal user: AuthUser, @PathVariable foundReportSeq: Long, @RequestBody request: UpdateFoundReportRequest) =
        FoundReportResponse.from(foundReportUseCase.update(user, foundReportSeq, request.toCommand()))

    /** 8.6 ACTIVE↔CLOSED. 작성자 또는 담당 SHELTER(종료만) */
    @PatchMapping("/{foundReportSeq}/status")
    fun changeStatus(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable foundReportSeq: Long,
        @Valid @RequestBody request: ChangeFoundStatusRequest,
    ) = FoundStatusResponse.from(foundReportUseCase.changeStatus(user, foundReportSeq, request.status!!, request.closedReason))

    /** 8.7 논리 삭제 */
    @DeleteMapping("/{foundReportSeq}")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable foundReportSeq: Long) {
        foundReportUseCase.delete(user.userSeq, foundReportSeq)
    }
}
