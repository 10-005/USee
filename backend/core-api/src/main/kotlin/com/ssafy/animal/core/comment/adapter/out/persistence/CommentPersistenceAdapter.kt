package com.ssafy.animal.core.comment.adapter.out.persistence

import com.ssafy.animal.core.comment.application.port.out.AuthorRow
import com.ssafy.animal.core.comment.application.port.out.CommentRepository
import com.ssafy.animal.core.comment.domain.Comment
import com.ssafy.animal.core.comment.domain.CommentType
import com.ssafy.animal.core.comment.domain.FoundReportComment
import com.ssafy.animal.core.comment.domain.MissingReportComment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface MissingReportCommentJpaRepository : JpaRepository<MissingReportComment, Long> {
    fun findByPostSeqAndParentCommentSeqIsNullOrderByCommentSeqDesc(postSeq: Int): List<MissingReportComment>

    fun findByParentCommentSeqOrderByRegDttmAscCommentSeqAsc(parentCommentSeq: Long): List<MissingReportComment>

    @Query(
        """
        select c.parentCommentSeq, count(c) from MissingReportComment c
        where c.parentCommentSeq in :rootSeqs and c.isDeleted = false group by c.parentCommentSeq
        """,
    )
    fun countReplies(@Param("rootSeqs") rootSeqs: Collection<Long>): List<Array<Any>>

    // 게시물 엔티티는 실종 도메인 몫이라 네이티브로 읽는다. 공통 필터: 삭제 게시물·탈퇴 작성자 게시물 비노출
    @Query(
        nativeQuery = true,
        value = """
        select r.user_seq from missing_reports r join users u on u.user_seq = r.user_seq
        where r.missing_report_seq = :postSeq and r.is_deleted = false and u.is_deleted = false
        """,
    )
    fun findVisiblePostAuthor(@Param("postSeq") postSeq: Long): Int?

    @Query(
        nativeQuery = true,
        value = """
        select u.user_seq as userSeq, u.role as role, u.is_deleted as deleted, u.nickname as nickname,
               u.profile_image_seq as profileImageSeq, s.shelter_name as shelterName, s.profile_image_seq as shelterImageSeq
        from users u
        left join shelter_members m on m.user_seq = u.user_seq
        left join shelters s on s.shelter_seq = m.shelter_seq and s.is_deleted = false
        where u.user_seq in (:userSeqs)
        """,
    )
    fun findAuthors(@Param("userSeqs") userSeqs: Collection<Int>): List<AuthorRow>
}

interface FoundReportCommentJpaRepository : JpaRepository<FoundReportComment, Long> {
    fun findByPostSeqAndParentCommentSeqIsNullOrderByCommentSeqDesc(postSeq: Int): List<FoundReportComment>

    fun findByParentCommentSeqOrderByRegDttmAscCommentSeqAsc(parentCommentSeq: Long): List<FoundReportComment>

    @Query(
        """
        select c.parentCommentSeq, count(c) from FoundReportComment c
        where c.parentCommentSeq in :rootSeqs and c.isDeleted = false group by c.parentCommentSeq
        """,
    )
    fun countReplies(@Param("rootSeqs") rootSeqs: Collection<Long>): List<Array<Any>>

    @Query(
        nativeQuery = true,
        value = """
        select r.user_seq from found_reports r join users u on u.user_seq = r.user_seq
        where r.found_report_seq = :postSeq and r.is_deleted = false and u.is_deleted = false
        """,
    )
    fun findVisiblePostAuthor(@Param("postSeq") postSeq: Long): Int?
}

@Repository
class CommentPersistenceAdapter(
    private val missing: MissingReportCommentJpaRepository,
    private val found: FoundReportCommentJpaRepository,
) : CommentRepository {

    override fun saveAndFlush(comment: Comment): Comment = when (comment) {
        is MissingReportComment -> missing.saveAndFlush(comment)
        is FoundReportComment -> found.saveAndFlush(comment)
        else -> error("알 수 없는 댓글 타입: ${comment.javaClass}")
    }

    override fun findById(type: CommentType, commentSeq: Long): Comment? = when (type) {
        CommentType.MISSING_COMMENT -> missing.findByIdOrNull(commentSeq)
        CommentType.FOUND_COMMENT -> found.findByIdOrNull(commentSeq)
    }

    override fun findRoots(type: CommentType, postSeq: Long): List<Comment> {
        val seq = Math.toIntExact(postSeq)
        return when (type) {
            CommentType.MISSING_COMMENT -> missing.findByPostSeqAndParentCommentSeqIsNullOrderByCommentSeqDesc(seq)
            CommentType.FOUND_COMMENT -> found.findByPostSeqAndParentCommentSeqIsNullOrderByCommentSeqDesc(seq)
        }
    }

    override fun findReplies(type: CommentType, rootSeq: Long): List<Comment> = when (type) {
        CommentType.MISSING_COMMENT -> missing.findByParentCommentSeqOrderByRegDttmAscCommentSeqAsc(rootSeq)
        CommentType.FOUND_COMMENT -> found.findByParentCommentSeqOrderByRegDttmAscCommentSeqAsc(rootSeq)
    }

    override fun countReplies(type: CommentType, rootSeqs: Collection<Long>): Map<Long, Long> {
        if (rootSeqs.isEmpty()) return emptyMap()
        val rows = when (type) {
            CommentType.MISSING_COMMENT -> missing.countReplies(rootSeqs)
            CommentType.FOUND_COMMENT -> found.countReplies(rootSeqs)
        }
        return rows.associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
    }

    override fun findVisiblePostAuthor(type: CommentType, postSeq: Long): Int? = when (type) {
        CommentType.MISSING_COMMENT -> missing.findVisiblePostAuthor(postSeq)
        CommentType.FOUND_COMMENT -> found.findVisiblePostAuthor(postSeq)
    }

    override fun findAuthors(userSeqs: Collection<Int>): Map<Int, AuthorRow> =
        if (userSeqs.isEmpty()) emptyMap() else missing.findAuthors(userSeqs).associateBy { it.userSeq }
}
