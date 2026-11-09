package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.notification.application.port.out.UserStatusReader
import com.ssafy.animal.notification.domain.AccountErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 명세 1.1 Q433. core-api AccountStatusInterceptor(73)와 같은 규칙을 별도 앱인 notification에도 건다:
 * 인증된 쓰기 요청(POST·PATCH·DELETE)만 처리 전에 core.users를 다시 읽어 탈퇴 401 AUTH_TOKEN_INVALID, 정지 403 AUTH_ACCOUNT_UNAVAILABLE.
 * 조회(GET)는 재확인하지 않는다. notification에는 STOMP가 없다.
 */
class AccountStatusInterceptor(
    private val userStatusReader: UserStatusReader,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod || request.method !in WRITE_METHODS) return true
        val authUser = SecurityContextHolder.getContext().authentication?.principal as? AuthUser ?: return true

        // ponytail: 쓰기마다 PK 조회 1회, 캐시 없음(core-api와 같음)
        val status = userStatusReader.find(authUser.userSeq)
        if (status == null || status.deleted) throw BusinessException(CommonErrorCode.AUTH_TOKEN_INVALID)
        if (status.accountStatus != ACTIVE) throw BusinessException(AccountErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
        return true
    }

    companion object {
        private val WRITE_METHODS = setOf("POST", "PATCH", "DELETE")
        private const val ACTIVE = "ACTIVE"
    }
}

/** WebMvcConfigurer를 @Bean으로 내서 @WebMvcTest 슬라이스가 자동으로 집어 가지 않게 한다(core-api 선례와 같음) */
@Configuration(proxyBeanMethods = false)
class AccountStatusConfig {
    @Bean
    fun accountStatusWebMvcConfigurer(userStatusReader: UserStatusReader) = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(AccountStatusInterceptor(userStatusReader))
        }
    }
}
