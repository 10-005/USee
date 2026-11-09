package com.ssafy.animal.chat.adapter.`in`.web

import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.chat.application.port.`in`.CreateRoomCommand
import com.ssafy.animal.chat.application.port.`in`.CreateRoomResult
import com.ssafy.animal.chat.domain.ParticipantType
import com.ssafy.animal.common.auth.AuthUser
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
@RestController
@RequestMapping("/api/chat/v1/chats")
class ChatController(
    private val chatUseCase: ChatUseCase,
) {

    /** 14.1 커서 목록(Q454) */
    @GetMapping("/rooms")
    fun getRooms(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(required = false) beforeLastMessageSeq: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ) = chatUseCase.getRooms(user, unreadOnly, beforeLastMessageSeq, size)

    /** 14.2 최초 생성 201, 기존 방 재사용 200 */
    @PostMapping("/rooms")
    fun createRoom(
        @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: CreateRoomRequest,
    ): ResponseEntity<CreateRoomResult> {
        val result = chatUseCase.createRoom(user, request.toCommand())
        return ResponseEntity.status(if (result.reused) HttpStatus.OK else HttpStatus.CREATED).body(result)
    }

    /** 14.3 */
    @GetMapping("/rooms/{chatRoomSeq}")
    fun getRoom(@AuthenticationPrincipal user: AuthUser, @PathVariable chatRoomSeq: Long) = chatUseCase.getRoom(user, chatRoomSeq)

    /** 14.4 beforeMessageSeq 역방향 커서 */
    @GetMapping("/rooms/{chatRoomSeq}/messages")
    fun getMessages(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable chatRoomSeq: Long,
        @RequestParam(required = false) beforeMessageSeq: Long?,
        @RequestParam(defaultValue = "30") size: Int,
    ) = chatUseCase.getMessages(user, chatRoomSeq, beforeMessageSeq, size)

    /** 14.6 */
    @PatchMapping("/rooms/{chatRoomSeq}/read")
    fun read(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable chatRoomSeq: Long,
        @Valid @RequestBody request: ReadRequest,
    ) = chatUseCase.read(user, chatRoomSeq, request.lastReadMessageSeq!!)

    /** 14.7 본문은 {} */
    @PostMapping("/rooms/{chatRoomSeq}/leave")
    fun leave(@AuthenticationPrincipal user: AuthUser, @PathVariable chatRoomSeq: Long) = chatUseCase.leave(user, chatRoomSeq)

    /** 14.8 */
    @GetMapping("/unread-count")
    fun getUnreadCount(@AuthenticationPrincipal user: AuthUser) = chatUseCase.getUnreadCount(user)
}

data class CreateRoomRequest(
    @field:NotNull
    val targetType: ParticipantType?,
    val targetUserSeq: Long?,
    val targetShelterSeq: Long?,
) {
    fun toCommand() = CreateRoomCommand(targetType!!, targetUserSeq, targetShelterSeq)
}

data class ReadRequest(
    @field:NotNull
    val lastReadMessageSeq: Long?,
)
