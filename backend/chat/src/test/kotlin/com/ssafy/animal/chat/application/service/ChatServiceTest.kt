package com.ssafy.animal.chat.application.service

import com.ssafy.animal.chat.application.port.`in`.ChatMessageView
import com.ssafy.animal.chat.application.port.`in`.ChatRoomItem
import com.ssafy.animal.chat.application.port.`in`.CreateRoomCommand
import com.ssafy.animal.chat.application.port.`in`.RoomRef
import com.ssafy.animal.chat.application.port.`in`.RoomUpdate
import com.ssafy.animal.chat.application.port.`in`.RoomUpdateType
import com.ssafy.animal.chat.application.port.`in`.SendMessageCommand
import com.ssafy.animal.chat.application.port.out.ChatPushPort
import com.ssafy.animal.chat.application.port.out.ChatRepository
import com.ssafy.animal.chat.application.port.out.ImageRow
import com.ssafy.animal.chat.application.port.out.RoomSummaryRow
import com.ssafy.animal.chat.application.port.out.ShelterRow
import com.ssafy.animal.chat.application.port.out.UserRow
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.chat.domain.ChatMessage
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
import com.ssafy.animal.common.exception.ErrorCode
import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatServiceTest {

    private val repo = FakeChatRepository()
    private val broadcasts = mutableListOf<Pair<Long, ChatMessageView>>()
    private val roomUpdates = mutableListOf<Pair<Long, RoomUpdate>>()
    private val events = mutableListOf<Any>()

    private val push = object : ChatPushPort {
        override fun broadcast(chatRoomSeq: Long, message: ChatMessageView) {
            broadcasts += chatRoomSeq to message
        }
        override fun sendRoomUpdate(userSeq: Long, update: RoomUpdate) {
            roomUpdates += userSeq to update
        }
    }

    private val service = ChatService(repo, push, object : EventPublisher {
        override fun publish(event: Any) {
            events += event
        }
    }, "https://cdn/")

    private val alice = AuthUser(20, Role.USER, null)
    private val bob = AuthUser(10, Role.USER, null)
    private val carol = AuthUser(30, Role.USER, null)
    private val shelterAccount = AuthUser(1, Role.SHELTER, 7)

    init {
        repo.user(20, "앨리스", profileImageSeq = 900)
        repo.user(10, "밥")
        repo.user(30, "캐럴")
        repo.user(1, "행복보호소", role = "SHELTER")
        repo.shelters[7] = Shelter(7, "행복보호소", 901, false, 1)
        repo.images[900] = Image(900, "images/a.jpg", 20)
        repo.images[901] = Image(901, "images/s.jpg", null)
        repo.images[902] = Image(902, "images/mine.jpg", 20)
    }

    private fun userRoom(): Long = service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 10, null)).chatRoomSeq

    private fun text(content: String, id: String? = null) = SendMessageCommand(id, "TEXT", content, null, null, null)

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException>(block = block).errorCode)

    // ---- 14.2 ----

    @Test
    fun `14_2 USER끼리 처음 열면 오름차순으로 저장하고 다시 열면 재사용한다`() {
        val created = service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 10, null))
        assertFalse(created.reused)
        assertEquals(ChatRoomType.USER, created.roomType)
        val room = repo.rooms.single()
        assertEquals(10, room.userASeq)
        assertEquals(20, room.userBSeq)
        assertEquals(2, repo.participants.size)

        val again = service.createRoom(bob, CreateRoomCommand(ParticipantType.USER, 20, null))
        assertTrue(again.reused)
        assertEquals(created.chatRoomSeq, again.chatRoomSeq)
    }

    @Test
    fun `14_2 USER가 보호소와, 보호소가 USER와 열면 같은 SHELTER 방이다`() {
        val byUser = service.createRoom(alice, CreateRoomCommand(ParticipantType.SHELTER, null, 7))
        assertEquals(ChatRoomType.SHELTER, byUser.roomType)
        assertTrue(repo.participants.any { it.participantType == ParticipantType.SHELTER && it.participantShelterSeq == 7 })

        val byShelter = service.createRoom(shelterAccount, CreateRoomCommand(ParticipantType.USER, 20, null))
        assertTrue(byShelter.reused)
        assertEquals(byUser.chatRoomSeq, byShelter.chatRoomSeq)
    }

    @Test
    fun `14_2 지원하지 않는 조합은 409`() {
        assertCode(ChatErrorCode.CHAT_UNSUPPORTED_PARTICIPANTS) {
            service.createRoom(shelterAccount, CreateRoomCommand(ParticipantType.SHELTER, null, 7))
        }
        assertCode(ChatErrorCode.CHAT_UNSUPPORTED_PARTICIPANTS) { service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 20, null)) }
        // 보호소 계정은 USER 대상으로 지정할 수 없다(보호소 대상으로 연다)
        assertCode(ChatErrorCode.CHAT_UNSUPPORTED_PARTICIPANTS) { service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 1, null)) }
    }

    @Test
    fun `14_2 대상 없음 404, 탈퇴 대상 409, 분기와 다른 필드는 400`() {
        assertCode(ChatErrorCode.CHAT_TARGET_NOT_FOUND) { service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 99, null)) }
        assertCode(ChatErrorCode.CHAT_TARGET_NOT_FOUND) { service.createRoom(alice, CreateRoomCommand(ParticipantType.SHELTER, null, 99)) }
        repo.user(40, "탈퇴자", deleted = true)
        assertCode(ChatErrorCode.CHAT_COUNTERPART_DELETED) { service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 40, null)) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 10, 7)) }
    }

    // ---- 14.5 ----

    @Test
    fun `14_5 전송하면 커밋 후 브로드캐스트, 참여자별 room-updates, ChatMessageSent`() {
        val roomSeq = userRoom()
        val view = service.sendMessage(alice, roomSeq, text("안녕하세요", "c-1"))

        assertEquals("c-1", view.clientMessageId)
        assertEquals("앨리스", view.senderDisplayName)
        assertEquals(listOf(roomSeq to view), broadcasts)

        // 받는 사람 기준 미읽음: 보낸 사람 0, 받는 사람 1
        val updates = roomUpdates.toMap()
        assertEquals(setOf(20L, 10L), updates.keys)
        assertEquals(RoomUpdateType.MESSAGE, updates.getValue(10).type)
        assertEquals(1, (updates.getValue(10).room as ChatRoomItem).unreadCount)
        assertEquals(1, updates.getValue(10).totalUnreadMessageCount)
        assertEquals(0, (updates.getValue(20).room as ChatRoomItem).unreadCount)
        assertEquals("앨리스", (updates.getValue(10).room as ChatRoomItem).counterpart.displayName)

        val event = assertIs<ChatMessageSent>(events.single())
        assertEquals(listOf(10L), event.recipientUserSeqs)
        assertEquals("안녕하세요", event.preview)
    }

    @Test
    fun `14_5 보호소 방은 담당 계정이 받는다`() {
        val roomSeq = service.createRoom(alice, CreateRoomCommand(ParticipantType.SHELTER, null, 7)).chatRoomSeq
        service.sendMessage(alice, roomSeq, text("입양 문의요"))

        assertEquals(listOf(1L), assertIs<ChatMessageSent>(events.single()).recipientUserSeqs)
        val shelterSide = roomUpdates.toMap().getValue(1).room as ChatRoomItem
        assertEquals(ParticipantType.USER, shelterSide.counterpart.type)
        val userSide = roomUpdates.toMap().getValue(20).room as ChatRoomItem
        assertEquals("행복보호소", userSide.counterpart.displayName)
        assertEquals("https://cdn/images/s.jpg", userSide.counterpart.profileImage?.imageUrl)

        // 보호소 담당 계정이 보낸 메시지는 USER에게 간다
        events.clear()
        val reply = service.sendMessage(shelterAccount, roomSeq, text("네"))
        assertEquals("행복보호소", reply.senderDisplayName)
        assertEquals(listOf(20L), assertIs<ChatMessageSent>(events.single()).recipientUserSeqs)
    }

    @Test
    fun `14_5 비참여자 403, 탈퇴 상대 409`() {
        val roomSeq = userRoom()
        assertCode(ChatErrorCode.CHAT_ROOM_NOT_PARTICIPANT) { service.sendMessage(carol, roomSeq, text("끼어들기")) }
        repo.user(10, "밥", deleted = true)
        assertCode(ChatErrorCode.CHAT_COUNTERPART_DELETED) { service.sendMessage(alice, roomSeq, text("안녕")) }
        assertTrue(repo.messages.isEmpty())
    }

    @Test
    fun `14_5 본문 규칙 위반은 CHAT_INVALID_PAYLOAD`() {
        val roomSeq = userRoom()
        listOf(
            text(" "),
            SendMessageCommand(null, "VIDEO", "x", null, null, null),
            SendMessageCommand(null, null, "x", null, null, null),
            SendMessageCommand(null, "IMAGE", "사진", null, null, null),
            SendMessageCommand(null, "SHARED_POST", null, null, "ADOPTION", 1),
            SendMessageCommand(null, "SHARED_POST", null, null, "FOUND", null),
            text("x", "c".repeat(65)),
        ).forEach { assertCode(ChatErrorCode.CHAT_INVALID_PAYLOAD) { service.sendMessage(alice, roomSeq, it) } }
    }

    @Test
    fun `14_5 이미지는 본인 업로드만, 공유 게시물은 존재해야 한다`() {
        val roomSeq = userRoom()
        assertCode(SharedErrorCode.IMAGE_NOT_FOUND) { service.sendMessage(alice, roomSeq, SendMessageCommand(null, "IMAGE", null, 999, null, null)) }
        assertCode(SharedErrorCode.IMAGE_NOT_OWNED) { service.sendMessage(alice, roomSeq, SendMessageCommand(null, "IMAGE", null, 901, null, null)) }
        assertCode(ChatErrorCode.CHAT_SHARED_POST_NOT_FOUND) {
            service.sendMessage(alice, roomSeq, SendMessageCommand(null, "SHARED_POST", null, null, "FOUND", 210))
        }

        val image = service.sendMessage(alice, roomSeq, SendMessageCommand(null, "IMAGE", " ", 902, null, null))
        assertEquals("https://cdn/images/mine.jpg", image.imageUrl)
        assertNull(image.body)
        assertEquals("사진을 보냈어요", assertIs<ChatMessageSent>(events.single()).preview)

        repo.visiblePosts[SharedTargetType.FOUND] = mutableSetOf(210)
        val shared = service.sendMessage(alice, roomSeq, SendMessageCommand(null, "SHARED_POST", null, null, "FOUND", 210))
        assertFalse(shared.sharedPostUnavailable)
    }

    @Test
    fun `14_5 탈퇴 401, 정지 403(Q433)`() {
        val roomSeq = userRoom()
        repo.user(20, "앨리스", status = "SUSPENDED")
        assertCode(SharedErrorCode.AUTH_ACCOUNT_UNAVAILABLE) { service.sendMessage(alice, roomSeq, text("안녕")) }
        repo.user(20, "앨리스", deleted = true)
        assertCode(CommonErrorCode.AUTH_TOKEN_INVALID) { service.sendMessage(alice, roomSeq, text("안녕")) }
    }

    // ---- 14.1·14.3·14.4·14.8 ----

    @Test
    fun `14_1 메시지가 있는 방만, 마지막 메시지 내림차순 커서`() {
        val withBob = userRoom()
        val withCarol = service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 30, null)).chatRoomSeq
        service.createRoom(alice, CreateRoomCommand(ParticipantType.SHELTER, null, 7)) // 메시지 없는 방은 안 보인다
        service.sendMessage(bob, withBob, text("1"))
        service.sendMessage(carol, withCarol, text("2"))

        val first = service.getRooms(alice, false, null, 1)
        assertEquals(listOf(withCarol), first.content.map { it.chatRoomSeq })
        assertTrue(first.hasNext)
        val second = service.getRooms(alice, false, first.nextBeforeLastMessageSeq, 1)
        assertEquals(listOf(withBob), second.content.map { it.chatRoomSeq })
        assertFalse(second.hasNext)
        assertNull(second.nextBeforeLastMessageSeq)

        val bobItem = second.content.single()
        assertEquals("밥", bobItem.counterpart.displayName)
        assertEquals("1", bobItem.lastMessage.body)
        assertEquals(1, bobItem.unreadCount)
    }

    @Test
    fun `14_1 unreadOnly와 탈퇴 상대 표시`() {
        val withBob = userRoom()
        val withCarol = service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 30, null)).chatRoomSeq
        service.sendMessage(bob, withBob, text("읽지 않음"))
        service.sendMessage(alice, withCarol, text("내가 보냄"))

        assertEquals(listOf(withBob), service.getRooms(alice, true, null, 20).content.map { it.chatRoomSeq })

        repo.user(10, "밥", profileImageSeq = 900, deleted = true)
        val item = service.getRooms(alice, true, null, 20).content.single()
        assertEquals(ChatService.WITHDRAWN, item.counterpart.displayName)
        assertNull(item.counterpart.profileImage)
    }

    @Test
    fun `14_3 상세와 미읽음, 비참여자는 404`() {
        val roomSeq = userRoom()
        service.sendMessage(bob, roomSeq, text("1"))
        service.sendMessage(bob, roomSeq, text("2"))

        val detail = service.getRoom(alice, roomSeq)
        assertEquals(2, detail.unreadCount)
        assertEquals("밥", detail.counterpart.displayName)
        assertNull(detail.lastReadMessageSeq)
        assertCode(ChatErrorCode.CHAT_ROOM_NOT_FOUND) { service.getRoom(carol, roomSeq) }
        assertCode(ChatErrorCode.CHAT_ROOM_NOT_FOUND) { service.getRoom(alice, 9999) }
    }

    @Test
    fun `14_4 역방향 커서, 다른 방 메시지 커서는 400, 삭제된 공유 게시물 표시`() {
        val roomSeq = userRoom()
        repo.visiblePosts[SharedTargetType.MISSING] = mutableSetOf(5)
        val seqs = (1..3).map { service.sendMessage(alice, roomSeq, text("m$it")).chatMessageSeq }
        service.sendMessage(alice, roomSeq, SendMessageCommand(null, "SHARED_POST", null, null, "MISSING", 5))
        repo.visiblePosts.clear() // 공유 후 게시물 삭제

        val first = service.getMessages(alice, roomSeq, null, 2)
        assertEquals(2, first.content.size)
        assertTrue(first.content[0].sharedPostUnavailable)
        assertEquals(seqs[2], first.content[1].chatMessageSeq)
        assertTrue(first.hasNext)
        val second = service.getMessages(alice, roomSeq, first.nextBeforeMessageSeq, 30)
        assertEquals(seqs.take(2).reversed(), second.content.map { it.chatMessageSeq })
        assertFalse(second.hasNext)

        val other = service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 30, null)).chatRoomSeq
        val otherSeq = service.sendMessage(alice, other, text("x")).chatMessageSeq
        assertCode(ChatErrorCode.CHAT_MESSAGE_NOT_IN_ROOM) { service.getMessages(alice, roomSeq, otherSeq, 30) }
        assertCode(ChatErrorCode.CHAT_ROOM_NOT_FOUND) { service.getMessages(carol, roomSeq, null, 30) }
    }

    // ---- 14.6·14.7 ----

    @Test
    fun `14_6 읽음은 앞으로만, 본인에게 READ`() {
        val roomSeq = userRoom()
        val m1 = service.sendMessage(bob, roomSeq, text("1")).chatMessageSeq
        val m2 = service.sendMessage(bob, roomSeq, text("2")).chatMessageSeq
        roomUpdates.clear()

        val result = service.read(alice, roomSeq, m2)
        assertEquals(m2, result.lastReadMessageSeq)
        assertEquals(0, result.unreadCount)
        assertEquals(0, result.totalUnreadMessageCount)
        val (userSeq, update) = roomUpdates.single()
        assertEquals(20L, userSeq)
        assertEquals(RoomUpdateType.READ, update.type)

        assertEquals(m2, service.read(alice, roomSeq, m1).lastReadMessageSeq) // 되돌리기 무시
        val other = service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 30, null)).chatRoomSeq
        val otherSeq = service.sendMessage(carol, other, text("x")).chatMessageSeq
        assertCode(ChatErrorCode.CHAT_MESSAGE_NOT_IN_ROOM) { service.read(alice, roomSeq, otherSeq) }
        assertCode(ChatErrorCode.CHAT_ROOM_NOT_FOUND) { service.read(carol, roomSeq, m1) }
    }

    @Test
    fun `14_7 나가면 본인에게만 숨기고 새 메시지가 오면 다시 보인다`() {
        val roomSeq = userRoom()
        val old = service.sendMessage(bob, roomSeq, text("옛날 대화")).chatMessageSeq
        roomUpdates.clear()

        val left = service.leave(alice, roomSeq)
        assertEquals(old, left.lastHiddenMessageSeq)
        assertEquals(0, left.totalUnreadMessageCount)
        val (userSeq, update) = roomUpdates.single()
        assertEquals(20L, userSeq)
        assertEquals(RoomUpdateType.LEFT, update.type)
        assertEquals(RoomRef(roomSeq), update.room)
        assertTrue(service.getRooms(alice, false, null, 20).content.isEmpty())
        assertEquals(1, service.getRooms(bob, false, null, 20).content.size)

        // 재입장: 같은 방, 이후 메시지만
        assertTrue(service.createRoom(alice, CreateRoomCommand(ParticipantType.USER, 10, null)).reused)
        service.sendMessage(bob, roomSeq, text("다시 안녕"))
        assertEquals(listOf("다시 안녕"), service.getMessages(alice, roomSeq, null, 30).content.map { it.body })
        assertEquals(1, service.getRooms(alice, false, null, 20).content.single().unreadCount)
        assertEquals(1, service.getUnreadCount(alice).totalUnreadMessageCount)
    }

    @Test
    fun `isParticipant는 SUBSCRIBE 인가에 쓴다`() {
        val roomSeq = userRoom()
        assertTrue(service.isParticipant(alice, roomSeq))
        assertFalse(service.isParticipant(carol, roomSeq))
    }
}

