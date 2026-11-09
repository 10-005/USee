package com.ssafy.animal.core.animal.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 19장 ANIMAL_ */
enum class AnimalErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    ANIMAL_NOT_FOUND(HttpStatus.NOT_FOUND, "보호동물을 찾을 수 없습니다."),
    ANIMAL_INVALID_STATUS_COMBINATION(HttpStatus.CONFLICT, "보호 상태와 입양 상태 조합이 올바르지 않습니다."),
    ANIMAL_HAS_ACTIVE_APPLICATIONS(HttpStatus.CONFLICT, "진행 중인 입양 신청이 있어 삭제할 수 없습니다."),
}

/**
 * 11.4·11.5가 내는 다른 도메인 코드. rescue 패키지(S3)의 RescueErrorCode와 ImageErrorCode에 아직 없어 임시로 둔다.
 * 응답 code는 enum 상수 이름이라 나중에 원래 enum으로 옮겨도 API는 같다(코디네이터 요청).
 */
enum class AnimalForeignErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    RESCUE_NOT_FOUND(HttpStatus.NOT_FOUND, "구조 기록을 찾을 수 없습니다."),
    RESCUE_NOT_DONE(HttpStatus.CONFLICT, "구조가 완료된 기록만 연결할 수 있습니다."),
    IMAGE_INVALID_COUNT(HttpStatus.BAD_REQUEST, "사진은 1~3장이어야 합니다."),
}
