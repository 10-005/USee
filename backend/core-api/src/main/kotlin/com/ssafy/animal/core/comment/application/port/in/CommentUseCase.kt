package com.ssafy.animal.core.comment.application.port.`in`

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.comment.domain.CommentType
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import java.time.OffsetDateTime
import java.util.Optional

interface CommentUseCase {
    /** 13.1 게시물의 루트 댓글 전량(commentSeq desc, 삭제된 루트 포함). 게시물 없음·비노출 404 COMMENT_TARGET_NOT_FOUND */
    fun listRoots(targetType: TargetType, targetSeq: Long): List<CommentDetail>

    /** 13.2 루트의 답글 전량(regDttm asc). 루트가 없거나 답글이면 404 COMMENT_NOT_FOUND */
    fun listReplies(commentType: TargetType, commentSeq: Long): List<CommentDetail>

    /** 13.6 단건. 삭제된 댓글도 200(isDeleted). 없음·게시물 비노출 404 COMMENT_NOT_FOUND */
    fun get(commentType: TargetType, commentSeq: Long): CommentDetail

    /** 13.3 댓글·답글 작성 후 COMMENT/COMMENT_REPLY 알림 요청(17.8) */
    fun create(userSeq: Long, command: CreateCommentCommand): CreatedComment

    /** 13.4 작성자 본인만. 아니면 404 COMMENT_NOT_FOUND */
    fun update(userSeq: Long, commentType: TargetType, commentSeq: Long, command: UpdateCommentCommand): CommentDetail

    /** 13.5 소프트 삭제 후 CommentDeleted 발행 */
    fun delete(userSeq: Long, commentType: TargetType, commentSeq: Long)
}

data class CreateCommentCommand(
    val targetType: TargetType,
    val targetSeq: Long,
    val parentCommentSeq: Long?,
    val body: String?,
    val imageSeq: Long?,
)

/** null = 미전달(유지), Optional.empty() = 명시적 null(해제) */
data class UpdateCommentCommand(
    val body: Optional<String>? = null,
    val imageSeq: Optional<Long>? = null,
)

data class CreatedComment(
    val commentType: CommentType,
    val commentSeq: Long,
    val parentCommentSeq: Long?,
)

/** 13.1 댓글 DTO. 삭제된 댓글은 body·image null */
data class CommentDetail(
    val commentType: CommentType,
    val commentSeq: Long,
    val parentCommentSeq: Long?,
    val targetType: TargetType,
    val targetSeq: Long,
    val author: CommentAuthor,
    val body: String?,
    val image: ImageSummary?,
    val isDeleted: Boolean,
    val replyCount: Long,
    val regDttm: OffsetDateTime?,
    val modDttm: OffsetDateTime?,
)

/** 1.4 ActorSummary. 탈퇴 작성자는 displayName "(탈퇴한 사용자)"·profileImage null, SHELTER는 보호소명·보호소 사진 */
data class CommentAuthor(
    val userSeq: Long,
    val displayName: String?,
    val role: Role,
    val profileImage: ImageSummary?,
)
