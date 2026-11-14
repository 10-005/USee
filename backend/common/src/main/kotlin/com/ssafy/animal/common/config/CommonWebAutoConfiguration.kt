package com.ssafy.animal.common.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtAuthenticationFilter
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.SecurityErrorResponder
import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
import com.ssafy.animal.common.exception.GlobalExceptionHandler
import com.ssafy.animal.common.response.ApiResponseBodyAdvice
import com.ssafy.animal.common.web.TraceIdFilter
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import java.util.TimeZone

/**
 * common 의존성만 추가하면 공통 응답, 예외 처리, TraceId, JWT 보안, 직렬화 규칙이 적용된다.
 * 서비스에서 같은 타입의 빈을 정의하면 그쪽이 우선한다.
 */
@AutoConfiguration(
    before = [
        ServletWebSecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@Import(GlobalExceptionHandler::class, ApiResponseBodyAdvice::class)
class CommonWebAutoConfiguration {

    @Bean
    fun traceIdFilter() = TraceIdFilter()

    @Bean
    fun commonJsonMapperCustomizer() = JsonMapperBuilderCustomizer { builder ->
        builder
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .defaultTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.ALWAYS) }
    }

    @Bean
    @ConditionalOnMissingBean
    fun jwtVerifier(@Value("\${jwt.public-key}") publicKey: String) = JwtVerifier(publicKey)

    @Bean
    fun securityErrorResponder(jsonMapper: JsonMapper) = SecurityErrorResponder(jsonMapper)

    @Bean
    @ConditionalOnMissingBean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtVerifier: JwtVerifier,
        securityErrorResponder: SecurityErrorResponder,
        @Value("\${security.permit-paths:}") permitPaths: Array<String>,
    ): SecurityFilterChain = http
        .csrf { it.disable() }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .logout { it.disable() }
        .cors { } // corsConfigurationSource 빈 사용. 인증보다 먼저 처리되어 preflight는 토큰 없이 통과
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .exceptionHandling {
            it.authenticationEntryPoint(securityErrorResponder).accessDeniedHandler(securityErrorResponder)
        }
        .authorizeHttpRequests {
            it.requestMatchers(*PUBLIC_PATHS).permitAll()
            val extra = permitPaths.filter(String::isNotBlank)
            if (extra.isNotEmpty()) it.requestMatchers(*extra.toTypedArray()).permitAll()
            it.anyRequest().authenticated()
        }
        .addFilterBefore(JwtAuthenticationFilter(jwtVerifier), UsernamePasswordAuthenticationFilter::class.java)
        .build()

    // 웹 개발 서버 등 다른 출처의 브라우저 호출 허용. 토큰은 Authorization 헤더로만 오가므로 쿠키(credentials)는 쓰지 않는다
    // MVC의 HandlerMappingIntrospector도 CorsConfigurationSource 타입이므로 타입이 아닌 이름으로 조건을 건다
    @Bean
    @ConditionalOnMissingBean(name = ["corsConfigurationSource"])
    fun corsConfigurationSource(@Value("\${cors.allowed-origins:}") allowedOrigins: Array<String>): CorsConfigurationSource {
        val origins = allowedOrigins.map(String::trim).filter(String::isNotEmpty)
        require(origins.none { "*" in it }) { "cors.allowed-origins에 와일드카드(*)를 쓸 수 없습니다: $origins" }
        val config = CorsConfiguration().apply {
            this.allowedOrigins = origins
            allowedMethods = listOf("GET", "POST", "PATCH", "DELETE")
            allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, TraceIdFilter.HEADER)
            exposedHeaders = listOf(TraceIdFilter.HEADER)
            allowCredentials = false
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

    // 기본 인메모리 사용자(생성 비밀번호) 등록 방지
    @Bean
    @ConditionalOnMissingBean
    fun userDetailsService() = UserDetailsService { throw UsernameNotFoundException(it) }

    // Swagger 화면의 Authorize 버튼으로 JWT를 넣어 인증이 필요한 API를 호출할 수 있게 한다
    @Bean
    @ConditionalOnMissingBean
    fun openApi(): OpenAPI = OpenAPI()
        .info(Info().title("반려동물 서비스 API").version("v1").description(API_DESCRIPTION))
        .components(
            Components().addSecuritySchemes(
                BEARER_AUTH,
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH))

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.messaging.support.ChannelInterceptor"])
    class StompConfiguration {
        @Bean
        fun stompAuthChannelInterceptor(jwtVerifier: JwtVerifier) = StompAuthChannelInterceptor(jwtVerifier)
    }

    companion object {
        init {
            // default-flat-param-object가 @AuthenticationPrincipal AuthUser까지 쿼리 파라미터로 펼치므로 문서에서 뺀다
            SpringDocUtils.getConfig().addRequestWrapperToIgnore(AuthUser::class.java)
        }

        private const val BEARER_AUTH = "bearerAuth"

        // 모든 API에 공통인 내용은 여기에만 적고 API마다 반복하지 않는다
        private val API_DESCRIPTION = """
            ## 응답 형태
            모든 응답은 같은 봉투로 감싼다. 각 API의 Schema·예시가 이 형태다.
            ```json
            { "status": 200, "code": "SUCCESS", "message": "요청이 성공했습니다.", "data": { }, "errors": [], "traceId": "4f2c9e1a7b3d4c5e" }
            ```
            - `status`: 실제 HTTP 상태 코드와 같다.
            - `code`: 성공은 `SUCCESS`, 실패는 에러 코드(enum 상수명).
            - `data`: 실패면 항상 `null`. 목록은 `{ content, page, size, totalElements, totalPages, hasNext }`.
            - `errors`: 필드 검증 실패(400 `COMMON_INVALID_INPUT`)에서만 `[{ field, reason }]`로 채우고, 그 외에는 빈 배열.
            - `traceId`: 응답 헤더 `X-Trace-Id`와 같다. 요청에 `X-Trace-Id`를 보내면 그 값을 이어 쓴다. 문의할 때 이 값을 전달한다.
            - **204 No Content는 본문(봉투)이 없다.** traceId는 응답 헤더로만 온다.
            - null 필드도 생략하지 않고 `null`로 내려온다. 시각은 ISO-8601 `+09:00`, 날짜는 `YYYY-MM-DD`.

            ## 목록 공통 파라미터
            - `page`: 0부터. 기본 0.
            - `size`: 기본 20, 최대 100(넘으면 100으로 잘린다). 기본값이 다른 API는 API 설명에 적는다.
            - `sort`: `필드,asc|desc`. API마다 허용 필드가 정해져 있고, 밖의 필드는 400 `COMMON_INVALID_INPUT`. 동률은 PK 내림차순.

            ## 공통 에러 코드
            | HTTP | code | 원인 | 클라이언트 처리 |
            |---|---|---|---|
            | 400 | `COMMON_INVALID_INPUT` | 필수값·형식·범위·정렬 필드 오류. `errors[]`에 필드별 사유 | 입력 수정 후 재요청 |
            | 400 | `COMMON_INVALID_JSON` | 본문을 읽을 수 없음(JSON 문법, 타입·날짜 형식 불일치) | 요청 형식 수정 |
            | 401 | `AUTH_UNAUTHORIZED` | `Authorization` 헤더 없음 | 로그인 |
            | 401 | `AUTH_TOKEN_EXPIRED` | access 토큰 만료 | `POST /api/core/v1/auth/refresh` 후 원래 요청 재시도 |
            | 401 | `AUTH_TOKEN_INVALID` | 서명·형식 오류, refresh 토큰을 access로 사용, 탈퇴한 계정의 쓰기 요청 | 재로그인 |
            | 403 | `AUTH_FORBIDDEN` | 역할이 맞지 않음(예: USER가 SHELTER 전용 API 호출) | 재시도하지 않음 |
            | 403 | `AUTH_ACCOUNT_UNAVAILABLE` | 정지된 계정의 쓰기 요청(POST·PATCH·DELETE). 조회는 토큰 만료까지 허용 | 재시도하지 않음 |
            | 404 | `COMMON_NOT_FOUND` | 경로 없음 | 경로 확인 |
            | 405 | `COMMON_METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP 메서드 | 메서드 확인 |
            | 500 | `COMMON_INTERNAL_ERROR` | 서버 내부 오류(상세 메시지 비노출) | traceId와 함께 문의 |
            | 503 | `COMMON_SERVICE_UNAVAILABLE` | 외부 연동 일시 장애 | 잠시 후 재시도 |

            - 타인·비노출 자원은 403이 아니라 도메인별 `*_NOT_FOUND`(404)로 응답해 존재를 감춘다.
            - 도메인 에러 코드는 각 API의 응답 예시에 적는다.

            ## 인증(Authorize)
            1. 로그인 API(`POST /api/core/v1/auth/kakao/verify` 또는 `POST /api/core/v1/auth/local/login`)로 `accessToken`을 받는다.
            2. 오른쪽 위 **Authorize** 버튼을 누르고 `accessToken` 값만 넣는다(`Bearer ` 접두어는 자동으로 붙는다).
            3. 자물쇠가 잠긴 API는 이 토큰으로 호출된다. 🔓 표시가 있는 API는 토큰 없이 호출한다.
            - access 토큰 수명은 1시간(dev는 환경 변수로 더 길 수 있음), refresh 토큰은 14일.
        """.trimIndent()
        private val PUBLIC_PATHS = arrayOf(
            // 문서 경로: /api/{서비스}/v3/api-docs(swagger-config 포함), /swagger-ui(화면·정적 리소스)
            "/actuator/health/**", "/api/*/v3/api-docs/**", "/swagger-ui/**", "/error",
            // 클러스터 안 Prometheus 수집용. Ingress가 /actuator를 넘기지 않아 외부에는 열리지 않는다(S15P31D106-423)
            "/actuator/prometheus",
        )
    }
}
