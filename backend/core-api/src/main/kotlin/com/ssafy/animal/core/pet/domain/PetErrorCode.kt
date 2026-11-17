package com.ssafy.animal.core.pet.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

/** API 명세 19장. 반려동물은 USER_ 접두어(Q452) */
enum class PetErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    USER_PET_NOT_FOUND(HttpStatus.NOT_FOUND, "반려동물을 찾을 수 없습니다."),

    // ponytail: ImageErrorCode에 아직 없어 여기 둔다(응답 code는 같다). ImageErrorCode로 옮기면 지운다
    IMAGE_INVALID_COUNT(HttpStatus.BAD_REQUEST, "사진은 1~3장이어야 합니다."),
}
