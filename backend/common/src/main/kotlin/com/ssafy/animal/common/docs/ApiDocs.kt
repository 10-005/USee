package com.ssafy.animal.common.docs

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.ssafy.animal.common.response.ErrorDetail
import io.swagger.v3.oas.annotations.media.Schema

/*
 * Swagger 문서 전용. 런타임에서는 쓰지 않는다.
 * 실제 응답은 ApiResponseBodyAdvice가 감싼 ApiResponse<T>인데, 제네릭은 @Schema(implementation)로 지목할 수 없어 미러를 둔다.
 */

/** 실패 응답 미러. 모든 서비스가 공용으로 쓴다. */
@Schema(name = "FailResponse", description = "실패 응답 봉투. data는 항상 null")
data class FailResponseSchema(
    @field:Schema(description = "HTTP 상태 코드", example = "400")
    val status: Int,
    @field:Schema(description = "에러 코드(enum 상수명)", example = "COMMON_INVALID_INPUT")
    val code: String,
    @field:Schema(description = "사용자에게 보여 줄 수 있는 메시지", example = "입력값이 올바르지 않습니다.")
    val message: String,
    @field:Schema(description = "항상 null", nullable = true, example = "null")
    val data: Any?,
    @field:Schema(description = "필드 검증 실패 사유. 그 외에는 빈 배열")
    val errors: List<ErrorDetail>,
    @field:Schema(description = "추적 ID. 응답 헤더 X-Trace-Id와 같은 값", example = "4f2c9e1a7b3d4c5e")
    val traceId: String?,
)

/**
 * 성공 응답 미러의 공통 필드. data 타입별로 한 줄짜리 하위 클래스를 만들어 @Schema(implementation)에 쓴다.
 * 예: `abstract class TokenSuccessSchema : SuccessResponseSchema<TokenResponse>()`
 */
@JsonPropertyOrder("status", "code", "message", "data", "errors", "traceId")
abstract class SuccessResponseSchema<T> {
    @get:Schema(description = "HTTP 상태 코드", example = "200")
    abstract val status: Int

    @get:Schema(description = "성공이면 항상 SUCCESS", example = "SUCCESS")
    abstract val code: String

    @get:Schema(description = "성공 메시지", example = "요청이 성공했습니다.")
    abstract val message: String

    @get:Schema(description = "응답 데이터")
    abstract val data: T

    @get:Schema(description = "성공이면 항상 빈 배열")
    abstract val errors: List<ErrorDetail>

    @get:Schema(description = "추적 ID. 응답 헤더 X-Trace-Id와 같은 값", example = "4f2c9e1a7b3d4c5e")
    abstract val traceId: String?
}

/** 여러 API에서 같은 모양으로 나오는 실패 응답 예시. 도메인 에러 예시는 각 컨트롤러에 둔다. */
object FailExamples {
    const val COMMON_INVALID_JSON =
        """{"status":400,"code":"COMMON_INVALID_JSON","message":"요청 본문을 읽을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val AUTH_UNAUTHORIZED =
        """{"status":401,"code":"AUTH_UNAUTHORIZED","message":"인증이 필요합니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val AUTH_TOKEN_EXPIRED =
        """{"status":401,"code":"AUTH_TOKEN_EXPIRED","message":"토큰이 만료되었습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val AUTH_TOKEN_INVALID =
        """{"status":401,"code":"AUTH_TOKEN_INVALID","message":"유효하지 않은 토큰입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val AUTH_FORBIDDEN =
        """{"status":403,"code":"AUTH_FORBIDDEN","message":"접근 권한이 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val AUTH_ACCOUNT_UNAVAILABLE =
        """{"status":403,"code":"AUTH_ACCOUNT_UNAVAILABLE","message":"이용할 수 없는 계정입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    const val COMMON_SERVICE_UNAVAILABLE =
        """{"status":503,"code":"COMMON_SERVICE_UNAVAILABLE","message":"서비스를 일시적으로 사용할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
}
