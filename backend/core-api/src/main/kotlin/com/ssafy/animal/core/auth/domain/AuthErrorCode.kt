package com.ssafy.animal.core.auth.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 2.1·2.3 실패 코드 */
enum class AuthErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    AUTH_INVALID_KAKAO_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 카카오 인증 정보입니다."),
    AUTH_INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 refresh 토큰입니다."),
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "이용할 수 없는 계정입니다."),
    AUTH_KAKAO_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "카카오 계정이 일반회원이 아닌 계정과 연결되어 있습니다."),
}
