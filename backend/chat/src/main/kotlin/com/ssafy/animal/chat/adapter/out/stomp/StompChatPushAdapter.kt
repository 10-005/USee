package com.ssafy.animal.chat.adapter.out.stomp

import com.ssafy.animal.chat.application.port.`in`.ChatMessageView
import com.ssafy.animal.chat.application.port.`in`.RoomUpdate
import com.ssafy.animal.chat.application.port.out.ChatPushPort
import org.springframework.context.annotation.Lazy
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component

/**
 * user destination 이름은 ChatPrincipal.name = userSeq.
 * 템플릿은 @Lazy: WebSocketConfig → ChatStompInterceptor → ChatService → 이 어댑터 → 템플릿(WebSocketConfig) 순환을 끊는다
 */
@Component
class StompChatPushAdapter(
    @Lazy private val messagingTemplate: SimpMessagingTemplate,
) : ChatPushPort {

    override fun broadcast(chatRoomSeq: Long, message: ChatMessageView) =
        messagingTemplate.convertAndSend("/topic/rooms/$chatRoomSeq", message)

    override fun sendRoomUpdate(userSeq: Long, update: RoomUpdate) =
        messagingTemplate.convertAndSendToUser(userSeq.toString(), "/queue/room-updates", update)
}
