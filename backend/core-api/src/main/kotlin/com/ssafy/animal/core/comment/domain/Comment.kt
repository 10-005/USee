package com.ssafy.animal.core.comment.domain

import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.persistence.BaseTimeEntity
import com.ssafy.animal.common.response.ErrorDetail
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import org.springframework.http.HttpStatus

/** 13장 타입 매핑. 게시물 targetType ↔ commentType ↔ 테이블. SHELTER_ANIMAL 댓글은 Q477로 없음 */
enum class CommentType(val targetType: TargetType) {
    MISSING_COMMENT(TargetType.MISSING),
    FOUND_COMMENT(TargetType.FOUND);

    companion object {
        /** 게시물 targetType(MISSING/FOUND)으로. 그 밖은 400 COMMON_INVALID_INPUT */
        fun ofTarget(targetType: TargetType) =
            entries.firstOrNull { it.targetType == targetType } ?: throw invalid("targetType", targetType)

        /** 경로의 commentType(MISSING_COMMENT/FOUND_COMMENT). 그 밖은 400 COMMON_INVALID_INPUT */
        fun of(commentType: TargetType) =
            entries.firstOrNull { it.name == commentType.name } ?: throw invalid("commentType", commentType)

        private fun invalid(field: String, value: TargetType) =
            BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, "허용하지 않는 값입니다: $value")))
    }
}

/** API 명세 13장·19장 COMMENT_ 코드 */
enum class CommentErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    COMMENT_EMPTY(HttpStatus.BAD_REQUEST, "댓글 내용이나 사진 중 하나는 있어야 합니다."),
    COMMENT_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "답글에는 답글을 달 수 없습니다."),
    COMMENT_PARENT_UNAVAILABLE(HttpStatus.CONFLICT, "답글을 달 수 없는 댓글입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
    COMMENT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 달 게시물을 찾을 수 없습니다."),
}

/** DB 명세 28·29. 두 댓글 테이블의 공통 컬럼. PK·게시물 FK 이름만 테이블마다 다르다 */
@MappedSuperclass
abstract class Comment(
    userSeq: Int,
    parentCommentSeq: Long?,
    body: String?,
    imageSeq: Long?,
) : BaseTimeEntity() {

    abstract val commentSeq: Long?
    abstract val postSeq: Int

    @Column(nullable = false, updatable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(updatable = false)
    var parentCommentSeq: Long? = parentCommentSeq
        protected set

    var body: String? = body
        protected set

    var imageSeq: Long? = imageSeq
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var modUsrSeq: Int? = null
        protected set

    val isRoot: Boolean get() = parentCommentSeq == null

    fun edit(body: String?, imageSeq: Long?, actorUserSeq: Int) {
        this.body = body
        this.imageSeq = imageSeq
        modUsrSeq = actorUserSeq
    }

    /** 13.5 소프트 삭제. 이미지 참조는 끊어야 공통 이미지 정리(Q427)가 원본을 지울 수 있다. 본문은 신고 보관용으로 남긴다 */
    fun softDelete(actorUserSeq: Int) {
        isDeleted = true
        imageSeq = null
        modUsrSeq = actorUserSeq
    }
}

@Entity
@Table(name = "missing_report_comments")
class MissingReportComment(
    postSeq: Int,
    userSeq: Int,
    parentCommentSeq: Long?,
    body: String?,
    imageSeq: Long?,
) : Comment(userSeq, parentCommentSeq, body, imageSeq) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "missing_report_comment_seq")
    override var commentSeq: Long? = null
        protected set

    @Column(name = "missing_report_seq", nullable = false, updatable = false)
    override var postSeq: Int = postSeq
        protected set
}

@Entity
@Table(name = "found_report_comments")
class FoundReportComment(
    postSeq: Int,
    userSeq: Int,
    parentCommentSeq: Long?,
    body: String?,
    imageSeq: Long?,
) : Comment(userSeq, parentCommentSeq, body, imageSeq) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "found_report_comment_seq")
    override var commentSeq: Long? = null
        protected set

    @Column(name = "found_report_seq", nullable = false, updatable = false)
    override var postSeq: Int = postSeq
        protected set
}
