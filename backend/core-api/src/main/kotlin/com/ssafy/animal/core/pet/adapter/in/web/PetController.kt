package com.ssafy.animal.core.pet.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.pet.application.port.`in`.PetUseCase
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 6장 내 반려동물. USER 전용 */
@RestController
@RequestMapping("/api/core/v1/users/me/pets")
@PreAuthorize("hasRole('USER')")
class PetController(
    private val petUseCase: PetUseCase,
) {

    /** 6.1 */
    @GetMapping
    fun getPage(@AuthenticationPrincipal user: AuthUser, @PageableDefault(size = 20) pageable: Pageable) =
        PageResponse.of(petUseCase.getPage(user.userSeq, pageable).map(PetSummaryResponse::from))

    /** 6.2 */
    @GetMapping("/{petSeq}")
    fun get(@AuthenticationPrincipal user: AuthUser, @PathVariable petSeq: Long) =
        PetDetailResponse.from(petUseCase.get(user.userSeq, petSeq))

    /** 6.3 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("반려동물이 등록되었습니다.")
    fun create(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreatePetRequest) =
        CreatedPetResponse.from(petUseCase.create(user.userSeq, request.toCommand()))

    /** 6.4 */
    @PatchMapping("/{petSeq}")
    fun update(@AuthenticationPrincipal user: AuthUser, @PathVariable petSeq: Long, @RequestBody request: UpdatePetRequest) =
        PetDetailResponse.from(petUseCase.update(user.userSeq, petSeq, request.toCommand()))

    /** 6.5 */
    @DeleteMapping("/{petSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable petSeq: Long) {
        petUseCase.delete(user.userSeq, petSeq)
    }
}
