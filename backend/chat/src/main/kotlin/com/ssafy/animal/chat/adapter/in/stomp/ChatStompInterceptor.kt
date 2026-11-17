package com.ssafy.animal.chat.adapter.`in`.stomp

import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import org.springframework.http.HttpHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.security.Principal
import java.time.Instant
import java.util.Base64

/**
 * STOMP 세션 주체. user destination 이름(getName)을 userSeq로 맞추고, SEND 때 만료를 보려고 토큰 만료 시각을 들고 있다(Q454).
 */
class ChatPrincipal(val authUser: AuthUser, val expiresAt: Instant?) : Principal {
    override fun getName() = authUser.userSeq.toString()
}

/**
 * common StompAuthChannelInterceptor(CONNECT 인증) 다음에 돈다.
 * - CONNECT: 주체를 ChatPrincipal로 바꾼다(서명 검증은 common이 끝냈으므로 만료 클레임만 읽는다)
 * - SUBSCRIBE: /topic/rooms/{id}는 참여자만(CHAT_ROOM_NOT_PARTICIPANT), 본인 큐 2개는 통과, 그 밖은 거부
 * - SEND: /app/ 아래만. 브로커 목적지(/topic·/queue)로 직접 보내 위조 메시지를 뿌리는 것을 막는다
 */
@Component
class ChatStompInterceptor(
    private val chatUseCase: ChatUseCase,
    private val jsonMapper: JsonMapper,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: return message
        when (accessor.command) {
            StompCommand.CONNECT -> {
                val authUser = (accessor.user as? Authentication)?.principal as? AuthUser
                    ?: throw BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED)
                accessor.user = ChatPrincipal(authUser, expiresAt(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)))
            }
            StompCommand.SUBSCRIBE -> authorizeSubscribe(accessor)
            StompCommand.SEND -> if (accessor.destination?.startsWith(APP_PREFIX) != true) {
                throw BusinessException(CommonErrorCode.AUTH_FORBIDDEN)
            }
            else -> Unit
        }
        return message
    }

    private fun authorizeSubscribe(accessor: StompHeaderAccessor) {
        val destination = accessor.destination
        if (destination in USER_QUEUES) return
        val roomSeq = destination?.let { ROOM_TOPIC.matchEntire(it) }?.groupValues?.get(1)?.toLongOrNull()
            ?: throw BusinessException(CommonErrorCode.AUTH_FORBIDDEN)
        val principal = accessor.user as? ChatPrincipal ?: throw BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED)
        if (!chatUseCase.isParticipant(principal.authUser, roomSeq)) {
            throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_PARTICIPANT)
        }
    }

    /** JWT payload의 exp(초). 읽지 못하면 null(만료 확인 생략, 다음 CONNECT에서 다시 검증) */
    private fun expiresAt(authorization: String?): Instant? = runCatching {
        val payload = authorization!!.removePrefix(BEARER).split('.')[1]
        val exp = jsonMapper.readTree(Base64.getUrlDecoder().decode(payload)).get("exp").asLong()
        Instant.ofEpochSecond(exp)
    }.getOrNull()

    companion object {
        const val APP_PREFIX = "/app/"
        private const val BEARER = "Bearer "
        private val USER_QUEUES = setOf("/user/queue/errors", "/user/queue/room-updates")
        private val ROOM_TOPIC = Regex("^/topic/rooms/(\\d+)$")
    }
}
