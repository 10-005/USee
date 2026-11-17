package com.ssafy.animal.chat.adapter.`in`.stomp

import com.ssafy.animal.chat.TestTokens
import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import tools.jackson.databind.json.JsonMapper
import java.security.Principal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ChatStompInterceptorTest {

    private val chatUseCase = mock(ChatUseCase::class.java)
    private val commonInterceptor = StompAuthChannelInterceptor(JwtVerifier(TestTokens.publicKeyBase64))
    private val interceptor = ChatStompInterceptor(chatUseCase, JsonMapper())
    private val channel = mock(MessageChannel::class.java)
    private val user = AuthUser(17, Role.USER, null)
    private val principal = ChatPrincipal(user, null)

    private fun frame(command: StompCommand, destination: String? = null, user: Principal? = principal, bearer: String? = null): Message<ByteArray> {
        val accessor = StompHeaderAccessor.create(command)
        destination?.let { accessor.destination = it }
        bearer?.let { accessor.addNativeHeader("Authorization", "Bearer $it") }
        accessor.user = user
        accessor.setLeaveMutable(true)
        return MessageBuilder.createMessage(ByteArray(0), accessor.messageHeaders)
    }

    private fun preSend(message: Message<*>) = interceptor.preSend(message, channel)

    private fun assertRejected(code: ErrorCode, message: Message<*>) =
        assertEquals(code, assertFailsWith<BusinessException> { preSend(message) }.errorCode)

    @Test
    fun `CONNECT는 common 인증 뒤 주체를 userSeq 이름과 만료 시각을 가진 ChatPrincipal로 바꾼다`() {
        val exp = Instant.now().plusSeconds(1800).epochSecond
        val connect = frame(StompCommand.CONNECT, user = null, bearer = TestTokens.access(17, expEpochSecond = exp))
        val result = preSend(commonInterceptor.preSend(connect, channel))

        val chatPrincipal = assertIs<ChatPrincipal>(StompHeaderAccessor.wrap(result).user)
        assertEquals("17", chatPrincipal.name)
        assertEquals(user, chatPrincipal.authUser)
        assertEquals(Instant.ofEpochSecond(exp), chatPrincipal.expiresAt)
    }

    @Test
    fun `SUBSCRIBE 방 토픽은 참여자만, 아니면 CHAT_ROOM_NOT_PARTICIPANT`() {
        given(chatUseCase.isParticipant(user, 501)).willReturn(true)
        preSend(frame(StompCommand.SUBSCRIBE, "/topic/rooms/501"))
        assertRejected(ChatErrorCode.CHAT_ROOM_NOT_PARTICIPANT, frame(StompCommand.SUBSCRIBE, "/topic/rooms/502"))
    }

    @Test
    fun `SUBSCRIBE 본인 큐는 검사 없이 통과, 그 밖의 목적지는 거부`() {
        preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/errors"))
        preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/room-updates"))
        assertRejected(CommonErrorCode.AUTH_FORBIDDEN, frame(StompCommand.SUBSCRIBE, "/topic/rooms/abc"))
        assertRejected(CommonErrorCode.AUTH_FORBIDDEN, frame(StompCommand.SUBSCRIBE, "/queue/other"))
    }

    @Test
    fun `SEND는 app 목적지만, 브로커 목적지로 직접 보내면 거부`() {
        preSend(frame(StompCommand.SEND, "/app/rooms/501/messages"))
        assertRejected(CommonErrorCode.AUTH_FORBIDDEN, frame(StompCommand.SEND, "/topic/rooms/501"))
    }
}