data class User(
    override val userSeq: Int,
    override val displayName: String?,
    override val profileImageSeq: Long?,
    override val role: String,
    override val accountStatus: String,
    override val deleted: Boolean,
) : UserRow

data class Shelter(
    override val shelterSeq: Int,
    override val shelterName: String,
    override val profileImageSeq: Long?,
    override val deleted: Boolean,
    override val memberUserSeq: Int?,
) : ShelterRow

data class Image(override val imageSeq: Long, override val imageKey: String, override val regUsrSeq: Int?) : ImageRow

data class Summary(override val chatRoomSeq: Long, override val lastMessageSeq: Long, override val unreadCount: Long) : RoomSummaryRow

/** 저장소 native 쿼리와 같은 규칙을 메모리에서 계산한다 */
class FakeChatRepository : ChatRepository {
    val rooms = mutableListOf<ChatRoom>()
    val participants = mutableListOf<ChatParticipant>()
    val messages = mutableListOf<ChatMessage>()
    val users = mutableMapOf<Int, UserRow>()
    val shelters = mutableMapOf<Int, ShelterRow>()
    val images = mutableMapOf<Long, ImageRow>()
    val visiblePosts = mutableMapOf<SharedTargetType, MutableSet<Long>>()
    private var seq = 0L

    fun user(seq: Int, name: String, role: String = "USER", status: String = "ACTIVE", deleted: Boolean = false, profileImageSeq: Long? = null) {
        users[seq] = User(seq, name, profileImageSeq, role, status, deleted)
    }

