package com.ssafy.animal.chat.adapter.out.persistence

import com.ssafy.animal.chat.application.port.out.ChatRepository
import com.ssafy.animal.chat.application.port.out.ImageRow
import com.ssafy.animal.chat.application.port.out.RoomSummaryRow
import com.ssafy.animal.chat.application.port.out.ShelterRow
import com.ssafy.animal.chat.application.port.out.UserRow
import com.ssafy.animal.chat.domain.ChatMessage
import com.ssafy.animal.chat.domain.ChatParticipant
import com.ssafy.animal.chat.domain.ChatRoom
import com.ssafy.animal.chat.domain.ChatRoomType
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.chat.domain.SharedTargetType
import com.ssafy.animal.common.auth.AuthUser
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface ChatRoomJpaRepository : JpaRepository<ChatRoom, Long> {
    @Query("select r from ChatRoom r where r.roomType = :roomType and r.userASeq = :userASeq and r.userBSeq = :userBSeq")
    fun findUserRoom(
        @Param("roomType") roomType: ChatRoomType,
        @Param("userASeq") userASeq: Int,
        @Param("userBSeq") userBSeq: Int,
    ): ChatRoom?

    @Query("select r from ChatRoom r where r.roomType = :roomType and r.userASeq = :userASeq and r.shelterSeq = :shelterSeq")
    fun findShelterRoom(
        @Param("roomType") roomType: ChatRoomType,
        @Param("userASeq") userASeq: Int,
        @Param("shelterSeq") shelterSeq: Int,
    ): ChatRoom?

    /**
     * 14.1 목록 행(Q454). 숨김 포인터 이후 메시지가 있는 방만, 미읽음 = 읽음·숨김 포인터 이후 + 본인 발신 제외.
     * "나"는 userKey(USER 참여) 또는 shelterKey(SHELTER 참여) 중 하나만 실제 값, 나머지는 -1
     */
    @Query(
        nativeQuery = true,
        value = """
        SELECT x.chat_room_seq AS chatRoomSeq, x.last_seq AS lastMessageSeq, x.unread AS unreadCount
        FROM (
            SELECT p.chat_room_seq,
                   COALESCE(p.last_hidden_message_seq, 0) AS hidden_floor,
                   (SELECT max(m.chat_message_seq) FROM chat_messages m WHERE m.chat_room_seq = p.chat_room_seq) AS last_seq,
                   (SELECT count(*) FROM chat_messages m
                     WHERE m.chat_room_seq = p.chat_room_seq
                       AND m.chat_message_seq > GREATEST(COALESCE(p.last_read_message_seq, 0), COALESCE(p.last_hidden_message_seq, 0))
                       AND m.sender_user_seq <> :actorUserSeq) AS unread
            FROM chat_participants p
            WHERE ((p.participant_type = 'USER' AND p.participant_user_seq = :userKey)
                OR (p.participant_type = 'SHELTER' AND p.participant_shelter_seq = :shelterKey))
              AND (:roomSeq = 0 OR p.chat_room_seq = :roomSeq)
        ) x
        WHERE x.last_seq > x.hidden_floor
          AND x.last_seq < :before
          AND (:unreadOnly = FALSE OR x.unread > 0)
        ORDER BY x.last_seq DESC
        LIMIT :limit
        """,
    )
    fun findRoomSummaries(
        @Param("actorUserSeq") actorUserSeq: Long,
        @Param("userKey") userKey: Long,
        @Param("shelterKey") shelterKey: Long,
        @Param("roomSeq") roomSeq: Long,
        @Param("before") before: Long,
        @Param("unreadOnly") unreadOnly: Boolean,
        @Param("limit") limit: Int,
    ): List<RoomSummaryRow>

    @Query(
        nativeQuery = true,
        value = """
        SELECT count(*) FROM chat_participants p
        JOIN chat_messages m ON m.chat_room_seq = p.chat_room_seq
         AND m.chat_message_seq > GREATEST(COALESCE(p.last_read_message_seq, 0), COALESCE(p.last_hidden_message_seq, 0))
         AND m.sender_user_seq <> :actorUserSeq
        WHERE (p.participant_type = 'USER' AND p.participant_user_seq = :userKey)
           OR (p.participant_type = 'SHELTER' AND p.participant_shelter_seq = :shelterKey)
        """,
    )
    fun countTotalUnread(
        @Param("actorUserSeq") actorUserSeq: Long,
        @Param("userKey") userKey: Long,
        @Param("shelterKey") shelterKey: Long,
    ): Long

    // 이하 다른 도메인 테이블 읽기 전용(Q420 D). 엔티티를 import하지 않는다

    @Query(
        nativeQuery = true,
        value = """
        SELECT u.user_seq AS userSeq, COALESCE(s.shelter_name, u.nickname) AS displayName,
               u.profile_image_seq AS profileImageSeq, u.role AS role, u.account_status AS accountStatus, u.is_deleted AS deleted
        FROM users u
        LEFT JOIN shelter_members sm ON sm.user_seq = u.user_seq
        LEFT JOIN shelters s ON s.shelter_seq = sm.shelter_seq
        WHERE u.user_seq IN (:userSeqs)
        """,
    )
    fun findUsers(@Param("userSeqs") userSeqs: Collection<Long>): List<UserRow>

    @Query(
        nativeQuery = true,
        value = """
        SELECT s.shelter_seq AS shelterSeq, s.shelter_name AS shelterName, s.profile_image_seq AS profileImageSeq,
               s.is_deleted AS deleted, sm.user_seq AS memberUserSeq
        FROM shelters s LEFT JOIN shelter_members sm ON sm.shelter_seq = s.shelter_seq
        WHERE s.shelter_seq IN (:shelterSeqs)
        """,
    )
    fun findShelters(@Param("shelterSeqs") shelterSeqs: Collection<Long>): List<ShelterRow>

    @Query(
        nativeQuery = true,
        value = "SELECT image_seq AS imageSeq, image_key AS imageKey, reg_usr_seq AS regUsrSeq FROM images WHERE image_seq IN (:imageSeqs)",
    )
    fun findImages(@Param("imageSeqs") imageSeqs: Collection<Long>): List<ImageRow>

    // 공통 필터(1.3): 탈퇴 작성자의 게시물은 비노출
    @Query(
        nativeQuery = true,
        value = """
        SELECT CAST(r.missing_report_seq AS BIGINT) FROM missing_reports r JOIN users u ON u.user_seq = r.user_seq
        WHERE r.missing_report_seq IN (:seqs) AND r.is_deleted = FALSE AND u.is_deleted = FALSE
        """,
    )
    fun findVisibleMissing(@Param("seqs") seqs: Collection<Long>): List<Long>

    @Query(
        nativeQuery = true,
        value = """
        SELECT CAST(r.found_report_seq AS BIGINT) FROM found_reports r JOIN users u ON u.user_seq = r.user_seq
        WHERE r.found_report_seq IN (:seqs) AND r.is_deleted = FALSE AND u.is_deleted = FALSE
        """,
    )
    fun findVisibleFound(@Param("seqs") seqs: Collection<Long>): List<Long>

    @Query(
        nativeQuery = true,
        value = "SELECT CAST(a.animal_seq AS BIGINT) FROM shelter_animals a WHERE a.animal_seq IN (:seqs) AND a.is_deleted = FALSE",
    )
    fun findVisibleShelterAnimals(@Param("seqs") seqs: Collection<Long>): List<Long>
}

