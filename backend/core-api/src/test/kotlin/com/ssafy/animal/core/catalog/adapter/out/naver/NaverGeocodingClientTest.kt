package com.ssafy.animal.core.catalog.adapter.out.naver

import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import org.hamcrest.Matchers.startsWith

class NaverGeocodingClientTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = NaverGeocodingClient(NaverMapProperties("id", "secret"), builder)

    @Test
    fun `주소 검색은 키 헤더를 붙이고 x·y를 경도·위도로 옮긴다`() {
        server.expect(requestTo(startsWith("https://maps.apigw.ntruss.com/map-geocode/v2/geocode")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("query", "%EA%B5%AC%EB%AF%B8%EC%8B%9C"))
            .andExpect(header("x-ncp-apigw-api-key-id", "id"))
            .andExpect(header("x-ncp-apigw-api-key", "secret"))
            .andRespond(
                withSuccess(
                    """{"status":"OK","meta":{"totalCount":1},"addresses":[{"roadAddress":"경상북도 구미시 송정대로 55","jibunAddress":"","x":"128.3445","y":"36.1195"}],"errorMessage":""}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(listOf(GeocodeCandidate("경상북도 구미시 송정대로 55", null, 36.1195, 128.3445)), client.search("구미시"))
    }

    @Test
    fun `역지오코딩은 legalcode의 10자리 코드를 돌려준다`() {
        server.expect(requestTo(startsWith("https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc")))
            .andExpect(queryParam("coords", "128.3445,36.1195"))
            .andExpect(queryParam("orders", "legalcode"))
            .andRespond(
                withSuccess(
                    """{"status":{"code":0,"name":"ok","message":"done"},"results":[{"name":"legalcode","code":{"id":"4719012500","type":"L","mappingId":"05190125"}}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals("4719012500", client.reverseLegalCode(36.1195, 128.3445))
    }

    @Test
    fun `역지오코딩 결과 없음(code 3)은 null`() {
        server.expect(requestTo(startsWith("https://maps.apigw.ntruss.com/map-reversegeocode/v2/gc")))
            .andRespond(withSuccess("""{"status":{"code":3,"name":"no results","message":"no results"},"results":[]}""", MediaType.APPLICATION_JSON))

        assertNull(client.reverseLegalCode(0.0, 0.0))
    }

    @Test
    fun `네이버 인증 오류와 키 미설정은 예외`() {
        server.expect(requestTo(startsWith("https://maps.apigw.ntruss.com/map-geocode/v2/geocode"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED))
        assertFails { client.search("구미시") }

        assertFails { NaverGeocodingClient(NaverMapProperties(), RestClient.builder()).search("구미시") }
    }
}
