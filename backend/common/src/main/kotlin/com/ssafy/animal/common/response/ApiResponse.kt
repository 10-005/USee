package com.ssafy.animal.common.response

import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.web.TraceIdFilter
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.MDC

/**
 * 모든 응답의 봉투. status는 직접 채우지 않는다: ApiResponseBodyAdvice가 실제 HTTP 상태로 맞춘다.
 */
data class ApiResponse<T>(
    val status: Int,
    val code: String,
    val message: String,
    val data: T?,
    val errors: List<ErrorDetail>,
    val traceId: String?,
) {
    companion object {
        const val SUCCESS_CODE = "SUCCESS"
        const val DEFAULT_SUCCESS_MESSAGE = "요청이 성공했습니다."

        fun <T> success(status: Int, data: T?, message: String = DEFAULT_SUCCESS_MESSAGE) =
            ApiResponse(status, SUCCESS_CODE, message, data, emptyList(), MDC.get(TraceIdFilter.MDC_KEY))

        fun error(
            errorCode: ErrorCode,
            message: String = errorCode.message,
            errors: List<ErrorDetail> = emptyList(),
        ) = ApiResponse<Nothing>(
            errorCode.status.value(), errorCode.name, message, null, errors, MDC.get(TraceIdFilter.MDC_KEY),
        )
    }
}

data class ErrorDetail(
    @field:Schema(description = "문제가 된 필드·파라미터 이름. 객체 전체 오류면 null", nullable = true, example = "nickname")
    val field: String?,
    @field:Schema(description = "실패 사유", example = "필수 입력값입니다.")
    val reason: String,
)
