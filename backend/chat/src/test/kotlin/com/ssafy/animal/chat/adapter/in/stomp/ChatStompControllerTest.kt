package com.ssafy.animal.chat.adapter.`in`.stomp

import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.application.port.`in`.SendMessageCommand
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.simp.annotation.SendToUser
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ChatStompControllerTest {

    private val chatUseCase = mock(ChatUseCase::class.java)
    private val controller = ChatStompController(chatUseCase)
    private val user = AuthUser(17, Role.USER, null)
    private val live = ChatPrincipal(user, Instant.now().plusSeconds(600))
    private val request = SendMessageRequest(clientMessageId = "c-1", type = "TEXT", content = "안녕하세요.")
    private val command = SendMessageCommand("c-1", "TEXT", "안녕하세요.", null, null, null)

    @Test
    fun `14_5 SEND는 CONNECT 주체로 유스케이스에 넘긴다`() {
        controller.send(501, request, live)
        verify(chatUseCase).sendMessage(user, 501, command)
    }

    @Test
    fun `14_5 토큰이 만료됐으면 처리하지 않고 AUTH_TOKEN_EXPIRED`() {
        val expired = ChatPrincipal(user, Instant.now().minusSeconds(1))
        val e = assertFailsWith<ChatSendException> { controller.send(501, request, expired) }
        assertEquals(CommonErrorCode.AUTH_TOKEN_EXPIRED, e.errorCode)
        assertEquals("c-1", e.clientMessageId)
        verifyNoInteractions(chatUseCase)
    }

    @Test
    fun `14_5 도메인 오류는 clientMessageId를 실어 본인 errors 큐로 보낸다`() {
        given(chatUseCase.sendMessage(user, 501, command)).willThrow(BusinessException(ChatErrorCode.CHAT_ROOM_NOT_PARTICIPANT))
        val e = assertFailsWith<ChatSendException> { controller.send(501, request, live) }

        val body = controller.handleSend(e)
        assertEquals(403, body.status)
        assertEquals("CHAT_ROOM_NOT_PARTICIPANT", body.code)
        assertEquals(mapOf("clientMessageId" to "c-1"), body.data)
        assertEquals(emptyList(), body.errors)

        val handler = ChatStompController::class.java.getMethod("handleSend", ChatSendException::class.java)
        val sendToUser = handler.getAnnotation(SendToUser::class.java)
        assertEquals(listOf("/queue/errors"), sendToUser.destinations.toList())
        assertEquals(false, sendToUser.broadcast)
        assertEquals(listOf(ChatSendException::class.java), handler.getAnnotation(MessageExceptionHandler::class.java).value.map { it.java })
    }

    @Test
    fun `14_5 본문을 읽을 수 없으면 CHAT_INVALID_PAYLOAD`() {
        val body = controller.handleConversion(MessageConversionException("bad json"))
        assertEquals(400, body.status)
        assertEquals("CHAT_INVALID_PAYLOAD", body.code)
        assertNull(body.data)
    }
}
