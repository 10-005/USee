package com.ssafy.animal.core.comment.application.port.`in`

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.comment.domain.CommentType
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import io.swagger.v3.oas.annotations.media.Schema
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
    @field:Schema(description = COMMENT_TYPE_DESCRIPTION, example = "MISSING_COMMENT")
    val commentType: CommentType,
    @field:Schema(description = "만든 댓글·답글 번호", example = "45")
    val commentSeq: Long,
    @field:Schema(description = "답글이면 루트 댓글 번호, 루트 댓글이면 null", nullable = true, example = "44")
    val parentCommentSeq: Long?,
)

/** 13.1 댓글 DTO. 삭제된 댓글은 body·image null */
data class CommentDetail(
    @field:Schema(description = COMMENT_TYPE_DESCRIPTION, example = "MISSING_COMMENT")
    val commentType: CommentType,
    @field:Schema(description = "댓글 번호. commentType마다 번호 체계가 따로다", example = "44")
    val commentSeq: Long,
    @field:Schema(description = "답글이면 루트 댓글 번호, 루트 댓글이면 null", nullable = true, example = "null")
    val parentCommentSeq: Long?,
    @field:Schema(description = "게시물 종류. MISSING: 실종 신고, FOUND: 발견 신고", example = "MISSING")
    val targetType: TargetType,
    @field:Schema(description = "게시물 번호(missingReportSeq 또는 foundReportSeq)", example = "101")
    val targetSeq: Long,
    @field:Schema(description = "작성자")
    val author: CommentAuthor,
    @field:Schema(description = "본문. 삭제된 댓글이거나 사진만 있으면 null", nullable = true, example = "근처에서 본 것 같아요.")
    val body: String?,
    @field:Schema(description = "첨부 사진. 없거나 삭제된 댓글이면 null", nullable = true)
    val image: ImageSummary?,
    @get:Schema(name = "isDeleted", description = "삭제 여부. true면 body·image가 null이고 화면은 삭제 안내를 보여 준다", example = "false")
    val isDeleted: Boolean,
    @field:Schema(description = "삭제되지 않은 답글 수. 답글 자신은 항상 0", example = "2")
    val replyCount: Long,
    @field:Schema(description = "작성 시각", example = "2026-10-29T17:50:00+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "마지막 수정·삭제 시각. 수정한 적 없으면 null", nullable = true, example = "null")
    val modDttm: OffsetDateTime?,
)

/** 1.4 ActorSummary. 탈퇴 작성자는 displayName "(탈퇴한 사용자)"·profileImage null, SHELTER는 보호소명·보호소 사진 */
data class CommentAuthor(
    @field:Schema(description = "작성자 userSeq", example = "17")
    val userSeq: Long,
    @field:Schema(description = "표시 이름. USER는 닉네임, SHELTER는 소속 보호소명, 탈퇴 작성자는 \"(탈퇴한 사용자)\"", nullable = true, example = "반려가족")
    val displayName: String?,
    @field:Schema(description = "역할. USER: 일반 회원, SHELTER: 보호소 담당자", example = "USER")
    val role: Role,
    @field:Schema(description = "프로필 사진. USER는 회원 사진, SHELTER는 보호소 사진. 없거나 탈퇴 작성자면 null", nullable = true)
    val profileImage: ImageSummary?,
)

private const val COMMENT_TYPE_DESCRIPTION = "댓글 종류. MISSING_COMMENT: 실종 신고 댓글, FOUND_COMMENT: 발견 신고 댓글"
