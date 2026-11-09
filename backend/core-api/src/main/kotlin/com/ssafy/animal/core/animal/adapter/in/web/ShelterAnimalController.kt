package com.ssafy.animal.core.animal.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.RegistrationPeriod
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalUseCase
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.Gender
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

/** 명세 11장 보호동물. 소속 검증은 서비스가 토큰 shelterSeq로 한다 */
@RestController
@RequestMapping("/api/core/v1/shelter-animals")
class ShelterAnimalController(
    private val shelterAnimalUseCase: ShelterAnimalUseCase,
) {

    /** 11.1 목록·검색(로그인) */
    @GetMapping
    fun search(
        @RequestParam(required = false) status: AnimalStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) regFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) regTo: LocalDate?,
        @RequestParam(required = false) districtCode: String?,
        @RequestParam(required = false) speciesSeq: Int?,
        @RequestParam(required = false) gender: Gender?,
        @RequestParam(required = false) isNeutered: Boolean?,
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(
        shelterAnimalUseCase.search(
            AnimalSearchCondition(status, regFrom, regTo, districtCode?.takeIf { it.isNotBlank() }, speciesSeq, gender, isNeutered, keyword),
            pageable,
        ),
    )

    /** 11.3 우리 보호소 보호동물(size 기본 8, 상태별 건수 동봉) */
    @GetMapping("/me")
    @PreAuthorize("hasRole('SHELTER')")
    fun searchMine(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) status: AnimalStatus?,
        @RequestParam(required = false) period: RegistrationPeriod?,
        @RequestParam(required = false) speciesSeq: Int?,
        @RequestParam(required = false) gender: Gender?,
        @RequestParam(required = false) isNeutered: Boolean?,
        @PageableDefault(size = 8) pageable: Pageable,
    ) = shelterAnimalUseCase.searchMine(user, MyAnimalSearchCondition(status, period, speciesSeq, gender, isNeutered), pageable)

    /** 11.10 보호소 전용 상세 */
    @GetMapping("/me/{animalSeq}")
    @PreAuthorize("hasRole('SHELTER')")
    fun getForShelter(@AuthenticationPrincipal user: AuthUser, @PathVariable animalSeq: Int) =
        shelterAnimalUseCase.getForShelter(user, animalSeq)

    /** 11.2 앱 상세(USER 전용, Q478) */
    @GetMapping("/{animalSeq}")
    @PreAuthorize("hasRole('USER')")
    fun getForUser(@AuthenticationPrincipal user: AuthUser, @PathVariable animalSeq: Int) =
        shelterAnimalUseCase.getForUser(user.userSeq, animalSeq)

    /** 11.4 등록 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SHELTER')")
    fun register(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: RegisterAnimalRequest) =
        shelterAnimalUseCase.register(user, request.toCommand())

    /** 11.5 기초 정보·사진 수정 */
    @PatchMapping("/{animalSeq}")
    @PreAuthorize("hasRole('SHELTER')")
    fun update(@AuthenticationPrincipal user: AuthUser, @PathVariable animalSeq: Int, @Valid @RequestBody request: UpdateAnimalRequest) =
        shelterAnimalUseCase.update(user, animalSeq, request.toCommand())

    /** 11.6 보호 상태·입양 가능 여부·공고기간 관리 */
    @PatchMapping("/{animalSeq}/management")
    @PreAuthorize("hasRole('SHELTER')")
    fun manage(@AuthenticationPrincipal user: AuthUser, @PathVariable animalSeq: Int, @RequestBody request: ManageAnimalRequest) =
        shelterAnimalUseCase.manage(user, animalSeq, request.toCommand())

    /** 11.9 논리 삭제 */
    @DeleteMapping("/{animalSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SHELTER')")
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable animalSeq: Int) {
        shelterAnimalUseCase.delete(user, animalSeq)
    }
}
