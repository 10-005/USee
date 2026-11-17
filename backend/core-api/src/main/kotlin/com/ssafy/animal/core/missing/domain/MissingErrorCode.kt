package com.ssafy.animal.core.missing.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 19장 MISSING_ */
enum class MissingErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    MISSING_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "실종 신고를 찾을 수 없습니다."),
    MISSING_REPORT_CLOSED(HttpStatus.CONFLICT, "종료된 실종 신고는 수정할 수 없습니다. 먼저 재개해 주세요."),

    // ponytail: ImageErrorCode에 아직 없어 여기 둔다(응답 code는 같다). ImageErrorCode로 옮기면 지운다
    IMAGE_INVALID_COUNT(HttpStatus.BAD_REQUEST, "사진은 1~3장이어야 합니다."),
}
