package com.ssafy.animal.chat.application.service

import com.ssafy.animal.chat.application.port.`in`.ChatMessagePage
import com.ssafy.animal.chat.application.port.`in`.ChatMessageView
import com.ssafy.animal.chat.application.port.`in`.ChatRoomDetail
import com.ssafy.animal.chat.application.port.`in`.ChatRoomItem
import com.ssafy.animal.chat.application.port.`in`.ChatRoomPage
import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.application.port.`in`.Counterpart
import com.ssafy.animal.chat.application.port.`in`.CounterpartApplication
import com.ssafy.animal.chat.application.port.`in`.CounterpartProfile
import com.ssafy.animal.chat.application.port.`in`.CreateRoomCommand
import com.ssafy.animal.chat.application.port.`in`.CreateRoomResult
import com.ssafy.animal.chat.application.port.`in`.ImageRef
import com.ssafy.animal.chat.application.port.`in`.LastMessage
import com.ssafy.animal.chat.application.port.`in`.LeaveResult
import com.ssafy.animal.chat.application.port.`in`.ReadResult
import com.ssafy.animal.chat.application.port.`in`.RoomRef
import com.ssafy.animal.chat.application.port.`in`.RoomUpdate
import com.ssafy.animal.chat.application.port.`in`.RoomUpdateType
import com.ssafy.animal.chat.application.port.`in`.SendMessageCommand
import com.ssafy.animal.chat.application.port.`in`.UnreadCount
import com.ssafy.animal.chat.application.port.out.ChatPushPort
import com.ssafy.animal.chat.application.port.out.ChatRepository
import com.ssafy.animal.chat.application.port.out.RoomSummaryRow
import com.ssafy.animal.chat.application.port.out.UserRow
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.chat.domain.ChatMessage
import com.ssafy.animal.chat.domain.ChatMessageType
import com.ssafy.animal.chat.domain.ChatParticipant
import com.ssafy.animal.chat.domain.ChatRoom
import com.ssafy.animal.chat.domain.ChatRoomType
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.chat.domain.SharedErrorCode
import com.ssafy.animal.chat.domain.SharedTargetType
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.ChatMessageSent
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.ZoneId

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatPushPort: ChatPushPort,
    private val eventPublisher: EventPublisher,
    // core-api와 같은 값(imageUrl = {base}/{imageKey}, Q443). chat yml에 없으면 환경 변수를 직접 읽는다
    @Value("\${storage.public-base-url:\${STORAGE_PUBLIC_BASE_URL:}}") publicBaseUrl: String,
) : ChatUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val imageBaseUrl = publicBaseUrl.trimEnd('/')

    override fun requireWritableAccount(userSeq: Long) {
        // ponytail: 쓰기마다 PK 조회 1회, 캐시 없음(core-api AccountStatusInterceptor와 같은 방식)
        val user = chatRepository.findUsers(listOf(userSeq)).firstOrNull()
        if (user == null || user.deleted) throw BusinessException(CommonErrorCode.AUTH_TOKEN_INVALID)
        if (user.accountStatus != ACTIVE) throw BusinessException(SharedErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
    }

    override fun isParticipant(actor: AuthUser, chatRoomSeq: Long) = chatRepository.findParticipant(chatRoomSeq, actor) != null

    override fun getRooms(actor: AuthUser, unreadOnly: Boolean, beforeLastMessageSeq: Long?, size: Int): ChatRoomPage {
        val limit = size.coerceIn(1, MAX_SIZE)
        val rows = chatRepository.findRoomSummaries(actor, unreadOnly, beforeLastMessageSeq ?: Long.MAX_VALUE, null, limit + 1)
        val page = rows.take(limit)
        val hasNext = rows.size > limit
        return ChatRoomPage(toRoomItems(actor, page), if (hasNext) page.last().lastMessageSeq else null, hasNext)
    }

    @Transactional
    override fun createRoom(actor: AuthUser, command: CreateRoomCommand): CreateRoomResult {
        val room = when {
            // SHELTER는 USER에게만 연다(R8), 보호소↔보호소 없음
            actor.shelterSeq != null -> {
                if (command.targetType != ParticipantType.USER) throw BusinessException(ChatErrorCode.CHAT_UNSUPPORTED_PARTICIPANTS)
                val userSeq = requireTargetUser(command, actor)
                chatRepository.findShelterRoom(userSeq, actor.shelterSeq!!.toInt()) ?: ChatRoom.userShelter(userSeq, actor.shelterSeq!!.toInt())
            }
            command.targetType == ParticipantType.USER -> {
                val userSeq = requireTargetUser(command, actor)
                val (a, b) = minOf(userSeq, actor.userSeq.toInt()) to maxOf(userSeq, actor.userSeq.toInt())
                chatRepository.findUserRoom(a, b) ?: ChatRoom.userPair(a, b)
            }
            else -> {
                val shelterSeq = requireTargetShelter(command)
                chatRepository.findShelterRoom(actor.userSeq.toInt(), shelterSeq) ?: ChatRoom.userShelter(actor.userSeq.toInt(), shelterSeq)
            }
        }
        if (room.chatRoomSeq != null) return CreateRoomResult(room.chatRoomSeq!!, room.roomType, reused = true)

        // ponytail: 같은 상대에게 동시에 처음 열면 부분 UNIQUE에 한쪽이 걸려 500. 재시도하면 기존 방을 재사용한다
        val saved = chatRepository.saveRoom(room)
        chatRepository.saveParticipants(saved.newParticipants())
        return CreateRoomResult(saved.chatRoomSeq!!, saved.roomType, reused = false)
    }

    override fun getRoom(actor: AuthUser, chatRoomSeq: Long): ChatRoomDetail {
        val me = participantOrNotFound(actor, chatRoomSeq)
        val room = chatRepository.findRoom(chatRoomSeq)!!
        return ChatRoomDetail(
            chatRoomSeq = chatRoomSeq,
            roomType = room.roomType,
            counterpart = counterparts(actor, listOf(room)).getValue(chatRoomSeq),
            lastReadMessageSeq = me.lastReadMessageSeq,
            lastHiddenMessageSeq = me.lastHiddenMessageSeq,
            unreadCount = chatRepository.countUnread(chatRoomSeq, me.readFloor, actor.userSeq),
        )
    }

    override fun getCounterpartProfile(actor: AuthUser, chatRoomSeq: Long): CounterpartProfile {
        val shelterSeq = actor.shelterSeq ?: throw BusinessException(CommonErrorCode.AUTH_FORBIDDEN)
        participantOrNotFound(actor, chatRoomSeq)
        // 보호소가 참여한 방은 SHELTER 방이고 회원은 userASeq
        val userSeq = chatRepository.findRoom(chatRoomSeq)!!.userASeq
        val user = chatRepository.findUsers(listOf(userSeq.toLong())).firstOrNull()?.takeUnless { it.deleted }
        val applications = chatRepository.findShelterApplications(userSeq, shelterSeq.toInt()).map {
            CounterpartApplication(it.applicationSeq, it.speciesName, it.breedName, it.status, it.submittedDttm?.atZone(KST)?.toOffsetDateTime())
        }
        // 본명은 이 보호소와 입양 신청으로 엮인 회원만(실명 노출 최소화)
        val realName = if (user != null && applications.isNotEmpty()) chatRepository.findUserRealName(userSeq) else null
        val image = user?.profileImageSeq?.let { ImageRef(it, imageUrls(listOf(it))[it]) }
        return CounterpartProfile(userSeq.toLong(), displayName(user), realName, image, applications)
    }

    override fun getMessages(actor: AuthUser, chatRoomSeq: Long, beforeMessageSeq: Long?, size: Int): ChatMessagePage {
        val me = participantOrNotFound(actor, chatRoomSeq)
        beforeMessageSeq?.let { requireMessageInRoom(chatRoomSeq, it) }
        val limit = size.coerceIn(1, MAX_SIZE)
        val messages = chatRepository.findMessagesBetween(chatRoomSeq, me.hiddenFloor, beforeMessageSeq ?: Long.MAX_VALUE, limit + 1)
        val page = messages.take(limit)
        val hasNext = messages.size > limit
        return ChatMessagePage(toViews(page, null), if (hasNext) page.last().chatMessageSeq else null, hasNext)
    }

    @Transactional
    override fun sendMessage(actor: AuthUser, chatRoomSeq: Long, command: SendMessageCommand): ChatMessageView {
        requireWritableAccount(actor.userSeq)
        // 명세 14.5 처리 순서: 참여자 → 탈퇴 상대 → 본문
        chatRepository.findParticipant(chatRoomSeq, actor) ?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_PARTICIPANT)
        val room = chatRepository.findRoom(chatRoomSeq)!!
        val members = membersOf(room)
        if (members.any { it.userSeq != actor.userSeq && it.deleted }) throw BusinessException(ChatErrorCode.CHAT_COUNTERPART_DELETED)

        val message = chatRepository.saveMessage(buildMessage(actor, chatRoomSeq, command))
        val view = toViews(listOf(message), command.clientMessageId).single()

        // 받는 사람 기준 목록 항목·미읽음은 같은 트랜잭션에서 계산하고, 전송은 커밋 후
        val recipients = members.mapNotNull { it.actor }
        val updates = recipients.map { it.userSeq to RoomUpdate(RoomUpdateType.MESSAGE, roomUpdateItem(it, chatRoomSeq), chatRepository.countTotalUnread(it)) }
        afterCommit {
            chatPushPort.broadcast(chatRoomSeq, view)
            updates.forEach { (userSeq, update) -> chatPushPort.sendRoomUpdate(userSeq, update) }
        }
        eventPublisher.publish(
            ChatMessageSent(
                chatRoomSeq = chatRoomSeq,
                messageSeq = view.chatMessageSeq,
                senderUserSeq = actor.userSeq,
                senderDisplayName = view.senderDisplayName,
                preview = message.preview(),
                recipientUserSeqs = recipients.map { it.userSeq }.filter { it != actor.userSeq },
            ),
        )
        return view
    }

    @Transactional
    override fun read(actor: AuthUser, chatRoomSeq: Long, lastReadMessageSeq: Long): ReadResult {
        val me = participantOrNotFound(actor, chatRoomSeq)
        requireMessageInRoom(chatRoomSeq, lastReadMessageSeq)
        me.read(lastReadMessageSeq)
        chatRepository.saveParticipant(me)
        val total = chatRepository.countTotalUnread(actor)
        val update = RoomUpdate(RoomUpdateType.READ, roomUpdateItem(actor, chatRoomSeq), total)
        afterCommit { chatPushPort.sendRoomUpdate(actor.userSeq, update) }
        return ReadResult(me.lastReadMessageSeq, chatRepository.countUnread(chatRoomSeq, me.readFloor, actor.userSeq), total)
    }

    @Transactional
    override fun leave(actor: AuthUser, chatRoomSeq: Long): LeaveResult {
        val me = participantOrNotFound(actor, chatRoomSeq)
        me.hideUntil(chatRepository.findLatestMessageSeq(chatRoomSeq))
        chatRepository.saveParticipant(me)
        val total = chatRepository.countTotalUnread(actor)
        afterCommit { chatPushPort.sendRoomUpdate(actor.userSeq, RoomUpdate(RoomUpdateType.LEFT, RoomRef(chatRoomSeq), total)) }
        return LeaveResult(me.lastHiddenMessageSeq, total)
    }

    override fun getUnreadCount(actor: AuthUser) = UnreadCount(chatRepository.countTotalUnread(actor))

    // ---- 방 생성 대상 ----

    private fun requireTargetUser(command: CreateRoomCommand, actor: AuthUser): Int {
        val targetUserSeq = command.targetUserSeq
        if (targetUserSeq == null || command.targetShelterSeq != null) throw invalidTarget("targetUserSeq")
        if (targetUserSeq == actor.userSeq) throw BusinessException(ChatErrorCode.CHAT_UNSUPPORTED_PARTICIPANTS)
        val user = chatRepository.findUsers(listOf(targetUserSeq)).firstOrNull()
            ?: throw BusinessException(ChatErrorCode.CHAT_TARGET_NOT_FOUND)
        if (user.deleted) throw BusinessException(ChatErrorCode.CHAT_COUNTERPART_DELETED)
        // 보호소 계정과는 보호소 대상(targetType=SHELTER)으로만 대화한다
        if (user.role != Role.USER.name) throw BusinessException(ChatErrorCode.CHAT_UNSUPPORTED_PARTICIPANTS)
        return user.userSeq
    }

    private fun requireTargetShelter(command: CreateRoomCommand): Int {
        val targetShelterSeq = command.targetShelterSeq
        if (targetShelterSeq == null || command.targetUserSeq != null) throw invalidTarget("targetShelterSeq")
        val shelter = chatRepository.findShelters(listOf(targetShelterSeq)).firstOrNull()
        if (shelter == null || shelter.deleted) throw BusinessException(ChatErrorCode.CHAT_TARGET_NOT_FOUND)
        return shelter.shelterSeq
    }

    private fun invalidTarget(field: String) = BusinessException(
        CommonErrorCode.COMMON_INVALID_INPUT,
        errors = listOf(ErrorDetail(field, "targetType에 맞는 대상 하나만 보내야 합니다.")),
    )

    // ---- 메시지 ----

    private fun buildMessage(actor: AuthUser, chatRoomSeq: Long, command: SendMessageCommand): ChatMessage {
        if ((command.clientMessageId?.length ?: 0) > CLIENT_MESSAGE_ID_MAX) throw invalidPayload()
        val type = command.type?.let { runCatching { ChatMessageType.valueOf(it) }.getOrNull() } ?: throw invalidPayload()
        val sender = actor.userSeq.toInt()
        val content = command.content?.takeIf { it.isNotBlank() }
        return when (type) {
            ChatMessageType.TEXT -> ChatMessage(chatRoomSeq, sender, type, content ?: throw invalidPayload(), null, null, null)
            ChatMessageType.IMAGE -> {
                val imageSeq = command.imageSeq ?: throw invalidPayload()
                val image = chatRepository.findImages(listOf(imageSeq)).firstOrNull()
                    ?: throw BusinessException(SharedErrorCode.IMAGE_NOT_FOUND)
                // Q407: 업로더 본인 이미지만(시드 NULL 포함 거부)
                if (image.regUsrSeq?.toLong() != actor.userSeq) throw BusinessException(SharedErrorCode.IMAGE_NOT_OWNED)
                ChatMessage(chatRoomSeq, sender, type, content, imageSeq, null, null)
            }
            ChatMessageType.SHARED_POST -> {
                val targetType = command.sharedTargetType?.let { runCatching { SharedTargetType.valueOf(it) }.getOrNull() }
                    ?: throw invalidPayload()
                val targetSeq = command.sharedTargetSeq ?: throw invalidPayload()
                if (targetSeq !in chatRepository.findVisibleSharedPosts(targetType, listOf(targetSeq))) {
                    throw BusinessException(ChatErrorCode.CHAT_SHARED_POST_NOT_FOUND)
                }
                ChatMessage(chatRoomSeq, sender, type, content, null, targetType, targetSeq)
            }
        }
    }

    private fun invalidPayload() = BusinessException(ChatErrorCode.CHAT_INVALID_PAYLOAD)

    private fun toViews(messages: List<ChatMessage>, clientMessageId: String?): List<ChatMessageView> {
        if (messages.isEmpty()) return emptyList()
        val senders = chatRepository.findUsers(messages.map { it.senderUserSeq.toLong() }.distinct()).associateBy { it.userSeq }
        val imageUrls = imageUrls(messages.mapNotNull { it.imageSeq })
        val visiblePosts = messages.filter { it.sharedTargetType != null }
            .groupBy({ it.sharedTargetType!! }, { it.sharedTargetSeq!! })
            .mapValues { (type, seqs) -> chatRepository.findVisibleSharedPosts(type, seqs.distinct()) }
        return messages.map { m ->
            ChatMessageView(
                chatMessageSeq = m.chatMessageSeq!!,
                clientMessageId = clientMessageId,
                chatRoomSeq = m.chatRoomSeq,
                senderUserSeq = m.senderUserSeq.toLong(),
                senderDisplayName = displayName(senders[m.senderUserSeq]),
                messageType = m.messageType,
                body = m.body,
                imageSeq = m.imageSeq,
                imageUrl = m.imageSeq?.let(imageUrls::get),
                sharedTargetType = m.sharedTargetType,
                sharedTargetSeq = m.sharedTargetSeq,
                sharedPostUnavailable = m.sharedTargetType?.let { m.sharedTargetSeq !in visiblePosts[it].orEmpty() } ?: false,
                regDttm = m.regDttm,
            )
        }
    }

    // ---- 방 목록 항목·상대 ----

    private fun toRoomItems(actor: AuthUser, rows: List<RoomSummaryRow>): List<ChatRoomItem> {
        if (rows.isEmpty()) return emptyList()
        val rooms = chatRepository.findRooms(rows.map { it.chatRoomSeq }).associateBy { it.chatRoomSeq!! }
        val lastMessages = chatRepository.findMessages(rows.map { it.lastMessageSeq }).associateBy { it.chatMessageSeq!! }
        val counterparts = counterparts(actor, rooms.values)
        return rows.map { row ->
            val last = lastMessages.getValue(row.lastMessageSeq)
            ChatRoomItem(
                chatRoomSeq = row.chatRoomSeq,
                roomType = rooms.getValue(row.chatRoomSeq).roomType,
                counterpart = counterparts.getValue(row.chatRoomSeq),
                lastMessage = LastMessage(last.chatMessageSeq!!, last.messageType, last.body),
                lastMessageSeq = row.lastMessageSeq,
                lastMessageDttm = last.regDttm,
                unreadCount = row.unreadCount,
            )
        }
    }

    /** room-updates의 room. 목록에 안 보이는 방(숨김 이후 메시지 없음)은 {chatRoomSeq}만 */
    private fun roomUpdateItem(viewer: AuthUser, chatRoomSeq: Long): Any =
        chatRepository.findRoomSummaries(viewer, false, Long.MAX_VALUE, chatRoomSeq, 1).firstOrNull()
            ?.let { toRoomItems(viewer, listOf(it)).single() }
            ?: RoomRef(chatRoomSeq)

    private fun counterparts(actor: AuthUser, rooms: Collection<ChatRoom>): Map<Long, Counterpart> {
        val userTargets = rooms.associate { it.chatRoomSeq!! to counterpartUserSeq(actor, it) }
        val users = chatRepository.findUsers(userTargets.values.filterNotNull().map(Int::toLong)).associateBy { it.userSeq }
        val shelterSeqs = rooms.filter { userTargets[it.chatRoomSeq!!] == null }.map { it.shelterSeq!!.toLong() }
        val shelters = chatRepository.findShelters(shelterSeqs).associateBy { it.shelterSeq }
        val imageUrls = imageUrls(
            users.values.filterNot { it.deleted }.mapNotNull { it.profileImageSeq } +
                shelters.values.filterNot { it.deleted }.mapNotNull { it.profileImageSeq },
        )
        fun image(seq: Long?) = seq?.let { ImageRef(it, imageUrls[it]) }

        return rooms.associate { room ->
            val seq = room.chatRoomSeq!!
            val userSeq = userTargets[seq]
            seq to if (userSeq != null) {
                val user = users[userSeq]?.takeUnless { it.deleted }
                Counterpart(ParticipantType.USER, userSeq.toLong(), null, displayName(user), image(user?.profileImageSeq))
            } else {
                val shelter = shelters[room.shelterSeq!!]?.takeUnless { it.deleted }
                Counterpart(
                    ParticipantType.SHELTER, null, room.shelterSeq!!.toLong(),
                    shelter?.shelterName ?: WITHDRAWN, image(shelter?.profileImageSeq),
                )
            }
        }
    }

    /** 상대가 회원이면 그 userSeq, 보호소면 null */
    private fun counterpartUserSeq(actor: AuthUser, room: ChatRoom): Int? = when {
        room.roomType == ChatRoomType.USER -> if (room.userASeq.toLong() == actor.userSeq) room.userBSeq else room.userASeq
        actor.shelterSeq != null -> room.userASeq
        else -> null
    }

    /** 방의 양쪽(보호소는 담당 계정으로). actor는 room-updates·푸시 수신자, 담당 계정이 없는 보호소는 null */
    private fun membersOf(room: ChatRoom): List<Member> {
        val userSeqs = listOfNotNull(room.userASeq, room.userBSeq).map(Int::toLong)
        val users = chatRepository.findUsers(userSeqs).associateBy { it.userSeq.toLong() }
        val members = userSeqs.map { Member(it, users[it]?.deleted ?: true, AuthUser(it, Role.USER, null)) }
        val shelterSeq = room.shelterSeq ?: return members
        val shelter = chatRepository.findShelters(listOf(shelterSeq.toLong())).firstOrNull()
        val memberUserSeq = shelter?.memberUserSeq?.toLong()
        return members + Member(
            userSeq = memberUserSeq,
            deleted = shelter == null || shelter.deleted,
            actor = memberUserSeq?.let { AuthUser(it, Role.SHELTER, shelterSeq.toLong()) },
        )
    }

    private data class Member(val userSeq: Long?, val deleted: Boolean, val actor: AuthUser?)

    private fun displayName(user: UserRow?) = user?.takeUnless { it.deleted }?.displayName ?: WITHDRAWN

    private fun imageUrls(imageSeqs: Collection<Long>): Map<Long, String> =
        if (imageSeqs.isEmpty()) emptyMap()
        else chatRepository.findImages(imageSeqs.distinct()).associate { it.imageSeq to "$imageBaseUrl/${it.imageKey}" }

    private fun participantOrNotFound(actor: AuthUser, chatRoomSeq: Long): ChatParticipant =
        chatRepository.findParticipant(chatRoomSeq, actor) ?: throw BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND)

    private fun requireMessageInRoom(chatRoomSeq: Long, chatMessageSeq: Long) {
        if (chatRepository.findMessage(chatMessageSeq)?.chatRoomSeq != chatRoomSeq) {
            throw BusinessException(ChatErrorCode.CHAT_MESSAGE_NOT_IN_ROOM)
        }
    }

    /** 트랜잭션 밖(단위 테스트)이면 바로 실행. 전송 실패가 이미 커밋된 요청을 실패로 바꾸지 않게 로그만 남긴다 */
    private fun afterCommit(action: () -> Unit) {
        val safe = { runCatching(action).onFailure { log.error("[chat] STOMP 전송 실패", it) }; Unit }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return safe()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = safe()
        })
    }

    companion object {
        const val WITHDRAWN = "(탈퇴한 사용자)"
        private val KST = ZoneId.of("Asia/Seoul")
        private const val ACTIVE = "ACTIVE"
        private const val MAX_SIZE = 100
        private const val CLIENT_MESSAGE_ID_MAX = 64
    }
}
