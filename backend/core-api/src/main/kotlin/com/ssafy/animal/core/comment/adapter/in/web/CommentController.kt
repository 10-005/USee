package com.ssafy.animal.core.comment.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.comment.application.port.`in`.CommentUseCase
import com.ssafy.animal.core.comment.application.port.`in`.CreateCommentCommand
import com.ssafy.animal.core.comment.application.port.`in`.UpdateCommentCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Optional

/** 13장 댓글·답글. 권한은 로그인(USER·SHELTER 모두) */
@RestController
@RequestMapping("/api/core/v1/comments")
class CommentController(
    private val commentUseCase: CommentUseCase,
) {

    /** 13.1 루트 댓글 전량 배열(Q480) */
    @GetMapping
    fun listRoots(@RequestParam targetType: TargetType, @RequestParam targetSeq: Long) =
        commentUseCase.listRoots(targetType, targetSeq)

    /** 13.2 답글 전량 배열 */
    @GetMapping("/{commentType}/{commentSeq}/replies")
    fun listReplies(@PathVariable commentType: TargetType, @PathVariable commentSeq: Long) =
        commentUseCase.listReplies(commentType, commentSeq)

    /** 13.6 단건(알림 진입용) */
    @GetMapping("/{commentType}/{commentSeq}")
    fun get(@PathVariable commentType: TargetType, @PathVariable commentSeq: Long) = commentUseCase.get(commentType, commentSeq)

    /** 13.3 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreateCommentRequest) =
        commentUseCase.create(user.userSeq, request.toCommand())

    /** 13.4 */
    @PatchMapping("/{commentType}/{commentSeq}")
    fun update(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable commentType: TargetType,
        @PathVariable commentSeq: Long,
        @RequestBody request: UpdateCommentRequest,
    ) = commentUseCase.update(user.userSeq, commentType, commentSeq, UpdateCommentCommand(request.body, request.imageSeq))

    /** 13.5 */
    @DeleteMapping("/{commentType}/{commentSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable commentType: TargetType, @PathVariable commentSeq: Long) {
        commentUseCase.delete(user.userSeq, commentType, commentSeq)
    }
}

data class CreateCommentRequest(
    @field:NotNull
    val targetType: TargetType?,
    @field:NotNull
    val targetSeq: Long?,
    val parentCommentSeq: Long?,
    val body: String?,
    val imageSeq: Long?,
) {
    fun toCommand() = CreateCommentCommand(targetType!!, targetSeq!!, parentCommentSeq, body, imageSeq)
}

/** 13.4 부분 수정. 미전달은 null(유지), 명시적 null은 Optional.empty()(해제) */
data class UpdateCommentRequest(
    val body: Optional<String>? = null,
    val imageSeq: Optional<Long>? = null,
)
