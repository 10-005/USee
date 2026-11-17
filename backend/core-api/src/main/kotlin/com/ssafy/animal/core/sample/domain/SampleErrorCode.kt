package com.ssafy.animal.core.sample.domain

import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class SampleErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    SAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "샘플을 찾을 수 없습니다."),
}
