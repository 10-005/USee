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

enum class ParticipantType { USER, SHELTER }

/**
 * DB 명세 26. chat_participants. 보호소 쪽은 계정이 아니라 보호소(participant_shelter_seq)로 참여한다.
 * 읽음·숨김 포인터는 같은 방 메시지만 가리킨다(복합 FK). 미읽음은 이 두 포인터로 조회 시 계산(Q454)
 */
@Entity
@Table(name = "chat_participants")
class ChatParticipant private constructor(
    chatRoomSeq: Long,
    participantType: ParticipantType,
    participantUserSeq: Int?,
    participantShelterSeq: Int?,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var chatParticipantSeq: Long? = null
        protected set

    @Column(nullable = false)
    var chatRoomSeq: Long = chatRoomSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var participantType: ParticipantType = participantType
        protected set

    var participantUserSeq: Int? = participantUserSeq
        protected set

    var participantShelterSeq: Int? = participantShelterSeq
        protected set

    var lastReadMessageSeq: Long? = null
        protected set

    var lastHiddenMessageSeq: Long? = null
        protected set

    /** 이 포인터보다 큰 메시지만 미읽음 후보 */
    val readFloor: Long get() = maxOf(lastReadMessageSeq ?: 0, hiddenFloor)

    /** 이 포인터보다 큰 메시지만 보인다(나가기 이전 대화 숨김) */
    val hiddenFloor: Long get() = lastHiddenMessageSeq ?: 0

    /** 14.6 읽음. 과거로 되돌리는 요청은 무시한다 */
    fun read(messageSeq: Long) {
        if (messageSeq > (lastReadMessageSeq ?: 0)) lastReadMessageSeq = messageSeq
    }

    /** 14.7 나가기. 방의 마지막 메시지까지 본인에게만 숨긴다(메시지가 없으면 그대로) */
    fun hideUntil(latestMessageSeq: Long?) {
        if (latestMessageSeq != null && latestMessageSeq > hiddenFloor) lastHiddenMessageSeq = latestMessageSeq
    }

    companion object {
        fun user(chatRoomSeq: Long, userSeq: Int) = ChatParticipant(chatRoomSeq, ParticipantType.USER, userSeq, null)

        fun shelter(chatRoomSeq: Long, shelterSeq: Int) = ChatParticipant(chatRoomSeq, ParticipantType.SHELTER, null, shelterSeq)
    }
}