interface ChatParticipantJpaRepository : JpaRepository<ChatParticipant, Long> {
    fun findByChatRoomSeqAndParticipantTypeAndParticipantUserSeq(chatRoomSeq: Long, type: ParticipantType, userSeq: Int): ChatParticipant?
    fun findByChatRoomSeqAndParticipantTypeAndParticipantShelterSeq(chatRoomSeq: Long, type: ParticipantType, shelterSeq: Int): ChatParticipant?
}

interface ChatMessageJpaRepository : JpaRepository<ChatMessage, Long> {
    fun countByChatRoomSeqAndChatMessageSeqGreaterThanAndSenderUserSeqNot(chatRoomSeq: Long, chatMessageSeq: Long, senderUserSeq: Int): Long

    @Query("select max(m.chatMessageSeq) from ChatMessage m where m.chatRoomSeq = :roomSeq")
    fun findLatestSeq(@Param("roomSeq") roomSeq: Long): Long?

    @Query(
        """
        select m from ChatMessage m
        where m.chatRoomSeq = :roomSeq and m.chatMessageSeq > :afterSeq and m.chatMessageSeq < :beforeSeq
        order by m.chatMessageSeq desc
        """,
    )
    fun findBetween(
        @Param("roomSeq") roomSeq: Long,
        @Param("afterSeq") afterSeq: Long,
        @Param("beforeSeq") beforeSeq: Long,
        pageable: Pageable,
    ): List<ChatMessage>
}

