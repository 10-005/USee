package com.ssafy.animal.core.auth.application.port.`in`

import com.ssafy.animal.core.user.domain.User
import java.time.LocalDate

interface KakaoRegisterUseCase {
    /** 2.5 가입 컨텍스트의 카카오 id로 USER + KAKAO 계정을 만들고 토큰을 발급한다. */
    fun register(command: KakaoRegisterCommand): KakaoRegisterResult
}

/** 누락 여부는 서비스가 판단해 명세 코드(401·400)를 내므로 모두 nullable로 받는다. */
data class KakaoRegisterCommand(
    val registrationToken: String?,
    val nickname: String?,
    val name: String?,
    val birthDate: LocalDate?,
    val phone: String?,
)

data class KakaoRegisterResult(
    val user: User,
    val tokens: IssuedTokens,
)
