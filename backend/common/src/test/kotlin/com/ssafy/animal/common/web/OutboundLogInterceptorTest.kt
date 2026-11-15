package com.ssafy.animal.common.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OutboundLogInterceptorTest {

    private val logger = LoggerFactory.getLogger("http.outbound") as Logger
    private val appender = ListAppender<ILoggingEvent>()
    private val builder = RestClient.builder().baseUrl("https://naveropenapi.example.com")
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = builder.requestInterceptor(OutboundLogInterceptor()).build()

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() = logger.detachAppender(appender).let { }

    private fun ILoggingEvent.kv(key: String): Any? = keyValuePairs.orEmpty().first { it.key == key }.value

    @Test
    fun `외부 호출의 도착지·결과를 남기고 쿼리(주소 등)는 남기지 않는다`() {
        server.expect(requestTo("https://naveropenapi.example.com/map-geocode/v2/geocode?query=%EC%97%AD%EC%82%BC")).andRespond(withSuccess())

        client.get().uri("/map-geocode/v2/geocode?query={q}", "역삼").retrieve().toBodilessEntity()

        val event = appender.list.single()
        assertEquals("INFO", event.level.toString())
        assertEquals(
            mapOf("host" to "naveropenapi.example.com", "port" to 443, "protocol" to "https", "method" to "GET", "path" to "/map-geocode/v2/geocode"),
            event.kv("destination"),
        )
        assertEquals(200, event.kv("status"))
    }

    @Test
    fun `5xx 응답은 WARN으로 남긴다`() {
        server.expect(requestTo("https://naveropenapi.example.com/v1/chat")).andRespond(withStatus(HttpStatus.BAD_GATEWAY))

        assertFailsWith<HttpServerErrorException> { client.post().uri("/v1/chat").retrieve().toBodilessEntity() }

        val event = appender.list.single()
        assertEquals("WARN", event.level.toString())
        assertEquals(502, event.kv("status"))
    }
}
