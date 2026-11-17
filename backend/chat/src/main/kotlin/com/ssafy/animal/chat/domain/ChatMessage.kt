package com.ssafy.animal.chat.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

enum class ChatMessageType { TEXT, IMAGE, SHARED_POST }

/** chat_messages.shared_target_type 허용값(Q387, CHECK ck_chat_shared_type) */
enum class SharedTargetType { MISSING, FOUND, SHELTER_ANIMAL }

/** DB 명세 27. chat_messages. 쓰기 경로는 STOMP SEND 하나(R16 B), 수정·삭제 없음 */
@Entity
@Table(name = "chat_messages")
class ChatMessage(
    chatRoomSeq: Long,
    senderUserSeq: Int,
    messageType: ChatMessageType,
    body: String?,
    imageSeq: Long?,
    sharedTargetType: SharedTargetType?,
    sharedTargetSeq: Long?,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var chatMessageSeq: Long? = null
        protected set

    @Column(nullable = false)
    var chatRoomSeq: Long = chatRoomSeq
        protected set

    @Column(nullable = false)
    var senderUserSeq: Int = senderUserSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var messageType: ChatMessageType = messageType
        protected set

    @Column(columnDefinition = "text")
    var body: String? = body
        protected set

    var imageSeq: Long? = imageSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    var sharedTargetType: SharedTargetType? = sharedTargetType
        protected set

    var sharedTargetSeq: Long? = sharedTargetSeq
        protected set

    /** ChatMessageSent 푸시 미리보기. 앱 목록 미리보기(14.1)와 같은 규칙. IMAGE·SHARED_POST는 본문이 있으면 본문(Q526) */
    fun preview(): String = when (messageType) {
        ChatMessageType.TEXT -> body.orEmpty()
        ChatMessageType.IMAGE -> body?.takeIf { it.isNotBlank() } ?: "사진을 보냈어요"
        ChatMessageType.SHARED_POST -> body?.takeIf { it.isNotBlank() } ?: "게시글을 공유했어요"
    }.take(PREVIEW_LENGTH)

    companion object {
        private const val PREVIEW_LENGTH = 100
    }
}
