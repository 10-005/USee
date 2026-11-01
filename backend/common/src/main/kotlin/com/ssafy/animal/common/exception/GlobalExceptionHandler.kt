package com.ssafy.animal.common.exception

import com.ssafy.animal.common.response.ApiResponse
import com.ssafy.animal.common.response.ErrorDetail
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.ErrorResponse
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException) = respond(e.errorCode, e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(e: MethodArgumentNotValidException) = respond(
        CommonErrorCode.COMMON_INVALID_INPUT,
        errors = e.bindingResult.allErrors.map {
            ErrorDetail((it as? FieldError)?.field, it.defaultMessage ?: INVALID_VALUE)
        },
    )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException) = respond(
        CommonErrorCode.COMMON_INVALID_INPUT,
        errors = e.constraintViolations.map { ErrorDetail(it.propertyPath.lastOrNull()?.name, it.message) },
    )

    // 컨트롤러 파라미터(@RequestParam, @PathVariable)에 붙은 제약 위반
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(e: HandlerMethodValidationException) = respond(
        CommonErrorCode.COMMON_INVALID_INPUT,
        errors = e.parameterValidationResults.flatMap { result ->
            result.resolvableErrors.map {
                ErrorDetail(result.methodParameter.parameterName, it.defaultMessage ?: INVALID_VALUE)
            }
        },
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException) =
        respond(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(e.name, "타입이 올바르지 않습니다.")))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException) = respond(CommonErrorCode.COMMON_INVALID_JSON)

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException) =
        respond(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(e: NoResourceFoundException) = respond(CommonErrorCode.COMMON_NOT_FOUND)

    // @PreAuthorize 등 메서드 보안 예외가 500으로 떨어지지 않도록
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException) = respond(CommonErrorCode.AUTH_FORBIDDEN)

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(e: AuthenticationException) = respond(CommonErrorCode.AUTH_UNAUTHORIZED)

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        // 위에서 다루지 않은 Spring MVC 예외(필수 파라미터 누락, 미지원 Content-Type 등)는 상태 코드로 매핑
        if (e is ErrorResponse && e.statusCode.is4xxClientError) {
            val errorCode = CommonErrorCode.entries.firstOrNull { it.status.value() == e.statusCode.value() }
                ?: CommonErrorCode.COMMON_INVALID_INPUT
            return respond(errorCode)
        }
        log.error("Unhandled exception", e)
        return respond(CommonErrorCode.COMMON_INTERNAL_ERROR)
    }

    private fun respond(
        errorCode: ErrorCode,
        message: String = errorCode.message,
        errors: List<ErrorDetail> = emptyList(),
    ) = ResponseEntity.status(errorCode.status).body(ApiResponse.error(errorCode, message, errors))

    companion object {
        private const val INVALID_VALUE = "올바르지 않은 값입니다."
    }
}
