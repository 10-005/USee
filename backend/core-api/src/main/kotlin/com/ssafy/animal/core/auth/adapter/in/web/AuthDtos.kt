package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.auth.application.port.`in`.IssuedTokens
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterCommand
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterResult
import com.ssafy.animal.core.auth.application.port.`in`.KakaoVerifyResult
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.time.LocalDate

data class KakaoVerifyRequest(
    @field:Schema(description = "앱이 카카오 SDK로 받은 카카오 access token", example = "kakao-access-token")
    @field:NotBlank
    val kakaoAccessToken: String?,
)

data class RefreshRequest(
    @field:Schema(description = "마지막으로 발급받은 refresh 토큰", example = "jwt-refresh-token")
    @field:NotBlank
    val refreshToken: String?,
)

data class LogoutRequest(
    @field:Schema(description = "이 기기의 refresh 토큰", example = "jwt-refresh-token")
    @field:NotBlank
    val refreshToken: String?,
    /** 있으면 이 기기의 FCM 토큰만 비활성화(Q419 A) */
    @field:Schema(description = "이 기기의 FCM 토큰. 보내면 이 기기 푸시만 끈다. 생략하면 푸시 설정은 그대로", nullable = true, example = "fcm-device-token")
    val fcmToken: String?,
)

/** 2.1 응답. 미가입이면 토큰·user는 null, registrationToken만 채운다. */
data class KakaoVerifyResponse(
    @field:Schema(description = "true면 미가입. registrationToken으로 회원가입(2.5)을 이어 간다", example = "false")
    val requiresRegistration: Boolean,
    @field:Schema(description = "가입 컨텍스트 토큰(10분 유효, JWT 아님). 미가입일 때만 채운다", nullable = true, example = "short-lived-registration-context")
    val registrationToken: String?,
    @field:Schema(description = "access 토큰. 기존 회원일 때만 채운다", nullable = true, example = "jwt-access-token")
    val accessToken: String?,
    @field:Schema(description = "refresh 토큰. 기존 회원일 때만 채운다", nullable = true, example = "jwt-refresh-token")
    val refreshToken: String?,
    @field:Schema(description = "항상 Bearer. 기존 회원일 때만 채운다", nullable = true, example = "Bearer")
    val tokenType: String?,
    @field:Schema(description = "access 토큰 유효 시간(초). 기존 회원일 때만 채운다", nullable = true, example = "3600")
    val expiresIn: Long?,
    @field:Schema(description = "로그인한 회원. 기존 회원일 때만 채운다", nullable = true)
    val user: LoginUser?,
) {
    data class LoginUser(
        @field:Schema(description = "회원 번호", example = "17")
        val userSeq: Long,
        @field:Schema(description = "역할. USER: 일반 회원, SHELTER: 보호소 담당자. 카카오 로그인은 항상 USER", example = "USER")
        val role: Role,
        @field:Schema(description = "닉네임", nullable = true, example = "반려가족")
        val nickname: String?,
    )

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
    @field:Schema(description = "2.1에서 받은 가입 컨텍스트 토큰", example = "short-lived-registration-context")
    val registrationToken: String?,
    @field:Schema(description = "닉네임. 2~10자, 한글·영문·숫자와 . _만, 공백 불가. 활성 회원 간 중복 불가", example = "반려가족")
    val nickname: String?,
    @field:Schema(description = "이름. 최대 100자", example = "김가람")
    val name: String?,
    @field:Schema(description = "생년월일(YYYY-MM-DD)", example = "1997-05-12")
    val birthDate: LocalDate?,
    @field:Schema(description = "전화번호. 최대 30자. 활성 회원 간 중복 불가", example = "01012345678")
    val phone: String?,
) {
    fun toCommand() = KakaoRegisterCommand(registrationToken, nickname, name, birthDate, phone)
}

/** 2.5 응답(명세 예시대로 expiresIn 없음) */
data class KakaoRegisterResponse(
    @field:Schema(description = "새 회원 번호", example = "17")
    val userSeq: Long,
    @field:Schema(description = "역할. 카카오 가입은 항상 USER", example = "USER")
    val role: Role,
    @field:Schema(description = "닉네임", nullable = true, example = "반려가족")
    val nickname: String?,
    @field:Schema(description = "access 토큰", example = "jwt-access-token")
    val accessToken: String,
    @field:Schema(description = "refresh 토큰", example = "jwt-refresh-token")
    val refreshToken: String,
    @field:Schema(description = "항상 Bearer", example = "Bearer")
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
    @field:Schema(description = "새 access 토큰", example = "jwt-access-token")
    val accessToken: String,
    @field:Schema(description = "새 refresh 토큰. 요청에 쓴 refresh 토큰은 이제 쓸 수 없다", example = "jwt-refresh-token")
    val refreshToken: String,
    @field:Schema(description = "항상 Bearer", example = "Bearer")
    val tokenType: String,
    @field:Schema(description = "access 토큰 유효 시간(초)", example = "3600")
    val expiresIn: Long,
) {
    companion object {
        fun from(tokens: IssuedTokens) = TokenResponse(tokens.accessToken, tokens.refreshToken, TOKEN_TYPE, tokens.expiresIn)
    }
}

private const val TOKEN_TYPE = "Bearer"

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class KakaoVerifySuccessSchema : SuccessResponseSchema<KakaoVerifyResponse>()
abstract class KakaoRegisterSuccessSchema : SuccessResponseSchema<KakaoRegisterResponse>()
abstract class TokenSuccessSchema : SuccessResponseSchema<TokenResponse>()
