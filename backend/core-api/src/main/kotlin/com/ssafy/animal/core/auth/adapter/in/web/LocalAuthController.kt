package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginResult
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 2.2 비인증 경로(application.yml security.permit-paths) */
@RestController
@RequestMapping("/api/core/v1/auth/local")
class LocalAuthController(
    private val localLoginUseCase: LocalLoginUseCase,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LocalLoginRequest) =
        LocalLoginResponse.from(localLoginUseCase.login(request.loginId!!, request.password!!))
}

data class LocalLoginRequest(
    // 길이 상한: Redis 키(auth:login-fail:{loginId})에 그대로 들어가므로 login_identifier 컬럼 길이로 막는다
    @field:NotBlank @field:Size(max = 255)
    val loginId: String?,
    @field:NotBlank
    val password: String?,
)

data class LocalLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val role: Role,
    val userSeq: Long,
    val shelterSeq: Long,
) {
    companion object {
        fun from(result: LocalLoginResult) = LocalLoginResponse(
            accessToken = result.tokens.accessToken,
            refreshToken = result.tokens.refreshToken,
            tokenType = "Bearer",
            expiresIn = result.tokens.expiresIn,
            role = Role.SHELTER,
            userSeq = result.userSeq,
            shelterSeq = result.shelterSeq,
        )
    }
}