    private fun ChatParticipant.isMine(actor: AuthUser) = if (actor.shelterSeq != null) {
        participantType == ParticipantType.SHELTER && participantShelterSeq == actor.shelterSeq!!.toInt()
    } else {
        participantType == ParticipantType.USER && participantUserSeq == actor.userSeq.toInt()
    }

    override fun findRoom(chatRoomSeq: Long) = rooms.find { it.chatRoomSeq == chatRoomSeq }
    override fun findRooms(chatRoomSeqs: Collection<Long>) = rooms.filter { it.chatRoomSeq in chatRoomSeqs }
    override fun findUserRoom(userASeq: Int, userBSeq: Int) =
        rooms.find { it.roomType == ChatRoomType.USER && it.userASeq == userASeq && it.userBSeq == userBSeq }
    override fun findShelterRoom(userSeq: Int, shelterSeq: Int) =
        rooms.find { it.roomType == ChatRoomType.SHELTER && it.userASeq == userSeq && it.shelterSeq == shelterSeq }
    override fun saveRoom(room: ChatRoom) = room.also {
        ReflectionTestUtils.setField(it, "chatRoomSeq", ++seq)
        rooms += it
    }
    override fun saveParticipants(participants: List<ChatParticipant>) {
        this.participants += participants
    }
    override fun findParticipant(chatRoomSeq: Long, actor: AuthUser) = participants.find { it.chatRoomSeq == chatRoomSeq && it.isMine(actor) }
    override fun saveParticipant(participant: ChatParticipant) = Unit
    override fun saveMessage(message: ChatMessage) = message.also {
        ReflectionTestUtils.setField(it, "chatMessageSeq", ++seq)
        ReflectionTestUtils.setField(it, "regDttm", OffsetDateTime.now())
        messages += it
    }
    override fun findMessage(chatMessageSeq: Long) = messages.find { it.chatMessageSeq == chatMessageSeq }
    override fun findMessages(chatMessageSeqs: Collection<Long>) = messages.filter { it.chatMessageSeq in chatMessageSeqs }
    override fun findLatestMessageSeq(chatRoomSeq: Long) = messages.filter { it.chatRoomSeq == chatRoomSeq }.maxOfOrNull { it.chatMessageSeq!! }
    override fun findMessagesBetween(chatRoomSeq: Long, afterSeq: Long, beforeSeq: Long, limit: Int) = messages
        .filter { it.chatRoomSeq == chatRoomSeq && it.chatMessageSeq!! > afterSeq && it.chatMessageSeq!! < beforeSeq }
        .sortedByDescending { it.chatMessageSeq }
        .take(limit)

