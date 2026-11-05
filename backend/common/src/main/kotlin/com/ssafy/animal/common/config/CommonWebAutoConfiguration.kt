package com.ssafy.animal.common.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtAuthenticationFilter
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.SecurityErrorResponder
import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.LoggingEventAdapter
import com.ssafy.animal.common.exception.GlobalExceptionHandler
import com.ssafy.animal.common.response.ApiResponseBodyAdvice
import com.ssafy.animal.common.web.TraceIdFilter
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
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

    @Bean
    @ConditionalOnMissingBean
    fun eventPublisher(): EventPublisher = LoggingEventAdapter()

    // Swagger 화면의 Authorize 버튼으로 JWT를 넣어 인증이 필요한 API를 호출할 수 있게 한다
    @Bean
    @ConditionalOnMissingBean
    fun openApi(): OpenAPI = OpenAPI()
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
        private val PUBLIC_PATHS = arrayOf(
            // 문서 경로: /api/{서비스}/v3/api-docs(swagger-config 포함), /swagger-ui(화면·정적 리소스)
            "/actuator/health/**", "/api/*/v3/api-docs/**", "/swagger-ui/**", "/error",
        )
    }
}
