package com.ssafy.animal.chat.adapter.`in`.stomp

import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.application.port.`in`.SendMessageCommand
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.messaging.converter.MessageConversionException
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal
import java.time.Instant
import java.util.UUID

/**
 * 14.5 STOMP SEND /app/rooms/{chatRoomSeq}/messages(R16 B). 브로드캐스트·room-updates·이벤트는 유스케이스가 커밋 후 처리.
 * 실패는 보낸 세션의 /user/queue/errors로 HTTP와 같은 봉투, data에 clientMessageId(14.5 [초안]).
 */
@Controller
class ChatStompController(
    private val chatUseCase: ChatUseCase,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // principal을 nullable로 두면 Spring이 Optional로 감싸 넘겨 호출이 실패한다(Kotlin 선택 인자 취급)
    @MessageMapping("/rooms/{chatRoomSeq}/messages")
    fun send(@DestinationVariable chatRoomSeq: Long, @Payload request: SendMessageRequest, principal: Principal) {
        try {
            val chatPrincipal = principal as? ChatPrincipal ?: throw BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED)
            // Q454: STOMP는 CONNECT 때만 인증하므로 SEND마다 토큰 만료를 본다(Q433 쓰기 재확인과 같은 위치)
            if (chatPrincipal.expiresAt?.isBefore(Instant.now()) == true) throw BusinessException(CommonErrorCode.AUTH_TOKEN_EXPIRED)
            chatUseCase.sendMessage(chatPrincipal.authUser, chatRoomSeq, request.toCommand())
        } catch (e: BusinessException) {
            throw ChatSendException(e.errorCode, request.clientMessageId)
        }
    }

    @MessageExceptionHandler(ChatSendException::class)
    @SendToUser(destinations = [ERRORS], broadcast = false)
    fun handleSend(e: ChatSendException) = errorBody(e.errorCode, e.clientMessageId)

    /** 본문을 읽을 수 없음(JSON 오류 등) */
    @MessageExceptionHandler(MessageConversionException::class)
    @SendToUser(destinations = [ERRORS], broadcast = false)
    fun handleConversion(e: MessageConversionException) = errorBody(ChatErrorCode.CHAT_INVALID_PAYLOAD, null)

    @MessageExceptionHandler(Exception::class)
    @SendToUser(destinations = [ERRORS], broadcast = false)
    fun handleUnexpected(e: Exception): ApiResponse<Map<String, String>> {
        log.error("[chat] STOMP 처리 실패", e)
        return errorBody(CommonErrorCode.COMMON_INTERNAL_ERROR, null)
    }

    private fun errorBody(errorCode: ErrorCode, clientMessageId: String?) = ApiResponse(
        status = errorCode.status.value(),
        code = errorCode.name,
        message = errorCode.message,
        data = clientMessageId?.let { mapOf("clientMessageId" to it) },
        errors = emptyList(),
        traceId = UUID.randomUUID().toString().replace("-", ""),
    )

    companion object {
        const val ERRORS = "/queue/errors"
    }
}

class ChatSendException(val errorCode: ErrorCode, val clientMessageId: String?) : RuntimeException(errorCode.name)

data class SendMessageRequest(
    val clientMessageId: String? = null,
    val type: String? = null,
    val content: String? = null,
    val imageSeq: Long? = null,
    val sharedTargetType: String? = null,
    val sharedTargetSeq: Long? = null,
) {
    fun toCommand() = SendMessageCommand(clientMessageId, type, content, imageSeq, sharedTargetType, sharedTargetSeq)
}