    override fun findRoomSummaries(actor: AuthUser, unreadOnly: Boolean, beforeLastMessageSeq: Long, chatRoomSeq: Long?, limit: Int) =
        participants.filter { it.isMine(actor) && (chatRoomSeq == null || it.chatRoomSeq == chatRoomSeq) }
            .mapNotNull { p ->
                val last = findLatestMessageSeq(p.chatRoomSeq) ?: return@mapNotNull null
                if (last <= p.hiddenFloor || last >= beforeLastMessageSeq) return@mapNotNull null
                Summary(p.chatRoomSeq, last, countUnread(p.chatRoomSeq, p.readFloor, actor.userSeq))
            }
            .filter { !unreadOnly || it.unreadCount > 0 }
            .sortedByDescending { it.lastMessageSeq }
            .take(limit)

    override fun countTotalUnread(actor: AuthUser) =
        participants.filter { it.isMine(actor) }.sumOf { countUnread(it.chatRoomSeq, it.readFloor, actor.userSeq) }

    override fun countUnread(chatRoomSeq: Long, readFloor: Long, actorUserSeq: Long) = messages
        .count { it.chatRoomSeq == chatRoomSeq && it.chatMessageSeq!! > readFloor && it.senderUserSeq.toLong() != actorUserSeq }
        .toLong()

    override fun findUsers(userSeqs: Collection<Long>) = userSeqs.mapNotNull { users[it.toInt()] }
    override fun findShelters(shelterSeqs: Collection<Long>) = shelterSeqs.mapNotNull { shelters[it.toInt()] }
    override fun findImages(imageSeqs: Collection<Long>) = imageSeqs.mapNotNull { images[it] }
    override fun findVisibleSharedPosts(type: SharedTargetType, seqs: Collection<Long>) =
        seqs.filter { it in visiblePosts[type].orEmpty() }.toSet()
}
