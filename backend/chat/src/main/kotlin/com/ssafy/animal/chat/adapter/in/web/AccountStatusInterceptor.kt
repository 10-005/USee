package com.ssafy.animal.chat.adapter.`in`.web

import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.common.auth.AuthUser
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
 * 명세 1.1 Q433: 인증된 REST 쓰기(POST·PATCH·DELETE)만 처리 전에 users를 다시 읽는다(core-api AccountStatusInterceptor와 같은 규칙).
 * STOMP SEND는 ChatStompController가 같은 확인(ChatUseCase.requireWritableAccount)을 한다.
 */
class AccountStatusInterceptor(
    private val chatUseCase: ChatUseCase,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod || request.method !in WRITE_METHODS) return true
        val authUser = SecurityContextHolder.getContext().authentication?.principal as? AuthUser ?: return true
        chatUseCase.requireWritableAccount(authUser.userSeq)
        return true
    }

    companion object {
        private val WRITE_METHODS = setOf("POST", "PATCH", "DELETE")
    }
}

/** @Bean으로 내서 @WebMvcTest 슬라이스가 자동으로 집어 가지 않게 한다(core-api AccountStatusConfig와 같은 방식) */
@Configuration(proxyBeanMethods = false)
class AccountStatusConfig {
    @Bean
    fun accountStatusWebMvcConfigurer(chatUseCase: ChatUseCase) = object : WebMvcConfigurer {
        override fun addInterceptors(registry: InterceptorRegistry) {
            registry.addInterceptor(AccountStatusInterceptor(chatUseCase))
        }
    }
}
