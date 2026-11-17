package com.ssafy.animal.core.catalog.adapter.out.naver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ssafy.animal.common.web.OutboundLogInterceptor
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.out.GeocodingClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** NCP Maps 키(비밀값). 비어 있으면 앱은 뜨고 검색은 503, 역지오코딩은 districtCode=null(Q406-2) */
@ConfigurationProperties("naver.map")
data class NaverMapProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val apiBaseUrl: String = "https://maps.apigw.ntruss.com",
)

/**
 * 네이버 클라우드 Maps Geocoding(5.6)·Reverse Geocoding(Q406). 실패는 예외로 던지고
 * 503/null 변환은 GeocodeService가 한다.
 */
@Component
@EnableConfigurationProperties(NaverMapProperties::class)
class NaverGeocodingClient(
    private val properties: NaverMapProperties,
    restClientBuilder: RestClient.Builder,
) : GeocodingClient {

    @Autowired
    constructor(properties: NaverMapProperties) : this(
        properties,
        RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build()).apply { setReadTimeout(TIMEOUT) },
        ),
    )

    private val restClient = restClientBuilder
        .baseUrl(properties.apiBaseUrl)
        .requestInterceptor(OutboundLogInterceptor())
        .defaultHeader("x-ncp-apigw-api-key-id", properties.clientId)
        .defaultHeader("x-ncp-apigw-api-key", properties.clientSecret)
        .build()

    override fun search(query: String): List<GeocodeCandidate> {
        requireConfigured()
        val response = restClient.get()
            .uri { it.path("/map-geocode/v2/geocode").queryParam("query", query).build() }
            .retrieve()
            .body(GeocodeResponse::class.java)
        check(response?.status == "OK") { "네이버 Geocoding 오류: ${response?.status} ${response?.errorMessage}" }
        return response.addresses.orEmpty().map {
            GeocodeCandidate(
                roadAddress = it.roadAddress?.ifBlank { null },
                jibunAddress = it.jibunAddress?.ifBlank { null },
                latitude = it.y.toDouble(),
                longitude = it.x.toDouble(),
            )
        }
    }

    override fun reverseLegalCode(latitude: Double, longitude: Double): String? {
        requireConfigured()
        val response = restClient.get()
            .uri {
                it.path("/map-reversegeocode/v2/gc")
                    .queryParam("coords", "$longitude,$latitude")
                    .queryParam("orders", "legalcode")
                    .queryParam("output", "json")
                    .build()
            }
            .retrieve()
            .body(ReverseResponse::class.java)
        // status.code 0=정상, 3=결과 없음(바다·국외 등)
        return when (response?.status?.code) {
            0 -> response.results.orEmpty().firstOrNull { it.name == "legalcode" }?.code?.id
            3 -> null
            else -> error("네이버 Reverse Geocoding 오류: ${response?.status}")
        }
    }

    private fun requireConfigured() = check(properties.clientId.isNotBlank()) { "NAVER_MAP_CLIENT_ID 미설정" }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class GeocodeResponse(val status: String?, val errorMessage: String?, val addresses: List<Address>?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Address(val roadAddress: String?, val jibunAddress: String?, val x: String, val y: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ReverseResponse(val status: ReverseStatus?, val results: List<ReverseResult>?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ReverseStatus(val code: Int?, val name: String?, val message: String?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ReverseResult(val name: String?, val code: ReverseCode?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class ReverseCode(val id: String?)

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
