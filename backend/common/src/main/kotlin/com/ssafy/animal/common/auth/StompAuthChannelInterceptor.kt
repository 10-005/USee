package com.ssafy.animal.common.auth

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor

/** STOMP CONNECT 프레임의 Authorization 헤더로 인증한다. chat의 clientInboundChannel에 등록해서 사용. */
class StompAuthChannelInterceptor(
    private val jwtVerifier: JwtVerifier,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        if (accessor?.command == StompCommand.CONNECT) {
            val token = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)
                ?.takeIf { it.startsWith(JwtAuthenticationFilter.BEARER_PREFIX) }
                ?.substring(JwtAuthenticationFilter.BEARER_PREFIX.length)
                ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)
            accessor.user = jwtVerifier.verify(token).toAuthentication()
        }
        return message
    }
}
