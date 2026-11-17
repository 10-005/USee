package com.ssafy.animal.core.auth.adapter.out.kakao

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.web.OutboundLogInterceptor
import com.ssafy.animal.core.auth.application.port.out.KakaoClient
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.http.HttpClient
import java.time.Duration

/** 앱 ID는 비밀값이 아니다(Kakao Developers > 앱 설정 > 일반). 다른 앱에서 받은 토큰을 거르는 데 쓴다. */
@ConfigurationProperties("kakao")
data class KakaoProperties(
    val appId: Long,
    val apiBaseUrl: String = "https://kapi.kakao.com",
)

/**
 * 앱이 카카오 SDK로 받은 access token을 토큰 정보 API로 검증한다(명세 Q413 A, 인가코드 교환 없음).
 * 응답의 id(앱별 회원번호)와 app_id를 함께 받으므로 사용자 조회(/v2/user/me) 없이 한 번에 끝난다. 프로필 정보는 받지 않는다(Q408-1).
 */
@Component
@EnableConfigurationProperties(KakaoProperties::class)
class KakaoApiAdapter(
    private val properties: KakaoProperties,
    restClientBuilder: RestClient.Builder,
) : KakaoClient {

    @Autowired
    constructor(properties: KakaoProperties) : this(
        properties,
        RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build()).apply { setReadTimeout(TIMEOUT) },
        ),
    )

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.baseUrl(properties.apiBaseUrl).requestInterceptor(OutboundLogInterceptor()).build()

    override fun fetchKakaoId(kakaoAccessToken: String): String {
        val info = try {
            restClient.get()
                .uri("/v1/user/access_token_info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $kakaoAccessToken")
                .retrieve()
                .body(TokenInfo::class.java)
        } catch (e: HttpClientErrorException) {
            // 401(-401 만료·무효), 400(-2 형식 오류) 등 4xx는 모두 클라이언트 토큰 문제
            throw BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN)
        } catch (e: RestClientException) {
            log.warn("카카오 토큰 정보 조회 실패", e)
            throw BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)
        }
        if (info?.id == null || info.appId != properties.appId) {
            throw BusinessException(AuthErrorCode.AUTH_INVALID_KAKAO_TOKEN)
        }
        return info.id.toString()
    }

    private data class TokenInfo(
        val id: Long?,
        @com.fasterxml.jackson.annotation.JsonProperty("app_id")
        val appId: Long?,
    )

    companion object {
        private val TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
