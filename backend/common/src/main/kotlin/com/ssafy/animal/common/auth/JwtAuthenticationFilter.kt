package com.ssafy.animal.common.auth

import com.ssafy.animal.common.exception.BusinessException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Bearer 토큰을 검증해 SecurityContext를 채운다. 실패해도 바로 응답하지 않고 사유만 남긴다:
 * 인증이 필요한 경로라면 SecurityErrorResponder가 그 사유(만료/위조)로 401을 낸다.
 */
class JwtAuthenticationFilter(
    private val jwtVerifier: JwtVerifier,
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.substring(BEARER_PREFIX.length)
        if (token != null) {
            try {
                val user = jwtVerifier.verify(token)
                SecurityContextHolder.getContext().authentication = user.toAuthentication()
                // 요청 로그(RequestLogFilter)가 보안 필터 밖에서 요청자를 기록할 수 있게 남긴다
                request.setAttribute(AUTH_USER_ATTRIBUTE, user)
            } catch (e: BusinessException) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.errorCode)
            }
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val BEARER_PREFIX = "Bearer "
        const val AUTH_ERROR_ATTRIBUTE = "com.ssafy.animal.common.auth.AUTH_ERROR"
        const val AUTH_USER_ATTRIBUTE = "com.ssafy.animal.common.auth.AUTH_USER"
    }
}
