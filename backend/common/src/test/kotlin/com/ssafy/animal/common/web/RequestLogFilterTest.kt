package com.ssafy.animal.common.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtAuthenticationFilter
import com.ssafy.animal.common.auth.Role
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.boot.logging.logback.StructuredLogEncoder
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestLogFilterTest {

    private val filter = RequestLogFilter()
    private val logger = LoggerFactory.getLogger("http.access") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() = logger.detachAppender(appender).let { }

    private fun request(uri: String = "/api/core/v1/shelters", query: String? = null) = MockHttpServletRequest("GET", uri).apply {
        queryString = query
        remoteAddr = "203.0.113.7"
        localAddr = "10.42.0.15"
        localPort = 8080
        protocol = "HTTP/1.1"
    }

    private fun ILoggingEvent.kv(key: String): Any? = keyValuePairs.orEmpty().first { it.key == key }.value

    @Suppress("UNCHECKED_CAST")
    private fun ILoggingEvent.map(key: String) = kv(key) as Map<String, Any?>

    @Test
    fun `요청이 끝나면 source·destination·status·처리 시간을 한 줄로 남긴다`() {
        val req = request(query = "page=0&keyword=%EB%A7%90%ED%8B%B0%EC%A6%88").apply {
            setAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE, AuthUser(3, Role.USER, null))
        }
        val chain = FilterChain { r, res ->
            r.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/core/v1/shelters")
            (res as MockHttpServletResponse).status = 200
        }

        filter.doFilter(req, MockHttpServletResponse(), chain)

        val event = appender.list.single()
        assertEquals("INFO", event.level.toString())
        assertEquals(
            mapOf(
                "ip" to "203.0.113.7", "userSeq" to 3L, "role" to "USER", "method" to "GET", "protocol" to "HTTP/1.1",
                "url" to "/api/core/v1/shelters", "query" to "page=0&keyword=%EB%A7%90%ED%8B%B0%EC%A6%88",
            ),
            event.map("source"),
        )
        assertEquals(mapOf("ip" to "10.42.0.15", "port" to 8080, "route" to "/api/core/v1/shelters"), event.map("destination"))
        assertEquals(200, event.kv("status"))
        assertTrue(event.formattedMessage.startsWith("GET /api/core/v1/shelters?page=0&keyword="))
    }

    @Test
    fun `토큰 없는 요청은 userSeq가 비고, 예외가 밖으로 나가면 500·WARN으로 남긴다`() {
        assertFailsWith<IllegalStateException> {
            filter.doFilter(request(), MockHttpServletResponse(), FilterChain { _, _ -> error("boom") })
        }

        val event = appender.list.single()
        assertEquals("WARN", event.level.toString())
        assertEquals(500, event.kv("status"))
        assertNull(event.map("source")["userSeq"])
    }

    @Test
    fun `actuator 경로(프로브·메트릭 수집)는 남기지 않는다`() {
        filter.doFilter(request("/actuator/prometheus"), MockHttpServletResponse(), MockFilterChain())

        assertTrue(appender.list.isEmpty())
    }

    @Test
    fun `쿼리의 연락처·주소·좌표·검색 주소는 가리고 긴 값은 자른다`() {
        val masked = RequestLogFilter.maskQuery(
            "contactPhone=01012345678&address=%EC%84%9C%EC%9A%B8&latitude=37.5&query=%EC%97%AD%EC%82%BC&nickname=bori" +
                "&districtCode=11680&keyword=${"a".repeat(80)}&flag",
        )

        assertEquals(
            "contactPhone=***&address=***&latitude=***&query=***&nickname=***&districtCode=11680&keyword=${"a".repeat(50)}&flag",
            masked,
        )
        assertNull(RequestLogFilter.maskQuery(null))
        assertEquals(300, RequestLogFilter.maskQuery("keyword=x&" + "page=1&".repeat(100))!!.length)
    }

    @Test
    fun `dev 로그 형식(logstash JSON)에서 source·destination은 중첩 객체이고 서비스명·Pod명이 붙는다`() {
        filter.doFilter(request(query = "page=1"), MockHttpServletResponse(), MockFilterChain())
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val environment = MockEnvironment()
            .withProperty("logging.structured.json.add.service", "core-api")
            .withProperty("logging.structured.json.add.pod", "core-api-abc")
        context.putObject(Environment::class.java.name, environment)
        val encoder = StructuredLogEncoder().apply {
            this.context = context
            setFormat("logstash")
            start()
        }

        val json = JsonMapper.builder().build().readTree(String(encoder.encode(appender.list.single())))

        assertEquals("GET", json.path("source").path("method").asString())
        assertEquals("page=1", json.path("source").path("query").asString())
        assertEquals(8080, json.path("destination").path("port").asInt())
        assertEquals("http.access", json.path("logger_name").asString())
        assertEquals("core-api", json.path("service").asString())
        assertEquals("core-api-abc", json.path("pod").asString())
    }
}
