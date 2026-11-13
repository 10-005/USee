package com.ssafy.animal.chat.application.port.`in`

import com.ssafy.animal.chat.domain.ChatMessageType
import com.ssafy.animal.chat.domain.ChatRoomType
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.chat.domain.SharedTargetType
import com.ssafy.animal.common.auth.AuthUser
import io.swagger.v3.oas.annotations.media.Schema
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

    /** 14.9 보호소 전용. 상대 회원 정보 + 이 보호소에 낸 입양 신청 */
    fun getCounterpartProfile(actor: AuthUser, chatRoomSeq: Long): CounterpartProfile
}

data class CounterpartProfile(
    @field:Schema(description = "상대 회원 번호", example = "1")
    val userSeq: Long,
    @field:Schema(description = "회원 닉네임. 탈퇴한 회원은 \"(탈퇴한 사용자)\"", example = "justin")
    val nickname: String,
    @field:Schema(
        description = "회원 계정의 본명. 이 보호소에 낸 입양 신청(applications)이 1건 이상일 때만 주고, 없거나 탈퇴했거나 본명이 없으면 null",
        nullable = true,
        example = "신지환",
    )
    val realName: String?,
    @field:Schema(description = "프로필 이미지. 없거나 탈퇴한 회원이면 null(기본 아바타)", nullable = true)
    val profileImage: ImageRef?,
    @field:Schema(description = "이 회원이 로그인한 보호소에 낸 입양 신청. 제출일 최신순, 없으면 빈 배열")
    val applications: List<CounterpartApplication>,
)

data class CounterpartApplication(
    @field:Schema(description = "신청서 번호. 상세는 GET /api/core/v1/adoption-applications/{applicationSeq}", example = "9")
    val applicationSeq: Long,
    @field:Schema(description = "축종 이름", example = "개")
    val speciesName: String,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(
        description = "신청 상태. SUBMITTED=심사 대기, APPROVED=서류 승인, ADOPTED=최종 승인, REJECTED=거절(DRAFT·CANCELED는 나오지 않음)",
        example = "SUBMITTED",
    )
    val status: String,
    @field:Schema(description = "제출 시각", nullable = true, example = "2026-11-07T01:59:35+09:00")
    val submittedDttm: OffsetDateTime?,
)

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

data class ImageRef(
    @field:Schema(description = "이미지 번호", example = "1201")
    val imageSeq: Long,
    @field:Schema(description = "접근 URL", nullable = true, example = "https://cdn.example.com/images/1201.jpg")
    val imageUrl: String?,
    @field:Schema(description = "표시 순서. 프로필은 항상 1", example = "1")
    val displayOrder: Int = 1,
)

data class Counterpart(
    @field:Schema(description = "상대 종류. USER=회원, SHELTER=보호소", example = "SHELTER")
    val type: ParticipantType,
    @field:Schema(description = "상대 회원 번호. 상대가 보호소면 null", nullable = true, example = "null")
    val userSeq: Long?,
    @field:Schema(description = "상대 보호소 번호. 상대가 회원이면 null", nullable = true, example = "7")
    val shelterSeq: Long?,
    @field:Schema(description = "표시 이름(회원 닉네임, 보호소 계정은 보호소명). 탈퇴한 상대는 \"(탈퇴한 사용자)\"", example = "행복동물보호센터")
    val displayName: String,
    @field:Schema(description = "프로필 이미지. 없거나 탈퇴한 상대면 null(앱 기본 아바타)", nullable = true)
    val profileImage: ImageRef?,
)

data class LastMessage(
    @field:Schema(description = "마지막 메시지 번호", example = "4002")
    val chatMessageSeq: Long,
    @field:Schema(description = "메시지 종류. TEXT=글, IMAGE=사진, SHARED_POST=게시글 공유", example = "TEXT")
    val messageType: ChatMessageType,
    @field:Schema(
        description = "본문. IMAGE·SHARED_POST는 없으면 null. 미리보기는 앱이 IMAGE는 \"사진을 보냈어요\"(본문이 있으면 본문), SHARED_POST는 \"게시글을 공유했어요\"(본문이 있으면 본문)로 표시",
        nullable = true,
        example = "확인했습니다.",
    )
    val body: String?,
)

