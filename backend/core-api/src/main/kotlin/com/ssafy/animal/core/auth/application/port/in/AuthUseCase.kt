package com.ssafy.animal.core.auth.application.port.`in`

import com.ssafy.animal.core.user.domain.User

interface AuthUseCase {
    /** 2.1 기존 회원이면 토큰 발급, 미가입이면 가입 컨텍스트(registrationToken)만 반환 */
    fun verifyKakao(kakaoAccessToken: String): KakaoVerifyResult

    /** 2.3 refresh rotation */
    fun refresh(refreshToken: String): IssuedTokens

    /** 2.4 이 기기의 refresh 폐기 */
    fun logout(userSeq: Long, refreshToken: String, fcmToken: String?)
}

data class IssuedTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)

/** 로그인 완료면 tokens·user, 미가입이면 registrationToken만 채운다. */
data class KakaoVerifyResult(
    val tokens: IssuedTokens?,
    val user: User?,
    val registrationToken: String?,
)
