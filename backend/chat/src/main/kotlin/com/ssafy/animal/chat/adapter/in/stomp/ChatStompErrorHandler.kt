package com.ssafy.animal.chat.adapter.`in`.stomp

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.response.ApiResponse
import org.springframework.messaging.Message
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageBuilder
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler
import tools.jackson.databind.json.JsonMapper

/**
 * 인터셉터에서 거부한 프레임(CONNECT 인증 실패, SUBSCRIBE 비참여자 등)의 ERROR 프레임.
 * message 헤더 = 에러 코드(예: CHAT_ROOM_NOT_PARTICIPANT), 본문 = HTTP와 같은 봉투
 */
class ChatStompErrorHandler(
    private val jsonMapper: JsonMapper,
) : StompSubProtocolErrorHandler() {

    override fun handleClientMessageProcessingError(clientMessage: Message<ByteArray>?, ex: Throwable): Message<ByteArray>? {
        val errorCode = generateSequence(ex) { it.cause }.filterIsInstance<BusinessException>().firstOrNull()?.errorCode
            ?: return super.handleClientMessageProcessingError(clientMessage, ex)
        val accessor = StompHeaderAccessor.create(StompCommand.ERROR).apply {
            message = errorCode.name
            setLeaveMutable(true)
        }
        return MessageBuilder.createMessage(jsonMapper.writeValueAsBytes(ApiResponse.error(errorCode)), accessor.messageHeaders)
    }
}