data class ChatRoomItem(
    @field:Schema(description = "대화방 번호", example = "501")
    val chatRoomSeq: Long,
    @field:Schema(description = "방 종류. USER=회원↔회원, SHELTER=회원↔보호소", example = "SHELTER")
    val roomType: ChatRoomType,
    @field:Schema(description = "대화 상대")
    val counterpart: Counterpart,
    @field:Schema(description = "방의 마지막 메시지")
    val lastMessage: LastMessage,
    @field:Schema(description = "마지막 메시지 번호. 목록 정렬 기준(내림차순)", example = "4002")
    val lastMessageSeq: Long,
    @field:Schema(description = "마지막 메시지 시각", example = "2026-10-29T17:50:00+09:00")
    val lastMessageDttm: OffsetDateTime?,
    @field:Schema(description = "이 방의 미읽음 수. 내 읽음·숨김 포인터 이후, 내가 보내지 않은 메시지 수", example = "2")
    val unreadCount: Long,
)

data class ChatRoomPage(
    @field:Schema(description = "대화방 목록. 마지막 메시지 최신순")
    val content: List<ChatRoomItem>,
    @field:Schema(description = "다음 페이지 커서. 다음 요청의 beforeLastMessageSeq에 넣는다. 마지막 페이지면 null", nullable = true, example = "3870")
    val nextBeforeLastMessageSeq: Long?,
    @field:Schema(description = "다음 페이지가 있는지", example = "true")
    val hasNext: Boolean,
)

data class CreateRoomResult(
    @field:Schema(description = "대화방 번호", example = "501")
    val chatRoomSeq: Long,
    @field:Schema(description = "방 종류. USER=회원↔회원, SHELTER=회원↔보호소", example = "SHELTER")
    val roomType: ChatRoomType,
    @field:Schema(description = "기존 방을 재사용했으면 true(HTTP 200), 새로 만들었으면 false(HTTP 201)", example = "false")
    val reused: Boolean,
)

data class ChatRoomDetail(
    @field:Schema(description = "대화방 번호", example = "501")
    val chatRoomSeq: Long,
    @field:Schema(description = "방 종류. USER=회원↔회원, SHELTER=회원↔보호소", example = "SHELTER")
    val roomType: ChatRoomType,
    @field:Schema(description = "대화 상대")
    val counterpart: Counterpart,
    @field:Schema(description = "내가 마지막으로 읽은 메시지 번호. 읽은 적 없으면 null", nullable = true, example = "4000")
    val lastReadMessageSeq: Long?,
    @field:Schema(description = "나가기로 숨긴 마지막 메시지 번호. 이 번호 이하 메시지는 내게 보이지 않는다. 나간 적 없으면 null", nullable = true, example = "null")
    val lastHiddenMessageSeq: Long?,
    @field:Schema(description = "이 방의 미읽음 수(목록과 같은 정의)", example = "2")
    val unreadCount: Long,
    @field:Schema(description = "상대가 마지막으로 읽은 메시지 번호. 내 메시지 중 이 번호 이하는 상대가 읽은 것. 상대가 읽은 적 없으면 null", nullable = true, example = "4001")
    val counterpartLastReadMessageSeq: Long?,
    @field:Schema(
        description = "이 방에서 가장 최근 공유한 게시글. 내가 나가기 전 공유도 포함(숨김 포인터와 무관). 원글이 삭제·비노출이어도 그대로 준다. 공유한 적 없으면 null",
        nullable = true,
    )
    val lastSharedPost: SharedPostRef?,
)

data class SharedPostRef(
    @field:Schema(description = "공유 대상 종류. MISSING=실종 신고, FOUND=발견 신고, SHELTER_ANIMAL=보호동물", example = "FOUND")
    val sharedTargetType: SharedTargetType,
    @field:Schema(description = "공유 대상 번호", example = "210")
    val sharedTargetSeq: Long,
)

