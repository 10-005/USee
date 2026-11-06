package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.core.auth.application.port.`in`.IssuedTokens
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterCommand
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterResult
import com.ssafy.animal.core.auth.application.port.`in`.KakaoVerifyResult
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class KakaoVerifyRequest(
    @field:NotBlank
    val kakaoAccessToken: String?,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String?,
)

data class LogoutRequest(
    @field:NotBlank
    val refreshToken: String?,
    /** 있으면 이 기기의 FCM 토큰만 비활성화(Q419 A) */
    val fcmToken: String?,
)

/** 2.1 응답. 미가입이면 토큰·user는 null, registrationToken만 채운다. */
data class KakaoVerifyResponse(
    val requiresRegistration: Boolean,
    val registrationToken: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenType: String?,
    val expiresIn: Long?,
    val user: LoginUser?,
) {
    data class LoginUser(val userSeq: Long, val role: Role, val nickname: String?)

    companion object {
        fun from(result: KakaoVerifyResult) = KakaoVerifyResponse(
            requiresRegistration = result.tokens == null,
            registrationToken = result.registrationToken,
            accessToken = result.tokens?.accessToken,
            refreshToken = result.tokens?.refreshToken,
            tokenType = result.tokens?.let { TOKEN_TYPE },
            expiresIn = result.tokens?.expiresIn,
            user = result.user?.let { LoginUser(it.userSeq!!.toLong(), it.role, it.nickname) },
        )
    }
}

/** 2.5 요청. 누락은 서비스가 401·400 도메인 코드로 처리하므로 Bean Validation을 걸지 않는다. */
data class KakaoRegisterRequest(
    val registrationToken: String?,
    val nickname: String?,
    val name: String?,
    val birthDate: LocalDate?,
    val phone: String?,
) {
    fun toCommand() = KakaoRegisterCommand(registrationToken, nickname, name, birthDate, phone)
}

/** 2.5 응답(명세 예시대로 expiresIn 없음) */
data class KakaoRegisterResponse(
    val userSeq: Long,
    val role: Role,
    val nickname: String?,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
) {
    companion object {
        fun from(result: KakaoRegisterResult) = KakaoRegisterResponse(
            userSeq = result.user.userSeq!!.toLong(),
            role = result.user.role,
            nickname = result.user.nickname,
            accessToken = result.tokens.accessToken,
            refreshToken = result.tokens.refreshToken,
            tokenType = TOKEN_TYPE,
        )
    }
}

/** 2.3 응답 */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
) {
    companion object {
        fun from(tokens: IssuedTokens) = TokenResponse(tokens.accessToken, tokens.refreshToken, TOKEN_TYPE, tokens.expiresIn)
    }
}

private const val TOKEN_TYPE = "Bearer"
