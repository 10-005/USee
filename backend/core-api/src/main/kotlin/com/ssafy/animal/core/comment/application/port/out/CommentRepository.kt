package com.ssafy.animal.core.comment.application.port.out

import com.ssafy.animal.core.comment.domain.Comment
import com.ssafy.animal.core.comment.domain.CommentType

interface CommentRepository {
    fun saveAndFlush(comment: Comment): Comment

    fun findById(type: CommentType, commentSeq: Long): Comment?

    /** 루트 댓글 전량, commentSeq desc */
    fun findRoots(type: CommentType, postSeq: Long): List<Comment>

    /** 루트의 답글 전량, regDttm asc·commentSeq asc */
    fun findReplies(type: CommentType, rootSeq: Long): List<Comment>

    /** 루트별 비삭제 답글 수. 답글이 없는 루트는 결과에 없다 */
    fun countReplies(type: CommentType, rootSeqs: Collection<Long>): Map<Long, Long>

    /** 노출 중인 게시물(비삭제·작성자 비탈퇴)의 작성자 userSeq. 없거나 비노출이면 null */
    fun findVisiblePostAuthor(type: CommentType, postSeq: Long): Int?

    /** 작성자 표시 정보(users + SHELTER면 소속 보호소) */
    fun findAuthors(userSeqs: Collection<Int>): Map<Int, AuthorRow>
}

/** users 네이티브 조회 결과. role은 USER/SHELTER */
interface AuthorRow {
    val userSeq: Int
    val role: String
    val deleted: Boolean
    val nickname: String?
    val profileImageSeq: Long?
    val shelterName: String?
    val shelterImageSeq: Long?
}
