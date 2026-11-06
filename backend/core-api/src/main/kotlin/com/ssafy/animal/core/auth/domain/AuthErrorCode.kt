package com.ssafy.animal.core.auth.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 2.1·2.2·2.3·2.5 실패 코드 */
enum class AuthErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    AUTH_INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 카카오 인증 정보입니다."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh 토큰입니다."),
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다."),
    AUTH_KAKAO_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "이미 다른 계정과 연결된 카카오 계정입니다."),
    AUTH_INVALID_REGISTRATION_CONTEXT(HttpStatus.UNAUTHORIZED, "가입 정보가 없거나 만료되었습니다. 카카오 로그인을 다시 해 주세요."),
}
