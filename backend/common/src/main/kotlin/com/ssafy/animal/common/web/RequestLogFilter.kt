package com.ssafy.animal.common.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtAuthenticationFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

/**
 * 요청이 끝날 때 한 줄: 누가 어디서(source) 이 서버의 어디로(destination) 무엇을 요청해 어떻게 끝났는지 (S15P31D106-423).
 * dev는 JSON(logstash) 필드로, 로컬은 메시지 한 줄로 보인다. 서비스명·Pod명은 모든 줄에 붙는다(common.yml).
 *
 * - 클라이언트 IP는 Traefik의 X-Forwarded-For(server.forward-headers-strategy)로 바뀐 진짜 값.
 *   클라이언트 포트는 Traefik이 넘기지 않아 알 수 없으므로 남기지 않는다
 * - 본문·Authorization·쿠키는 남기지 않고, 쿼리 파라미터는 개인정보 키를 가린다([maskQuery])
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // TraceIdFilter 다음: traceId가 MDC에 있을 때 기록한다
class RequestLogFilter : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("http.access")

    override fun shouldNotFilter(request: HttpServletRequest) = request.requestURI.startsWith("/actuator")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val start = System.nanoTime()
        var failed = false
        try {
            chain.doFilter(request, response)
        } catch (e: Exception) {
            failed = true
            throw e
        } finally {
            write(request, if (failed) 500 else response.status, (System.nanoTime() - start) / 1_000_000)
        }
    }

    private fun write(request: HttpServletRequest, status: Int, durationMs: Long) {
        val user = request.getAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) as? AuthUser
        val query = maskQuery(request.queryString)
        val source = mapOf(
            "ip" to request.remoteAddr,
            "userSeq" to user?.userSeq,
            "role" to user?.role?.name,
            "method" to request.method,
            "protocol" to request.protocol,
            "url" to request.requestURI,
            "query" to query,
        )
        val destination = mapOf(
            "ip" to request.localAddr,
            "port" to request.localPort,
            "route" to request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),
        )
        log.atLevel(if (status >= 500) Level.WARN else Level.INFO)
            .addKeyValue("source", source)
            .addKeyValue("destination", destination)
            .addKeyValue("status", status)
            .addKeyValue("durationMs", durationMs)
            .log("{} {}{} {} {}ms", request.method, request.requestURI, query?.let { "?$it" }.orEmpty(), status, durationMs)
    }

    companion object {
        private const val MAX_QUERY_LENGTH = 300
        private const val MAX_VALUE_LENGTH = 50

        // 연락처·주소·좌표·계정 정보가 담길 수 있는 파라미터. 키 이름(소문자)에 포함되면 값을 가린다
        private val SENSITIVE_PARTS = listOf("phone", "contact", "address", "email", "token", "password", "secret")
        // 주소 검색어(query), 닉네임 중복 확인, 좌표
        private val SENSITIVE_KEYS = setOf("query", "nickname", "realname", "lat", "lng", "lon", "latitude", "longitude", "x", "y")

        /** 개인정보 키의 값은 `***`, 나머지 값은 50자, 전체는 300자까지. 인코딩된 원문 그대로 다룬다 */
        fun maskQuery(query: String?): String? {
            if (query.isNullOrEmpty()) return null
            val masked = query.split('&').joinToString("&") { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                val lower = key.lowercase()
                when {
                    '=' !in pair -> pair
                    lower in SENSITIVE_KEYS || SENSITIVE_PARTS.any { it in lower } -> "$key=***"
                    else -> "$key=${value.take(MAX_VALUE_LENGTH)}"
                }
            }
            return masked.take(MAX_QUERY_LENGTH)
        }
    }
}
