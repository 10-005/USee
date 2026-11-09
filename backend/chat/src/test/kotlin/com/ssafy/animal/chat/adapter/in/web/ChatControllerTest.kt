package com.ssafy.animal.chat.adapter.`in`.web

import com.ssafy.animal.chat.application.port.`in`.ChatMessagePage
import com.ssafy.animal.chat.application.port.`in`.ChatMessageView
import com.ssafy.animal.chat.application.port.`in`.ChatRoomDetail
import com.ssafy.animal.chat.application.port.`in`.ChatRoomItem
import com.ssafy.animal.chat.application.port.`in`.ChatRoomPage
import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.application.port.`in`.Counterpart
import com.ssafy.animal.chat.application.port.`in`.CreateRoomCommand
import com.ssafy.animal.chat.application.port.`in`.CreateRoomResult
import com.ssafy.animal.chat.application.port.`in`.ImageRef
import com.ssafy.animal.chat.application.port.`in`.LastMessage
import com.ssafy.animal.chat.application.port.`in`.LeaveResult
import com.ssafy.animal.chat.application.port.`in`.ReadResult
import com.ssafy.animal.chat.application.port.`in`.UnreadCount
import com.ssafy.animal.chat.domain.ChatErrorCode
import com.ssafy.animal.chat.domain.ChatMessageType
import com.ssafy.animal.chat.domain.ChatRoomType
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.chat.domain.SharedErrorCode
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.chat.TestTokens
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(ChatController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
@Import(AccountStatusConfig::class)
class ChatControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var chatUseCase: ChatUseCase

    private val me = AuthUser(17, Role.USER, null)
    private val dttm = OffsetDateTime.of(2026, 10, 29, 17, 50, 0, 0, ZoneOffset.ofHours(9))
    private val counterpart = Counterpart(ParticipantType.SHELTER, null, 7, "행복동물보호센터", ImageRef(1201, "https://cdn/p.jpg"))

    private fun get(path: String, auth: Boolean = true) = mockMvc.get("/api/chat/v1/chats$path") {
        if (auth) header(HttpHeaders.AUTHORIZATION, BEARER)
    }

    private fun send(method: String, path: String, body: String): ResultActionsDsl {
        val init: org.springframework.test.web.servlet.MockHttpServletRequestDsl.() -> Unit = {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
        return if (method == "PATCH") mockMvc.patch("/api/chat/v1/chats$path", dsl = init) else mockMvc.post("/api/chat/v1/chats$path", dsl = init)
    }

    private fun failWith(code: com.ssafy.animal.common.exception.ErrorCode) = BusinessException(code)

    @Test
    fun `인증 없이는 401`() {
        get("/rooms", auth = false).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
    }

    @Test
    fun `14_1 커서 목록`() {
        val item = ChatRoomItem(501, ChatRoomType.SHELTER, counterpart, LastMessage(4002, ChatMessageType.TEXT, "확인했습니다."), 4002, dttm, 2)
        given(chatUseCase.getRooms(me, true, 4000, 20)).willReturn(ChatRoomPage(listOf(item), 4002, true))

        get("/rooms?unreadOnly=true&beforeLastMessageSeq=4000").andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].chatRoomSeq") { value(501) }
            jsonPath("$.data.content[0].counterpart.type") { value("SHELTER") }
            jsonPath("$.data.content[0].counterpart.userSeq") { value(null) }
            jsonPath("$.data.content[0].counterpart.profileImage.imageUrl") { value("https://cdn/p.jpg") }
            jsonPath("$.data.content[0].lastMessage.body") { value("확인했습니다.") }
            jsonPath("$.data.content[0].lastMessageDttm") { value("2026-10-29T17:50:00+09:00") }
            jsonPath("$.data.content[0].unreadCount") { value(2) }
            jsonPath("$.data.nextBeforeLastMessageSeq") { value(4002) }
            jsonPath("$.data.hasNext") { value(true) }
        }
    }

    @Test
    fun `14_2 최초 생성 201, 재사용 200`() {
        given(chatUseCase.createRoom(me, CreateRoomCommand(ParticipantType.SHELTER, null, 7)))
            .willReturn(CreateRoomResult(501, ChatRoomType.SHELTER, reused = false))
        send("POST", "/rooms", """{"targetType":"SHELTER","targetShelterSeq":7}""").andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(201) }
            jsonPath("$.data.chatRoomSeq") { value(501) }
            jsonPath("$.data.reused") { value(false) }
        }

        given(chatUseCase.createRoom(me, CreateRoomCommand(ParticipantType.USER, 18, null)))
            .willReturn(CreateRoomResult(502, ChatRoomType.USER, reused = true))
        send("POST", "/rooms", """{"targetType":"USER","targetUserSeq":18}""").andExpect {
            status { isOk() }
            jsonPath("$.data.reused") { value(true) }
        }
    }

    @Test
    fun `14_2 targetType 누락 400, 도메인 오류 409·404`() {
        send("POST", "/rooms", """{"targetUserSeq":18}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }

        given(chatUseCase.createRoom(me, CreateRoomCommand(ParticipantType.USER, 18, null)))
            .willThrow(failWith(ChatErrorCode.CHAT_COUNTERPART_DELETED))
        send("POST", "/rooms", """{"targetType":"USER","targetUserSeq":18}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CHAT_COUNTERPART_DELETED") }
        }

        given(chatUseCase.createRoom(me, CreateRoomCommand(ParticipantType.USER, 99, null)))
            .willThrow(failWith(ChatErrorCode.CHAT_TARGET_NOT_FOUND))
        send("POST", "/rooms", """{"targetType":"USER","targetUserSeq":99}""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CHAT_TARGET_NOT_FOUND") }
        }
    }

    @Test
    fun `14_2 정지 계정의 쓰기는 처리 전에 403(Q433)`() {
        willThrow(failWith(SharedErrorCode.AUTH_ACCOUNT_UNAVAILABLE)).given(chatUseCase).requireWritableAccount(17)
        send("POST", "/rooms", """{"targetType":"USER","targetUserSeq":18}""").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_ACCOUNT_UNAVAILABLE") }
        }
        verify(chatUseCase, never()).createRoom(me, CreateRoomCommand(ParticipantType.USER, 18, null))
    }

    @Test
    fun `조회(GET)는 계정 재확인을 하지 않는다`() {
        willThrow(failWith(CommonErrorCode.AUTH_TOKEN_INVALID)).given(chatUseCase).requireWritableAccount(17)
        given(chatUseCase.getUnreadCount(me)).willReturn(UnreadCount(3))
        get("/unread-count").andExpect { status { isOk() } }
    }

    @Test
    fun `14_3 상세, 비참여자 404`() {
        given(chatUseCase.getRoom(me, 501)).willReturn(ChatRoomDetail(501, ChatRoomType.SHELTER, counterpart, 4001, null, 1))
        get("/rooms/501").andExpect {
            status { isOk() }
            jsonPath("$.data.lastReadMessageSeq") { value(4001) }
            jsonPath("$.data.lastHiddenMessageSeq") { value(null) }
            jsonPath("$.data.counterpart.displayName") { value("행복동물보호센터") }
            jsonPath("$.data.unreadCount") { value(1) }
        }

        given(chatUseCase.getRoom(me, 999)).willThrow(failWith(ChatErrorCode.CHAT_ROOM_NOT_FOUND))
        get("/rooms/999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CHAT_ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `14_4 메시지 커서, 다른 방 커서 400`() {
        val message = ChatMessageView(
            4003, null, 501, 17, "반려가족", ChatMessageType.SHARED_POST, null, null, null,
            com.ssafy.animal.chat.domain.SharedTargetType.FOUND, 210, true, dttm,
        )
        given(chatUseCase.getMessages(me, 501, null, 30)).willReturn(ChatMessagePage(listOf(message), 4003, true))
        get("/rooms/501/messages").andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].chatMessageSeq") { value(4003) }
            jsonPath("$.data.content[0].sharedTargetType") { value("FOUND") }
            jsonPath("$.data.content[0].sharedPostUnavailable") { value(true) }
            jsonPath("$.data.nextBeforeMessageSeq") { value(4003) }
        }

        given(chatUseCase.getMessages(me, 501, 77, 10)).willThrow(failWith(ChatErrorCode.CHAT_MESSAGE_NOT_IN_ROOM))
        get("/rooms/501/messages?beforeMessageSeq=77&size=10").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CHAT_MESSAGE_NOT_IN_ROOM") }
        }
    }

    @Test
    fun `14_6 읽음, 필수값 누락 400, 다른 방 메시지 400`() {
        given(chatUseCase.read(me, 501, 4003)).willReturn(ReadResult(4003, 0, 121))
        send("PATCH", "/rooms/501/read", """{"lastReadMessageSeq":4003}""").andExpect {
            status { isOk() }
            jsonPath("$.data.lastReadMessageSeq") { value(4003) }
            jsonPath("$.data.unreadCount") { value(0) }
            jsonPath("$.data.totalUnreadMessageCount") { value(121) }
        }

        send("PATCH", "/rooms/501/read", "{}").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }

        given(chatUseCase.read(me, 501, 1)).willThrow(failWith(ChatErrorCode.CHAT_MESSAGE_NOT_IN_ROOM))
        send("PATCH", "/rooms/501/read", """{"lastReadMessageSeq":1}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CHAT_MESSAGE_NOT_IN_ROOM") }
        }
    }

    @Test
    fun `14_7 나가기, 비참여자 404`() {
        given(chatUseCase.leave(me, 501)).willReturn(LeaveResult(4003, 119))
        send("POST", "/rooms/501/leave", "{}").andExpect {
            status { isOk() }
            jsonPath("$.data.lastHiddenMessageSeq") { value(4003) }
            jsonPath("$.data.totalUnreadMessageCount") { value(119) }
        }

        given(chatUseCase.leave(me, 999)).willThrow(failWith(ChatErrorCode.CHAT_ROOM_NOT_FOUND))
        send("POST", "/rooms/999/leave", "{}").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CHAT_ROOM_NOT_FOUND") }
        }
    }

    @Test
    fun `14_8 전체 미읽음`() {
        given(chatUseCase.getUnreadCount(me)).willReturn(UnreadCount(123))
        get("/unread-count").andExpect {
            status { isOk() }
            jsonPath("$.data.totalUnreadMessageCount") { value(123) }
        }
    }

    companion object {
        private val BEARER = "Bearer " + TestTokens.access(17)

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { TestTokens.publicKeyBase64 }
        }
    }
}