/** 14.4 목록 항목이자 14.5 브로드캐스트 본문(clientMessageId는 브로드캐스트에서만) */
data class ChatMessageView(
    @field:Schema(description = "메시지 번호", example = "4003")
    val chatMessageSeq: Long,
    @field:Schema(description = "STOMP 전송 때 클라이언트가 보낸 ID. 브로드캐스트에서만 채우고 REST 조회에서는 항상 null", nullable = true, example = "null")
    val clientMessageId: String?,
    @field:Schema(description = "대화방 번호", example = "501")
    val chatRoomSeq: Long,
    @field:Schema(description = "보낸 계정의 회원 번호(보호소 계정도 users PK)", example = "17")
    val senderUserSeq: Long,
    @field:Schema(description = "보낸 사람 표시 이름. 탈퇴했으면 \"(탈퇴한 사용자)\"", example = "반려가족")
    val senderDisplayName: String,
    @field:Schema(description = "메시지 종류. TEXT=글, IMAGE=사진, SHARED_POST=게시글 공유", example = "TEXT")
    val messageType: ChatMessageType,
    @field:Schema(description = "본문. TEXT는 항상 있고 IMAGE·SHARED_POST는 선택", nullable = true, example = "안녕하세요.")
    val body: String?,
    @field:Schema(description = "IMAGE의 이미지 번호. 그 외 null", nullable = true, example = "null")
    val imageSeq: Long?,
    @field:Schema(description = "IMAGE의 접근 URL. 그 외 null", nullable = true, example = "null")
    val imageUrl: String?,
    @field:Schema(
        description = "SHARED_POST의 공유 대상 종류. MISSING=실종 신고, FOUND=발견 신고, SHELTER_ANIMAL=보호동물. 그 외 null",
        nullable = true,
        example = "null",
    )
    val sharedTargetType: SharedTargetType?,
    @field:Schema(description = "SHARED_POST의 공유 대상 번호. 그 외 null", nullable = true, example = "null")
    val sharedTargetSeq: Long?,
    @field:Schema(description = "공유한 게시글이 삭제·비노출이면 true. SHARED_POST가 아니면 항상 false", example = "false")
    val sharedPostUnavailable: Boolean,
    @field:Schema(description = "보낸 시각", example = "2026-10-29T17:50:00+09:00")
    val regDttm: OffsetDateTime?,
)

data class ChatMessagePage(
    @field:Schema(description = "메시지 목록. 최신 메시지가 먼저(chatMessageSeq 내림차순)")
    val content: List<ChatMessageView>,
    @field:Schema(description = "다음(더 오래된) 페이지 커서. 다음 요청의 beforeMessageSeq에 넣는다. 마지막 페이지면 null", nullable = true, example = "3990")
    val nextBeforeMessageSeq: Long?,
    @field:Schema(description = "더 오래된 메시지가 있는지", example = "true")
    val hasNext: Boolean,
)

data class ReadResult(
    @field:Schema(description = "처리 후 내 읽음 포인터. 과거로 되돌리는 요청이면 기존 값", nullable = true, example = "4003")
    val lastReadMessageSeq: Long?,
    @field:Schema(description = "이 방의 남은 미읽음 수", example = "0")
    val unreadCount: Long,
    @field:Schema(description = "내 모든 방의 미읽음 합계", example = "121")
    val totalUnreadMessageCount: Long,
)

data class LeaveResult(
    @field:Schema(description = "숨김 포인터. 이 번호 이하 메시지는 내게 보이지 않는다. 방에 메시지가 없으면 null", nullable = true, example = "4003")
    val lastHiddenMessageSeq: Long?,
    @field:Schema(description = "내 모든 방의 미읽음 합계", example = "119")
    val totalUnreadMessageCount: Long,
)

data class UnreadCount(
    @field:Schema(description = "내 모든 방의 미읽음 메시지 합계(방별 unreadCount의 합)", example = "123")
    val totalUnreadMessageCount: Long,
)

enum class RoomUpdateType { MESSAGE, READ, LEFT, COUNTERPART_READ }

/**
 * /user/queue/room-updates 페이로드(Q454). room은 ChatRoomItem, LEFT(또는 목록에 안 보이는 방)는 {chatRoomSeq}만,
 * COUNTERPART_READ는 CounterpartRead
 */
data class RoomUpdate(val type: RoomUpdateType, val room: Any, val totalUnreadMessageCount: Long)

data class RoomRef(val chatRoomSeq: Long)

/** 상대가 읽음 포인터를 앞으로 옮겼을 때 보낸 쪽에 알리는 room(COUNTERPART_READ) */
data class CounterpartRead(val chatRoomSeq: Long, val counterpartLastReadMessageSeq: Long)
