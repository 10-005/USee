package com.ssafy.animal.chat.application.port.out

import com.ssafy.animal.chat.application.port.`in`.ChatMessageView
import com.ssafy.animal.chat.application.port.`in`.RoomUpdate

/** STOMP 실시간 전송(14장 머리말). 커밋 후에만 부른다 */
interface ChatPushPort {
    /** /topic/rooms/{chatRoomSeq} */
    fun broadcast(chatRoomSeq: Long, message: ChatMessageView)

    /** /user/{userSeq}/queue/room-updates */
    fun sendRoomUpdate(userSeq: Long, update: RoomUpdate)
}
