package com.ssafy.animal.core.shelter.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterUseCase
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/core/v1/shelters")
class ShelterController(
    private val shelterUseCase: ShelterUseCase,
) {

    /** 9.1 보호소 목록·검색 */
    @GetMapping
    fun getPage(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) districtCode: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(shelterUseCase.getPage(keyword, districtCode, pageable).map(ShelterSummaryResponse::from))

    /** 9.3 소속 보호소 상세. 대상은 토큰 shelterSeq로만 정한다 */
    @GetMapping("/me")
    @PreAuthorize("hasRole('SHELTER')")
    fun getMine(@AuthenticationPrincipal user: AuthUser) = ShelterResponse.from(shelterUseCase.get(user.shelterSeq))

    /** 9.4 소속 보호소 운영정보·이미지 부분 수정 */
    @PatchMapping("/me")
    @PreAuthorize("hasRole('SHELTER')")
    fun updateMine(@AuthenticationPrincipal user: AuthUser, @RequestBody request: UpdateShelterRequest) =
        ShelterResponse.from(shelterUseCase.updateMine(user.userSeq, user.shelterSeq, request.toCommand()))

    /** 9.2 보호소 상세 */
    @GetMapping("/{shelterSeq}")
    fun get(@PathVariable shelterSeq: Long) = ShelterResponse.from(shelterUseCase.get(shelterSeq))
}
