package com.ssafy.animal.common.auth

import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import tools.jackson.databind.json.JsonMapper

/** Spring Security의 401/403을 공통 응답 형식으로 내보낸다. */
class SecurityErrorResponder(
    private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException) {
        val errorCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) as? ErrorCode
            ?: ErrorCode.AUTH_UNAUTHORIZED
        write(response, errorCode)
    }

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, e: AccessDeniedException) =
        write(response, ErrorCode.AUTH_FORBIDDEN)

    private fun write(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.outputStream, ApiResponse.error(errorCode))
    }
}
