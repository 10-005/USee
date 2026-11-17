package com.ssafy.animal.core.auth.application.port.out

/** 카카오 access token을 검증하고 카카오 회원번호를 돌려준다. 무효 토큰이면 AUTH_INVALID_KAKAO_TOKEN */
interface KakaoClient {
    fun fetchKakaoId(kakaoAccessToken: String): String
}
