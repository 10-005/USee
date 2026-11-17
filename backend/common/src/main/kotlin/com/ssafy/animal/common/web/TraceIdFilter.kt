package com.ssafy.animal.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** X-Trace-Id를 이어받거나 새로 만들어 MDC와 응답 헤더에 넣는다. 보안 필터보다 먼저 실행된다. */
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val traceId = request.getHeader(HEADER)?.takeIf { VALID_TRACE_ID.matches(it) }
            ?: UUID.randomUUID().toString().replace("-", "")
        MDC.put(MDC_KEY, traceId)
        response.setHeader(HEADER, traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER = "X-Trace-Id"
        const val MDC_KEY = "traceId"

        // 외부 입력이 로그에 그대로 찍히므로 형식 제한
        private val VALID_TRACE_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}
