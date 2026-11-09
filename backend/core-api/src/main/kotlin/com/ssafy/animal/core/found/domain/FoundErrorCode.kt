package com.ssafy.animal.core.found.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 19장 FOUND_ */
enum class FoundErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    FOUND_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "발견 신고를 찾을 수 없습니다."),
    FOUND_REPORT_CLOSED(HttpStatus.CONFLICT, "종료된 발견 신고는 수정할 수 없습니다."),
    FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE(HttpStatus.CONFLICT, "배정할 수 있는 보호소가 없습니다."),
}

/**
 * 19장 IMAGE_INVALID_COUNT(400). image 패키지 ImageErrorCode에 아직 없어 응답 코드만 같게 여기 둔다.
 * ponytail: ImageErrorCode에 IMAGE_INVALID_COUNT가 생기면 이 enum을 지우고 그쪽을 쓴다(코디네이터 요청).
 */
enum class FoundImageErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    IMAGE_INVALID_COUNT(HttpStatus.BAD_REQUEST, "사진은 1~3장이어야 합니다."),
}