@Repository
class ChatPersistenceAdapter(
    private val roomJpaRepository: ChatRoomJpaRepository,
    private val participantJpaRepository: ChatParticipantJpaRepository,
    private val messageJpaRepository: ChatMessageJpaRepository,
) : ChatRepository {

    override fun findRoom(chatRoomSeq: Long) = roomJpaRepository.findByIdOrNull(chatRoomSeq)

    override fun findRooms(chatRoomSeqs: Collection<Long>): List<ChatRoom> = roomJpaRepository.findAllById(chatRoomSeqs)

    override fun findUserRoom(userASeq: Int, userBSeq: Int) =
        roomJpaRepository.findUserRoom(ChatRoomType.USER, userASeq, userBSeq)

    override fun findShelterRoom(userSeq: Int, shelterSeq: Int) =
        roomJpaRepository.findShelterRoom(ChatRoomType.SHELTER, userSeq, shelterSeq)

    override fun saveRoom(room: ChatRoom): ChatRoom = roomJpaRepository.save(room)

    override fun saveParticipants(participants: List<ChatParticipant>) {
        participantJpaRepository.saveAll(participants)
    }

    override fun findParticipant(chatRoomSeq: Long, actor: AuthUser): ChatParticipant? {
        val shelterSeq = actor.shelterSeq
        return if (shelterSeq != null) {
            participantJpaRepository.findByChatRoomSeqAndParticipantTypeAndParticipantShelterSeq(chatRoomSeq, ParticipantType.SHELTER, shelterSeq.toInt())
        } else {
            participantJpaRepository.findByChatRoomSeqAndParticipantTypeAndParticipantUserSeq(chatRoomSeq, ParticipantType.USER, actor.userSeq.toInt())
        }
    }

    override fun saveParticipant(participant: ChatParticipant) {
        participantJpaRepository.saveAndFlush(participant)
    }

    // IDENTITY라 save 시점에 INSERT가 나가 seq·regDttm이 채워진다
    override fun saveMessage(message: ChatMessage): ChatMessage = messageJpaRepository.save(message)

    override fun findMessage(chatMessageSeq: Long) = messageJpaRepository.findByIdOrNull(chatMessageSeq)

    override fun findMessages(chatMessageSeqs: Collection<Long>): List<ChatMessage> = messageJpaRepository.findAllById(chatMessageSeqs)

    override fun findLatestMessageSeq(chatRoomSeq: Long) = messageJpaRepository.findLatestSeq(chatRoomSeq)

    override fun findMessagesBetween(chatRoomSeq: Long, afterSeq: Long, beforeSeq: Long, limit: Int) =
        messageJpaRepository.findBetween(chatRoomSeq, afterSeq, beforeSeq, PageRequest.of(0, limit))

    override fun findRoomSummaries(actor: AuthUser, unreadOnly: Boolean, beforeLastMessageSeq: Long, chatRoomSeq: Long?, limit: Int) =
        roomJpaRepository.findRoomSummaries(
            actor.userSeq, actor.userKey(), actor.shelterKey(), chatRoomSeq ?: 0, beforeLastMessageSeq, unreadOnly, limit,
        )

    override fun countTotalUnread(actor: AuthUser) = roomJpaRepository.countTotalUnread(actor.userSeq, actor.userKey(), actor.shelterKey())

    override fun countUnread(chatRoomSeq: Long, readFloor: Long, actorUserSeq: Long) =
        messageJpaRepository.countByChatRoomSeqAndChatMessageSeqGreaterThanAndSenderUserSeqNot(chatRoomSeq, readFloor, actorUserSeq.toInt())

    override fun findUsers(userSeqs: Collection<Long>) = if (userSeqs.isEmpty()) emptyList() else roomJpaRepository.findUsers(userSeqs)

    override fun findShelters(shelterSeqs: Collection<Long>) =
        if (shelterSeqs.isEmpty()) emptyList() else roomJpaRepository.findShelters(shelterSeqs)

    override fun findImages(imageSeqs: Collection<Long>) = if (imageSeqs.isEmpty()) emptyList() else roomJpaRepository.findImages(imageSeqs)

    override fun findVisibleSharedPosts(type: SharedTargetType, seqs: Collection<Long>): Set<Long> {
        if (seqs.isEmpty()) return emptySet()
        return when (type) {
            SharedTargetType.MISSING -> roomJpaRepository.findVisibleMissing(seqs)
            SharedTargetType.FOUND -> roomJpaRepository.findVisibleFound(seqs)
            SharedTargetType.SHELTER_ANIMAL -> roomJpaRepository.findVisibleShelterAnimals(seqs)
        }.toSet()
    }

    // SHELTER 계정은 보호소로, USER는 회원으로 참여한다. 해당 없는 쪽은 어떤 seq와도 같지 않은 -1
    private fun AuthUser.userKey() = if (shelterSeq == null) userSeq else -1L

    private fun AuthUser.shelterKey() = shelterSeq ?: -1L
}
