package com.ssafy.animal.core.comment.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.CommentDeleted
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.comment.application.port.`in`.CommentAuthor
import com.ssafy.animal.core.comment.application.port.`in`.CommentDetail
import com.ssafy.animal.core.comment.application.port.`in`.CommentUseCase
import com.ssafy.animal.core.comment.application.port.`in`.CreateCommentCommand
import com.ssafy.animal.core.comment.application.port.`in`.CreatedComment
import com.ssafy.animal.core.comment.application.port.`in`.UpdateCommentCommand
import com.ssafy.animal.core.comment.application.port.out.CommentRepository
import com.ssafy.animal.core.comment.domain.Comment
import com.ssafy.animal.core.comment.domain.CommentErrorCode
import com.ssafy.animal.core.comment.domain.CommentType
import com.ssafy.animal.core.comment.domain.FoundReportComment
import com.ssafy.animal.core.comment.domain.MissingReportComment
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class CommentService(
    private val commentRepository: CommentRepository,
    private val imageUseCase: ImageUseCase,
    private val eventPublisher: EventPublisher,
) : CommentUseCase {

    @Transactional(readOnly = true)
    override fun listRoots(targetType: TargetType, targetSeq: Long): List<CommentDetail> {
        val type = CommentType.ofTarget(targetType)
        commentRepository.findVisiblePostAuthor(type, targetSeq) ?: throw BusinessException(CommentErrorCode.COMMENT_TARGET_NOT_FOUND)
        return details(type, commentRepository.findRoots(type, targetSeq))
    }

    @Transactional(readOnly = true)
    override fun listReplies(commentType: TargetType, commentSeq: Long): List<CommentDetail> {
        val type = CommentType.of(commentType)
        val root = loadVisible(type, commentSeq).takeIf { it.isRoot } ?: throw notFound()
        return details(type, commentRepository.findReplies(type, root.commentSeq!!))
    }

    @Transactional(readOnly = true)
    override fun get(commentType: TargetType, commentSeq: Long): CommentDetail {
        val type = CommentType.of(commentType)
        return details(type, listOf(loadVisible(type, commentSeq))).single()
    }

    @Transactional
    override fun create(userSeq: Long, command: CreateCommentCommand): CreatedComment {
        val type = CommentType.ofTarget(command.targetType)
        val body = command.body?.takeIf { it.isNotBlank() }
        if (body == null && command.imageSeq == null) throw BusinessException(CommentErrorCode.COMMENT_EMPTY)
        val postAuthor = commentRepository.findVisiblePostAuthor(type, command.targetSeq)
            ?: throw BusinessException(CommentErrorCode.COMMENT_TARGET_NOT_FOUND)

        val parent = command.parentCommentSeq?.let { parentSeq ->
            val parent = commentRepository.findById(type, parentSeq)?.takeIf { it.postSeq.toLong() == command.targetSeq }
                ?: throw notFound()
            if (!parent.isRoot) throw BusinessException(CommentErrorCode.COMMENT_DEPTH_EXCEEDED)
            // 삭제된 루트([가안])·탈퇴한 작성자의 루트에는 새 답글 불가. 기존 답글은 그대로 둔다
            if (parent.isDeleted || isWithdrawn(parent.userSeq)) throw BusinessException(CommentErrorCode.COMMENT_PARENT_UNAVAILABLE)
            parent
        }
        command.imageSeq?.let { imageUseCase.requireOwned(userSeq, it) }

        val postSeq = Math.toIntExact(command.targetSeq)
        val writer = Math.toIntExact(userSeq)
        val comment = commentRepository.saveAndFlush(
            when (type) {
                CommentType.MISSING_COMMENT -> MissingReportComment(postSeq, writer, parent?.commentSeq, body, command.imageSeq)
                CommentType.FOUND_COMMENT -> FoundReportComment(postSeq, writer, parent?.commentSeq, body, command.imageSeq)
            },
        )

        // 17.8: 루트는 게시물 작성자에게 COMMENT, 답글은 루트 작성자에게만 COMMENT_REPLY. 본인에게는 보내지 않는다
        val recipient = parent?.userSeq ?: postAuthor
        if (recipient != writer) {
            eventPublisher.publish(
                NotificationRequested(
                    recipientUserSeq = recipient.toLong(),
                    notificationType = if (parent == null) NotificationType.COMMENT else NotificationType.COMMENT_REPLY,
                    message = notificationMessage(body),
                    targetType = type.targetType,
                    targetSeq = command.targetSeq,
                    commentSeq = comment.commentSeq,
                    parentCommentSeq = parent?.commentSeq,
                ),
            )
        }
        return CreatedComment(type, comment.commentSeq!!, parent?.commentSeq)
    }

    @Transactional
    override fun update(userSeq: Long, commentType: TargetType, commentSeq: Long, command: UpdateCommentCommand): CommentDetail {
        val type = CommentType.of(commentType)
        val comment = loadOwn(type, commentSeq, userSeq)
        val body = command.body.orKeep(comment.body)?.takeIf { it.isNotBlank() }
        val oldImageSeq = comment.imageSeq
        val newImageSeq = command.imageSeq.orKeep(oldImageSeq)
        if (body == null && newImageSeq == null) throw BusinessException(CommentErrorCode.COMMENT_EMPTY)
        if (newImageSeq != null && newImageSeq != oldImageSeq) imageUseCase.requireOwned(userSeq, newImageSeq)

        comment.edit(body, newImageSeq, Math.toIntExact(userSeq))
        commentRepository.saveAndFlush(comment)
        if (oldImageSeq != null && oldImageSeq != newImageSeq) imageUseCase.deleteIfUnreferenced(oldImageSeq)
        return details(type, listOf(comment)).single()
    }

    @Transactional
    override fun delete(userSeq: Long, commentType: TargetType, commentSeq: Long) {
        val type = CommentType.of(commentType)
        val comment = loadOwn(type, commentSeq, userSeq)
        val oldImageSeq = comment.imageSeq
        comment.softDelete(Math.toIntExact(userSeq))
        commentRepository.saveAndFlush(comment)
        oldImageSeq?.let(imageUseCase::deleteIfUnreferenced)
        // 트랜잭션 안이라 커밋 후에만 나간다. 관련 알림 message를 "삭제된 댓글입니다"로(Q448)
        eventPublisher.publish(CommentDeleted(type.targetType, commentSeq))
    }

    /** 게시물이 노출 중인 댓글. 삭제된 댓글도 돌려준다(13.1·13.6 자리 유지) */
    private fun loadVisible(type: CommentType, commentSeq: Long): Comment {
        val comment = commentRepository.findById(type, commentSeq) ?: throw notFound()
        commentRepository.findVisiblePostAuthor(type, comment.postSeq.toLong()) ?: throw notFound()
        return comment
    }

    /** 수정·삭제 대상. 타인·삭제된 댓글은 404로 감춘다(Q423·Q434) */
    private fun loadOwn(type: CommentType, commentSeq: Long, userSeq: Long): Comment =
        loadVisible(type, commentSeq).takeIf { !it.isDeleted && it.userSeq.toLong() == userSeq } ?: throw notFound()

    private fun isWithdrawn(userSeq: Int) = commentRepository.findAuthors(listOf(userSeq))[userSeq]?.deleted ?: true

    private fun details(type: CommentType, comments: List<Comment>): List<CommentDetail> {
        if (comments.isEmpty()) return emptyList()
        val replyCounts = commentRepository.countReplies(type, comments.filter { it.isRoot }.map { it.commentSeq!! })
        val authors = commentRepository.findAuthors(comments.map { it.userSeq }.distinct())
            .mapValues { (seq, row) ->
                val role = Role.valueOf(row.role)
                when {
                    row.deleted -> CommentAuthor(seq.toLong(), WITHDRAWN_NAME, role, null)
                    role == Role.SHELTER -> CommentAuthor(seq.toLong(), row.shelterName, role, row.shelterImageSeq?.let(imageUseCase::findSummary))
                    else -> CommentAuthor(seq.toLong(), row.nickname, role, row.profileImageSeq?.let(imageUseCase::findSummary))
                }
            }
        // ponytail: 댓글 이미지는 건마다 findSummary 1회(PK 조회). 댓글이 많아져 느려지면 ImageUseCase에 일괄 조회 추가
        return comments.map { c ->
            CommentDetail(
                commentType = type,
                commentSeq = c.commentSeq!!,
                parentCommentSeq = c.parentCommentSeq,
                targetType = type.targetType,
                targetSeq = c.postSeq.toLong(),
                author = authors[c.userSeq] ?: CommentAuthor(c.userSeq.toLong(), WITHDRAWN_NAME, Role.USER, null),
                body = c.body.takeUnless { c.isDeleted },
                image = c.imageSeq?.takeUnless { c.isDeleted }?.let(imageUseCase::findSummary),
                isDeleted = c.isDeleted,
                replyCount = replyCounts[c.commentSeq] ?: 0,
                regDttm = c.regDttm,
                modDttm = c.modDttm,
            )
        }
    }

    private fun notFound() = BusinessException(CommentErrorCode.COMMENT_NOT_FOUND)

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)

    companion object {
        const val WITHDRAWN_NAME = "(탈퇴한 사용자)"
        private const val MESSAGE_MAX = 100

        /** 17.1 COMMENT 계열 body 템플릿: 본문 최대 100자(초과 시 …), 사진만 있으면 "사진을 남겼어요" */
        fun notificationMessage(body: String?): String = when {
            body == null -> "사진을 남겼어요"
            body.length > MESSAGE_MAX -> body.take(MESSAGE_MAX) + "…"
            else -> body
        }
    }
}
