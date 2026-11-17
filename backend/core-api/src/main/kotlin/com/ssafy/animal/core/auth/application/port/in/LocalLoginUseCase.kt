package com.ssafy.animal.core.auth.application.port.`in`

interface LocalLoginUseCase {
    /** 2.2 사전 발급 SHELTER 계정 로그인 */
    fun login(loginId: String, password: String): LocalLoginResult
}

data class LocalLoginResult(
    val tokens: IssuedTokens,
    val userSeq: Long,
    val shelterSeq: Long,
)
