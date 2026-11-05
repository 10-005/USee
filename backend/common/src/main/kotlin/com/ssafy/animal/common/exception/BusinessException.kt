package com.ssafy.animal.common.exception

import com.ssafy.animal.common.response.ErrorDetail

class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    val errors: List<ErrorDetail> = emptyList(),
) : RuntimeException(message)
