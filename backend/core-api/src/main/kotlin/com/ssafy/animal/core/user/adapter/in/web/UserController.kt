package com.ssafy.animal.core.user.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.user.application.port.`in`.UserProfileUseCase
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/core/v1/users")
class UserController(
    private val userProfileUseCase: UserProfileUseCase,
) {

    /** 3.1 내 프로필. SHELTER는 9.3을 쓴다(Q451) */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun getMe(@AuthenticationPrincipal user: AuthUser) = MyProfileResponse.from(userProfileUseCase.getMe(user.userSeq))

    /** 3.2 내 프로필 부분 갱신 */
    @PatchMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun updateMe(@AuthenticationPrincipal user: AuthUser, @RequestBody request: UpdateMyProfileRequest) =
        MyProfileResponse.from(userProfileUseCase.updateMe(user.userSeq, request.toCommand()))

    /** 3.5 공개 프로필 */
    @GetMapping("/{userSeq}/profile")
    fun getPublicProfile(@PathVariable userSeq: Long) = PublicProfileResponse.from(userProfileUseCase.getPublicProfile(userSeq))
}
