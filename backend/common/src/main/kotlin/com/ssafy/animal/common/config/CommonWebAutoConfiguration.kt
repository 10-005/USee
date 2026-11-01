package com.ssafy.animal.common.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.ssafy.animal.common.auth.JwtAuthenticationFilter
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.SecurityErrorResponder
import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.LoggingEventAdapter
import com.ssafy.animal.common.exception.GlobalExceptionHandler
import com.ssafy.animal.common.response.ApiResponseBodyAdvice
import com.ssafy.animal.common.web.TraceIdFilter
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
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

    // 기본 인메모리 사용자(생성 비밀번호) 등록 방지
    @Bean
    @ConditionalOnMissingBean
    fun userDetailsService() = UserDetailsService { throw UsernameNotFoundException(it) }

    @Bean
    @ConditionalOnMissingBean
    fun eventPublisher(): EventPublisher = LoggingEventAdapter()

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.messaging.support.ChannelInterceptor"])
    class StompConfiguration {
        @Bean
        fun stompAuthChannelInterceptor(jwtVerifier: JwtVerifier) = StompAuthChannelInterceptor(jwtVerifier)
    }

    companion object {
        private val PUBLIC_PATHS = arrayOf(
            "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error",
        )
    }
}
