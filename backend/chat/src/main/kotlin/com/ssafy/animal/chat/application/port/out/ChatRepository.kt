package com.ssafy.animal.chat.application.port.out

import com.ssafy.animal.chat.domain.ChatMessage
import com.ssafy.animal.chat.domain.ChatParticipant
import com.ssafy.animal.chat.domain.ChatRoom
import com.ssafy.animal.chat.domain.SharedTargetType
import com.ssafy.animal.common.auth.AuthUser
import java.time.Instant

/**
 * chat_* 쓰기 + 다른 도메인 테이블(users·shelters·shelter_members·images·게시물) 읽기 전용 native 조회(Q420 D).
 * 참여자 "나"는 USER면 participant_user_seq, SHELTER면 participant_shelter_seq로 찾는다.
 */
interface ChatRepository {
    fun findRoom(chatRoomSeq: Long): ChatRoom?
    fun findRooms(chatRoomSeqs: Collection<Long>): List<ChatRoom>
    fun findUserRoom(userASeq: Int, userBSeq: Int): ChatRoom?
    fun findShelterRoom(userSeq: Int, shelterSeq: Int): ChatRoom?
    fun saveRoom(room: ChatRoom): ChatRoom
    fun saveParticipants(participants: List<ChatParticipant>)

    fun findParticipant(chatRoomSeq: Long, actor: AuthUser): ChatParticipant?

    /** 포인터 변경을 바로 반영한다(이어지는 native 미읽음 계산이 보도록) */
    fun saveParticipant(participant: ChatParticipant)

    fun saveMessage(message: ChatMessage): ChatMessage
    fun findMessage(chatMessageSeq: Long): ChatMessage?
    fun findMessages(chatMessageSeqs: Collection<Long>): List<ChatMessage>
    fun findLatestMessageSeq(chatRoomSeq: Long): Long?

    /** afterSeq < seq < beforeSeq, seq 내림차순 최대 limit개 */
    fun findMessagesBetween(chatRoomSeq: Long, afterSeq: Long, beforeSeq: Long, limit: Int): List<ChatMessage>

    /**
     * 14.1 목록 행. 숨김 포인터 이후 메시지가 있는 방만, 마지막 메시지 seq < beforeLastMessageSeq, 내림차순.
     * chatRoomSeq를 주면 그 방 하나만 본다(room-updates용)
     */
    fun findRoomSummaries(
        actor: AuthUser,
        unreadOnly: Boolean,
        beforeLastMessageSeq: Long,
        chatRoomSeq: Long?,
        limit: Int,
    ): List<RoomSummaryRow>

    /** 14.8 내 모든 방 미읽음 합계 */
    fun countTotalUnread(actor: AuthUser): Long

    /** 한 방의 미읽음: seq > readFloor 이고 보낸 사람이 내가 아닌 메시지 수 */
    fun countUnread(chatRoomSeq: Long, readFloor: Long, actorUserSeq: Long): Long

    fun findUsers(userSeqs: Collection<Long>): List<UserRow>
    fun findShelters(shelterSeqs: Collection<Long>): List<ShelterRow>
    fun findImages(imageSeqs: Collection<Long>): List<ImageRow>

    /** 비삭제·작성자 비탈퇴(보호동물은 비삭제) 게시물만 */
    fun findVisibleSharedPosts(type: SharedTargetType, seqs: Collection<Long>): Set<Long>

    /** 14.9 회원이 이 보호소에 낸 입양 신청. 12.9와 같이 DRAFT·CANCELED·삭제는 빼고 제출일 최신순 */
    fun findShelterApplications(userSeq: Int, shelterSeq: Int): List<ApplicationRow>

    /** 회원 계정의 본명(users.name). 없으면 null */
    fun findUserRealName(userSeq: Int): String?
}

interface ApplicationRow {
    val applicationSeq: Long
    val speciesName: String
    val breedName: String
    val status: String

    /** native timestamptz는 Instant로 온다 */
    val submittedDttm: Instant?
}

interface RoomSummaryRow {
    val chatRoomSeq: Long
    val lastMessageSeq: Long
    val unreadCount: Long
}

/** users 읽기. SHELTER 계정의 displayName은 소속 보호소명 */
interface UserRow {
    val userSeq: Int
    val displayName: String?
    val profileImageSeq: Long?
    val role: String
    val accountStatus: String
    val deleted: Boolean
}

interface ShelterRow {
    val shelterSeq: Int
    val shelterName: String
    val profileImageSeq: Long?
    val deleted: Boolean

    /** 담당 계정(shelter_members, 보호소당 1개). 없으면 null */
    val memberUserSeq: Int?
}

interface ImageRow {
    val imageSeq: Long
    val imageKey: String
    val regUsrSeq: Int?
}
