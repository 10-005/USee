package com.ssafy.animal.chat.adapter.`in`.web

import com.ssafy.animal.chat.application.port.`in`.ChatMessagePage
import com.ssafy.animal.chat.application.port.`in`.ChatRoomDetail
import com.ssafy.animal.chat.application.port.`in`.ChatRoomPage
import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.application.port.`in`.CounterpartProfile
import com.ssafy.animal.chat.application.port.`in`.CreateRoomCommand
import com.ssafy.animal.chat.application.port.`in`.CreateRoomResult
import com.ssafy.animal.chat.application.port.`in`.LeaveResult
import com.ssafy.animal.chat.application.port.`in`.ReadResult
import com.ssafy.animal.chat.application.port.`in`.UnreadCount
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 명세 14장 REST(메시지 전송은 STOMP 14.5만). USER·SHELTER 공통 */
@Tag(
    name = "채팅",
    description = "대화방 목록·생성·상세, 메시지 조회, 읽음, 나가기, 미읽음 수. " +
        "메시지 전송은 REST가 아니라 STOMP(엔드포인트 /api/chat/v1/ws, SEND /app/rooms/{chatRoomSeq}/messages)로 한다",
)
@RestController
@RequestMapping("/api/chat/v1/chats")
class ChatController(
    private val chatUseCase: ChatUseCase,
) {

    /** 14.1 커서 목록(Q454) */
    @Operation(
        summary = "내 대화방 목록",
        description = "- 로그인 필요(USER·SHELTER). SHELTER 계정은 보호소로 참여한 방이 나온다.\n" +
            "- 커서 방식(page·sort 없음). 정렬은 마지막 메시지 번호 내림차순 고정.\n" +
            "- 첫 페이지는 `beforeLastMessageSeq` 생략. 다음 페이지는 응답의 `nextBeforeLastMessageSeq`를 그대로 넣는다. `hasNext=false`면 `nextBeforeLastMessageSeq`는 null.\n" +
            "- `size` 기본 20. 1 미만은 1, 100 초과는 100으로 맞춘다.\n" +
            "- `unreadOnly=true`면 미읽음이 1 이상인 방만.\n" +
            "- 나가기 이후 새 메시지가 있는 방만 나온다. 메시지 없이 만들기만 한 방, 나간 뒤 새 메시지가 없는 방은 빠진다.\n" +
            "- 실시간 갱신은 STOMP `/user/queue/room-updates`로 받는다. 재연결·포그라운드 복귀 때 첫 페이지와 `GET /api/chat/v1/chats/unread-count`를 다시 조회한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 방이 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatRoomPageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "대화방 목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"chatRoomSeq":501,"roomType":"SHELTER","counterpart":{"type":"SHELTER","userSeq":null,"shelterSeq":7,"displayName":"행복동물보호센터","profileImage":{"imageSeq":1201,"imageUrl":"https://cdn.example.com/images/1201.jpg","displayOrder":1}},"lastMessage":{"chatMessageSeq":4002,"messageType":"TEXT","body":"확인했습니다."},"lastMessageSeq":4002,"lastMessageDttm":"2026-10-29T17:50:00+09:00","unreadCount":2}],"nextBeforeLastMessageSeq":4002,"hasNext":true},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "마지막 페이지",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[],"nextBeforeLastMessageSeq":null,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "unreadOnly·beforeLastMessageSeq·size 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "size 타입 오류", value = INVALID_SIZE_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/rooms")
    fun getRooms(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "true면 미읽음이 있는 방만", example = "false")
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @Parameter(description = "커서. 이 번호보다 마지막 메시지가 오래된 방만 준다. 첫 페이지는 생략, 다음은 nextBeforeLastMessageSeq", example = "4002")
        @RequestParam(required = false) beforeLastMessageSeq: Long?,
        @Parameter(description = "한 번에 받을 방 수. 기본 20, 1~100으로 맞춘다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ) = chatUseCase.getRooms(user, unreadOnly, beforeLastMessageSeq, size)

    /** 14.2 최초 생성 201, 기존 방 재사용 200 */
    @Operation(
        summary = "대화방 생성 또는 기존 방 재사용",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 1:1만. USER↔USER, USER↔SHELTER. 보호소↔보호소는 없다. 상대 조합당 방 하나.\n" +
            "- `targetType=USER`면 `targetUserSeq`만, `targetType=SHELTER`면 `targetShelterSeq`만 보낸다. 둘 다 보내거나 맞는 쪽이 없으면 400.\n" +
            "- SHELTER 계정은 회원(`targetType=USER`)에게만 열 수 있다.\n" +
            "- 보호소 계정과 대화하려면 회원 번호가 아니라 `targetType=SHELTER`로 연다(보호소 계정의 userSeq로 열면 409).\n" +
            "- 처음 만들면 201(`reused=false`), 이미 있으면 200(`reused=true`). 예전에 나간 방이면 나가기 이전 메시지는 계속 숨겨진다.\n" +
            "- 방만 만들고 메시지가 없으면 대화방 목록에는 나오지 않는다.\n" +
            "- 같은 상대에게 동시에 처음 열면 한쪽이 500이 날 수 있다. 다시 호출하면 기존 방(200)을 준다.\n" +
            "- 다음 호출: `GET /api/chat/v1/chats/rooms/{chatRoomSeq}`, STOMP로 `/topic/rooms/{chatRoomSeq}` 구독.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "새 방 생성",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatCreateRoomSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "새 방",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"chatRoomSeq":501,"roomType":"SHELTER","reused":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "200",
            description = "기존 방 재사용",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatCreateRoomSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "기존 방",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"chatRoomSeq":501,"roomType":"SHELTER","reused":true},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "targetType 누락, targetType과 대상 번호 불일치, 또는 본문을 읽을 수 없음(targetType 값 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "targetType 누락", value = INVALID_TARGET_TYPE_NULL),
                        ExampleObject(name = "대상 번호 불일치", value = INVALID_TARGET_MISMATCH),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 회원, 또는 없거나 삭제된 보호소",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CHAT_TARGET_NOT_FOUND", value = CHAT_TARGET_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "지원하지 않는 상대(자기 자신, 보호소 계정의 userSeq, SHELTER 계정이 보호소 대상 지정), 또는 탈퇴한 회원",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "CHAT_UNSUPPORTED_PARTICIPANTS", value = CHAT_UNSUPPORTED_PARTICIPANTS),
                        ExampleObject(name = "CHAT_COUNTERPART_DELETED", value = CHAT_COUNTERPART_DELETED),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/rooms")
    fun createRoom(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: CreateRoomRequest,
    ): ResponseEntity<CreateRoomResult> {
        val result = chatUseCase.createRoom(user, request.toCommand())
        return ResponseEntity.status(if (result.reused) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    /** 14.3 */
    @Operation(
        summary = "대화방 상세",
        description = "- 로그인 필요. 이 방의 참여자만.\n" +
            "- 없는 방이거나 참여자가 아니면 404(존재를 감춘다).\n" +
            "- 나간 방도 참여자이므로 조회된다.\n" +
            "- 다음 호출: 메시지 내역 `GET /api/chat/v1/chats/rooms/{chatRoomSeq}/messages`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatRoomDetailSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "대화방 상세",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"chatRoomSeq":501,"roomType":"USER","counterpart":{"type":"USER","userSeq":18,"shelterSeq":null,"displayName":"(탈퇴한 사용자)","profileImage":null},"lastReadMessageSeq":4000,"lastHiddenMessageSeq":null,"unreadCount":2},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "chatRoomSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "chatRoomSeq 타입 오류", value = INVALID_ROOM_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 방이거나 참여자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CHAT_ROOM_NOT_FOUND", value = CHAT_ROOM_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/rooms/{chatRoomSeq}")
    fun getRoom(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대화방 번호", example = "501") @PathVariable chatRoomSeq: Long,
    ) = chatUseCase.getRoom(user, chatRoomSeq)

    /** 14.9 보호소 웹 채팅창의 상대 정보 카드 */
    @Operation(
        summary = "상대 회원 정보와 입양 신청 (보호소 전용)",
        description = "- 로그인 필요(SHELTER만). USER가 부르면 403 `AUTH_FORBIDDEN`.\n" +
            "- 내 보호소가 참여한 방이어야 한다. 없는 방이거나 참여자가 아니면 404.\n" +
            "- `applications`: 이 회원이 **내 보호소에** 낸 입양 신청. 12.9와 같이 DRAFT·CANCELED는 빼고 제출일 최신순, 페이지 없는 배열. 없으면 `[]`.\n" +
            "- `realName`: 회원 계정의 본명. **`applications`가 1건 이상일 때만** 주고, 없으면 null(입양 신청으로 엮이지 않은 회원의 실명은 보이지 않음).\n" +
            "- 탈퇴한 회원은 `nickname`이 \"(탈퇴한 사용자)\", `realName`·`profileImage`는 null. 신청 내역은 그대로 나온다.\n" +
            "- 신청서 상세는 `applicationSeq`로 `GET /api/core/v1/adoption-applications/{applicationSeq}`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatCounterpartProfileSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "신청 내역 있음",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":1,"nickname":"justin","realName":"신지환","profileImage":{"imageSeq":19,"imageUrl":"https://cdn.example.com/images/19.jpg","displayOrder":1},"applications":[{"applicationSeq":9,"speciesName":"개","breedName":"말티즈","status":"SUBMITTED","submittedDttm":"2026-11-07T01:59:35+09:00"},{"applicationSeq":10,"speciesName":"고양이","breedName":"믹스묘","status":"APPROVED","submittedDttm":"2026-11-04T01:59:35+09:00"}]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "신청 내역 없음",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":18,"nickname":"반려가족","realName":null,"profileImage":null,"applications":[]},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "chatRoomSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "chatRoomSeq 타입 오류", value = INVALID_ROOM_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "SHELTER가 아닌 계정",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 방이거나 내 보호소가 참여자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CHAT_ROOM_NOT_FOUND", value = CHAT_ROOM_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/rooms/{chatRoomSeq}/counterpart")
    fun getCounterpartProfile(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대화방 번호", example = "501") @PathVariable chatRoomSeq: Long,
    ) = chatUseCase.getCounterpartProfile(user, chatRoomSeq)

    /** 14.4 beforeMessageSeq 역방향 커서 */
    @Operation(
        summary = "메시지 내역",
        description = "- 로그인 필요. 이 방의 참여자만.\n" +
            "- 역방향 커서(page·sort 없음). 최신 메시지부터 chatMessageSeq 내림차순으로 준다.\n" +
            "- 첫 페이지는 `beforeMessageSeq` 생략. 더 오래된 메시지는 응답의 `nextBeforeMessageSeq`를 넣어 부른다.\n" +
            "- `size` 기본 30. 1 미만은 1, 100 초과는 100으로 맞춘다.\n" +
            "- 내가 나가기 한 시점 이후 메시지만 준다.\n" +
            "- `clientMessageId`는 REST에서 항상 null. 공유 게시글이 삭제·비노출이면 `sharedPostUnavailable=true`.\n" +
            "- 새 메시지는 STOMP `/topic/rooms/{chatRoomSeq}` 구독으로 받는다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 메시지가 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatMessagePageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "메시지 내역",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"chatMessageSeq":4003,"clientMessageId":null,"chatRoomSeq":501,"senderUserSeq":17,"senderDisplayName":"반려가족","messageType":"SHARED_POST","body":null,"imageSeq":null,"imageUrl":null,"sharedTargetType":"FOUND","sharedTargetSeq":210,"sharedPostUnavailable":false,"regDttm":"2026-10-29T17:50:00+09:00"},{"chatMessageSeq":4002,"clientMessageId":null,"chatRoomSeq":501,"senderUserSeq":18,"senderDisplayName":"행복동물보호센터","messageType":"TEXT","body":"확인했습니다.","imageSeq":null,"imageUrl":null,"sharedTargetType":null,"sharedTargetSeq":null,"sharedPostUnavailable":false,"regDttm":"2026-10-29T17:49:00+09:00"}],"nextBeforeMessageSeq":4002,"hasNext":true},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "beforeMessageSeq가 이 방의 메시지가 아님, 또는 경로·쿼리 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "CHAT_MESSAGE_NOT_IN_ROOM", value = CHAT_MESSAGE_NOT_IN_ROOM),
                        ExampleObject(name = "chatRoomSeq 타입 오류", value = INVALID_ROOM_SEQ_TYPE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 방이거나 참여자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CHAT_ROOM_NOT_FOUND", value = CHAT_ROOM_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/rooms/{chatRoomSeq}/messages")
    fun getMessages(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대화방 번호", example = "501") @PathVariable chatRoomSeq: Long,
        @Parameter(description = "커서. 이 번호보다 오래된 메시지만 준다. 이 방의 메시지 번호여야 한다. 첫 페이지는 생략", example = "4002")
        @RequestParam(required = false) beforeMessageSeq: Long?,
        @Parameter(description = "한 번에 받을 메시지 수. 기본 30, 1~100으로 맞춘다", example = "30")
        @RequestParam(defaultValue = "30") size: Int,
    ) = chatUseCase.getMessages(user, chatRoomSeq, beforeMessageSeq, size)

    /** 14.6 */
    @Operation(
        summary = "읽음 처리",
        description = "- 로그인 필요. 이 방의 참여자만.\n" +
            "- 내 읽음 포인터를 `lastReadMessageSeq`로 옮긴다. 이 방의 메시지 번호여야 한다.\n" +
            "- 멱등: 현재 값 이하로 되돌리는 요청은 무시하고 현재 값과 미읽음 수를 200으로 준다.\n" +
            "- 처리 후 STOMP `/user/queue/room-updates`로 내 다른 기기에 `READ` 갱신을 보낸다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "처리 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatReadSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "읽음 처리",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"lastReadMessageSeq":4003,"unreadCount":0,"totalUnreadMessageCount":121},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "lastReadMessageSeq 누락, 이 방의 메시지가 아님, chatRoomSeq 타입 오류, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "lastReadMessageSeq 누락", value = INVALID_LAST_READ_NULL),
                        ExampleObject(name = "CHAT_MESSAGE_NOT_IN_ROOM", value = CHAT_MESSAGE_NOT_IN_ROOM),
                        ExampleObject(name = "chatRoomSeq 타입 오류", value = INVALID_ROOM_SEQ_TYPE),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 방이거나 참여자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CHAT_ROOM_NOT_FOUND", value = CHAT_ROOM_NOT_FOUND)],
                ),
            ],
        ),
    )
    @PatchMapping("/rooms/{chatRoomSeq}/read")
    fun read(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대화방 번호", example = "501") @PathVariable chatRoomSeq: Long,
        @Valid @RequestBody request: ReadRequest,
    ) = chatUseCase.read(user, chatRoomSeq, request.lastReadMessageSeq!!)

    /** 14.7 본문은 {} */
    @Operation(
        summary = "대화방 나가기",
        description = "- 로그인 필요. 이 방의 참여자만.\n" +
            "- 요청 본문은 받지 않는다(보내도 무시).\n" +
            "- 방의 마지막 메시지까지를 나에게만 숨긴다. 방·메시지는 지우지 않고 상대에게는 변화가 없다.\n" +
            "- 나간 방은 새 메시지가 올 때까지 대화방 목록에서 빠진다. 다시 대화하면 같은 방을 쓰고 이후 메시지만 보인다.\n" +
            "- 멱등: 다시 호출해도 200. 메시지가 없는 방이면 `lastHiddenMessageSeq`는 null일 수 있다.\n" +
            "- 처리 후 STOMP `/user/queue/room-updates`로 내 기기에 `LEFT` 갱신을 보낸다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "처리 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatLeaveSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "나가기",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"lastHiddenMessageSeq":4003,"totalUnreadMessageCount":119},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "chatRoomSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "chatRoomSeq 타입 오류", value = INVALID_ROOM_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 방이거나 참여자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "CHAT_ROOM_NOT_FOUND", value = CHAT_ROOM_NOT_FOUND)],
                ),
            ],
        ),
    )
    @PostMapping("/rooms/{chatRoomSeq}/leave")
    fun leave(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대화방 번호", example = "501") @PathVariable chatRoomSeq: Long,
    ) = chatUseCase.leave(user, chatRoomSeq)

    /** 14.8 */
    @Operation(
        summary = "전체 미읽음 수",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 내 모든 방의 미읽음 메시지 합계. 대화방 목록의 방별 `unreadCount`를 더한 값과 같다.\n" +
            "- 화면 진입·재연결·포그라운드 복귀 때 대화방 목록 첫 페이지와 함께 조회하고, 그 사이는 STOMP `room-updates`의 `totalUnreadMessageCount`로 갱신한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ChatUnreadCountSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "미읽음 수",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"totalUnreadMessageCount":123},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/unread-count")
    fun getUnreadCount(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) = chatUseCase.getUnreadCount(user)

    private companion object {
        // 문서 예시 전용
        const val INVALID_SIZE_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"size","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_ROOM_SEQ_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"chatRoomSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_TARGET_TYPE_NULL =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetType","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_TARGET_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetUserSeq","reason":"targetType에 맞는 대상 하나만 보내야 합니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_LAST_READ_NULL =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"lastReadMessageSeq","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CHAT_MESSAGE_NOT_IN_ROOM =
            """{"status":400,"code":"CHAT_MESSAGE_NOT_IN_ROOM","message":"이 대화방의 메시지가 아닙니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CHAT_ROOM_NOT_FOUND =
            """{"status":404,"code":"CHAT_ROOM_NOT_FOUND","message":"대화방을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CHAT_TARGET_NOT_FOUND =
            """{"status":404,"code":"CHAT_TARGET_NOT_FOUND","message":"대화 상대를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CHAT_UNSUPPORTED_PARTICIPANTS =
            """{"status":409,"code":"CHAT_UNSUPPORTED_PARTICIPANTS","message":"지원하지 않는 대화 상대입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CHAT_COUNTERPART_DELETED =
            """{"status":409,"code":"CHAT_COUNTERPART_DELETED","message":"탈퇴한 상대와는 대화할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class CreateRoomRequest(
    @field:Schema(description = "상대 종류(필수). USER=회원, SHELTER=보호소", example = "SHELTER")
    @field:NotNull
    val targetType: ParticipantType?,
    @field:Schema(description = "상대 회원 번호. targetType=USER일 때만 보낸다", nullable = true, example = "null")
    val targetUserSeq: Long?,
    @field:Schema(description = "상대 보호소 번호. targetType=SHELTER일 때만 보낸다", nullable = true, example = "7")
    val targetShelterSeq: Long?,
) {
    fun toCommand() = CreateRoomCommand(targetType!!, targetUserSeq, targetShelterSeq)
}

data class ReadRequest(
    @field:Schema(description = "마지막으로 읽은 메시지 번호(필수). 이 방의 메시지여야 한다", example = "4003")
    @field:NotNull
    val lastReadMessageSeq: Long?,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ChatRoomPageSuccessSchema : SuccessResponseSchema<ChatRoomPage>()
abstract class ChatCreateRoomSuccessSchema : SuccessResponseSchema<CreateRoomResult>()
abstract class ChatRoomDetailSuccessSchema : SuccessResponseSchema<ChatRoomDetail>()
abstract class ChatCounterpartProfileSuccessSchema : SuccessResponseSchema<CounterpartProfile>()
abstract class ChatMessagePageSuccessSchema : SuccessResponseSchema<ChatMessagePage>()
abstract class ChatReadSuccessSchema : SuccessResponseSchema<ReadResult>()
abstract class ChatLeaveSuccessSchema : SuccessResponseSchema<LeaveResult>()
abstract class ChatUnreadCountSuccessSchema : SuccessResponseSchema<UnreadCount>()
