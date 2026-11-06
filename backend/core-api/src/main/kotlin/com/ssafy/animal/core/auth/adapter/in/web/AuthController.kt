package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.auth.application.port.`in`.AuthUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** verify·refresh는 비인증 경로(application.yml security.permit-paths), logout은 로그인 필요 */
@RestController
@RequestMapping("/api/core/v1/auth")
class AuthController(
    private val authUseCase: AuthUseCase,
) {

    @PostMapping("/kakao/verify")
    fun verifyKakao(@Valid @RequestBody request: KakaoVerifyRequest) =
        KakaoVerifyResponse.from(authUseCase.verifyKakao(request.kakaoAccessToken!!))

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest) =
        TokenResponse.from(authUseCase.refresh(request.refreshToken!!))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: LogoutRequest) {
        authUseCase.logout(user.userSeq, request.refreshToken!!, request.fcmToken)
    }
}
