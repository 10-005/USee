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

enum class ChatRoomType { USER, SHELTER }

/** DB 명세 25. chat_rooms. USER형은 user_a < user_b 정렬 저장, SHELTER형은 user_a + 보호소(CHECK ck_chat_room_pair) */
@Entity
@Table(name = "chat_rooms")
class ChatRoom private constructor(
    roomType: ChatRoomType,
    userASeq: Int,
    userBSeq: Int?,
    shelterSeq: Int?,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var chatRoomSeq: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var roomType: ChatRoomType = roomType
        protected set

    // 기본 명명 전략은 userASeq를 user_aseq로 바꾸므로 이름을 직접 적는다
    @Column(name = "user_a_seq", nullable = false)
    var userASeq: Int = userASeq
        protected set

    @Column(name = "user_b_seq")
    var userBSeq: Int? = userBSeq
        protected set

    var shelterSeq: Int? = shelterSeq
        protected set

    /** 양쪽 참여자 행(DB 명세 26: 방과 같이 만든다) */
    fun newParticipants(): List<ChatParticipant> {
        val roomSeq = checkNotNull(chatRoomSeq) { "저장 후 호출" }
        return when (roomType) {
            ChatRoomType.USER -> listOf(ChatParticipant.user(roomSeq, userASeq), ChatParticipant.user(roomSeq, userBSeq!!))
            ChatRoomType.SHELTER -> listOf(ChatParticipant.user(roomSeq, userASeq), ChatParticipant.shelter(roomSeq, shelterSeq!!))
        }
    }

    companion object {
        fun userPair(first: Int, second: Int) = ChatRoom(ChatRoomType.USER, minOf(first, second), maxOf(first, second), null)

        fun userShelter(userSeq: Int, shelterSeq: Int) = ChatRoom(ChatRoomType.SHELTER, userSeq, null, shelterSeq)
    }
}
