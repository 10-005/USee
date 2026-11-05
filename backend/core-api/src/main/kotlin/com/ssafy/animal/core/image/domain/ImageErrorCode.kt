package com.ssafy.animal.core.image.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class ImageErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "이미지는 10MB 이하만 올릴 수 있습니다."),
    IMAGE_UNSUPPORTED_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPG, PNG 이미지만 올릴 수 있습니다."),
}
