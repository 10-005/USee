package com.ssafy.animal.common.response

import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import tools.jackson.databind.json.JsonMapper

/**
 * 컨트롤러가 반환한 DTO를 ApiResponse로 감싼다. 우리 패키지의 컨트롤러에만 적용(actuator, springdoc 제외).
 */
@RestControllerAdvice(basePackages = ["com.ssafy.animal"])
class ApiResponseBodyAdvice(
    private val jsonMapper: JsonMapper,
) : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out HttpMessageConverter<*>>) =
        AbstractJacksonHttpMessageConverter::class.java.isAssignableFrom(converterType) ||
            StringHttpMessageConverter::class.java.isAssignableFrom(converterType)

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        val status = (response as? ServletServerHttpResponse)?.servletResponse?.status ?: 200
        val wrapped = if (body is ApiResponse<*>) {
            body.copy(status = status)
        } else {
            val message = returnType.getMethodAnnotation(SuccessMessage::class.java)?.value
                ?: ApiResponse.DEFAULT_SUCCESS_MESSAGE
            ApiResponse.success(status, body, message)
        }
        // String 반환 시 StringHttpMessageConverter가 선택되므로 직접 직렬화
        if (StringHttpMessageConverter::class.java.isAssignableFrom(selectedConverterType)) {
            response.headers.contentType = MediaType.APPLICATION_JSON
            return jsonMapper.writeValueAsString(wrapped)
        }
        return wrapped
    }
}
