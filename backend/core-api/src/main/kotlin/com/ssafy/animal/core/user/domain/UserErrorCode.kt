package com.ssafy.animal.core.user.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 2.5·3.2 실패 코드 */
enum class UserErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    USER_REQUIRED_PROFILE_MISSING(HttpStatus.BAD_REQUEST, "필수 회원 정보가 없습니다."),
    USER_DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    USER_DUPLICATE_PHONE(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),
}
