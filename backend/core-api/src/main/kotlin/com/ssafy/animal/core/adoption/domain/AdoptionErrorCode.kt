package com.ssafy.animal.core.adoption.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 19장 ADOPTION_ */
enum class AdoptionErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 입양 신청이 있습니다."),
    ADOPTION_ANIMAL_NOT_AVAILABLE(HttpStatus.CONFLICT, "지금은 입양 신청을 받지 않는 보호동물입니다."),
    ADOPTION_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "입양 신청서를 찾을 수 없습니다."),
    ADOPTION_APPLICATION_NOT_DRAFT(HttpStatus.CONFLICT, "임시저장 상태의 신청서만 처리할 수 있습니다."),
    ADOPTION_APPLICATION_NOT_SUBMITTED(HttpStatus.CONFLICT, "제출된 신청서만 처리할 수 있습니다."),
    ADOPTION_APPLICATION_NOT_APPROVED(HttpStatus.CONFLICT, "승인된 신청서만 처리할 수 있습니다."),
    ADOPTION_REQUIRED_ANSWER_MISSING(HttpStatus.BAD_REQUEST, "필수 항목이 비어 있습니다."),
    ADOPTION_INVALID_ANSWER(HttpStatus.BAD_REQUEST, "답변 형식이 올바르지 않습니다."),
    ADOPTION_REJECTION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "거절 사유를 입력해 주세요."),
    ADOPTION_ANIMAL_ALREADY_FINISHED(HttpStatus.CONFLICT, "이미 다른 신청서로 입양이 완료된 보호동물입니다."),
}
