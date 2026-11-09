package com.ssafy.animal.chat.application.port.`in`

import com.ssafy.animal.chat.domain.ChatMessageType
import com.ssafy.animal.chat.domain.ChatRoomType
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.chat.domain.SharedTargetType
import com.ssafy.animal.common.auth.AuthUser
import java.time.OffsetDateTime

/** 명세 14장. REST에서 참여자가 아니면 404 CHAT_ROOM_NOT_FOUND(Q434), STOMP는 403 CHAT_ROOM_NOT_PARTICIPANT */
interface ChatUseCase {
    /** Q433 쓰기 전 계정 재확인. 탈퇴 401 AUTH_TOKEN_INVALID, 정지 403 AUTH_ACCOUNT_UNAVAILABLE */
    fun requireWritableAccount(userSeq: Long)

    /** SUBSCRIBE /topic/rooms/{id} 인가 */
    fun isParticipant(actor: AuthUser, chatRoomSeq: Long): Boolean

    /** 14.1 */
    fun getRooms(actor: AuthUser, unreadOnly: Boolean, beforeLastMessageSeq: Long?, size: Int): ChatRoomPage

    /** 14.2 */
    fun createRoom(actor: AuthUser, command: CreateRoomCommand): CreateRoomResult

    /** 14.3 */
    fun getRoom(actor: AuthUser, chatRoomSeq: Long): ChatRoomDetail

    /** 14.4 */
    fun getMessages(actor: AuthUser, chatRoomSeq: Long, beforeMessageSeq: Long?, size: Int): ChatMessagePage

    /** 14.5 STOMP SEND. 커밋 후 브로드캐스트·room-updates·ChatMessageSent */
    fun sendMessage(actor: AuthUser, chatRoomSeq: Long, command: SendMessageCommand): ChatMessageView

    /** 14.6 */
    fun read(actor: AuthUser, chatRoomSeq: Long, lastReadMessageSeq: Long): ReadResult

    /** 14.7 */
    fun leave(actor: AuthUser, chatRoomSeq: Long): LeaveResult

    /** 14.8 */
    fun getUnreadCount(actor: AuthUser): UnreadCount
}

data class CreateRoomCommand(
    val targetType: ParticipantType,
    val targetUserSeq: Long?,
    val targetShelterSeq: Long?,
)

/** SEND 본문 그대로. 형식 검증은 서비스가 CHAT_INVALID_PAYLOAD로 한다 */
data class SendMessageCommand(
    val clientMessageId: String?,
    val type: String?,
    val content: String?,
    val imageSeq: Long?,
    val sharedTargetType: String?,
    val sharedTargetSeq: Long?,
)

data class ImageRef(val imageSeq: Long, val imageUrl: String?, val displayOrder: Int = 1)

data class Counterpart(
    val type: ParticipantType,
    val userSeq: Long?,
    val shelterSeq: Long?,
    val displayName: String,
    val profileImage: ImageRef?,
)

data class LastMessage(val chatMessageSeq: Long, val messageType: ChatMessageType, val body: String?)

data class ChatRoomItem(
    val chatRoomSeq: Long,
    val roomType: ChatRoomType,
    val counterpart: Counterpart,
    val lastMessage: LastMessage,
    val lastMessageSeq: Long,
    val lastMessageDttm: OffsetDateTime?,
    val unreadCount: Long,
)

data class ChatRoomPage(val content: List<ChatRoomItem>, val nextBeforeLastMessageSeq: Long?, val hasNext: Boolean)

data class CreateRoomResult(val chatRoomSeq: Long, val roomType: ChatRoomType, val reused: Boolean)

data class ChatRoomDetail(
    val chatRoomSeq: Long,
    val roomType: ChatRoomType,
    val counterpart: Counterpart,
    val lastReadMessageSeq: Long?,
    val lastHiddenMessageSeq: Long?,
    val unreadCount: Long,
)

/** 14.4 목록 항목이자 14.5 브로드캐스트 본문(clientMessageId는 브로드캐스트에서만) */
data class ChatMessageView(
    val chatMessageSeq: Long,
    val clientMessageId: String?,
    val chatRoomSeq: Long,
    val senderUserSeq: Long,
    val senderDisplayName: String,
    val messageType: ChatMessageType,
    val body: String?,
    val imageSeq: Long?,
    val imageUrl: String?,
    val sharedTargetType: SharedTargetType?,
    val sharedTargetSeq: Long?,
    val sharedPostUnavailable: Boolean,
    val regDttm: OffsetDateTime?,
)

data class ChatMessagePage(val content: List<ChatMessageView>, val nextBeforeMessageSeq: Long?, val hasNext: Boolean)

data class ReadResult(val lastReadMessageSeq: Long?, val unreadCount: Long, val totalUnreadMessageCount: Long)

data class LeaveResult(val lastHiddenMessageSeq: Long?, val totalUnreadMessageCount: Long)

data class UnreadCount(val totalUnreadMessageCount: Long)

enum class RoomUpdateType { MESSAGE, READ, LEFT }

/** /user/queue/room-updates 페이로드(Q454). room은 ChatRoomItem, LEFT(또는 목록에 안 보이는 방)는 {chatRoomSeq}만 */
data class RoomUpdate(val type: RoomUpdateType, val room: Any, val totalUnreadMessageCount: Long)

data class RoomRef(val chatRoomSeq: Long)
