package com.ssafy.animal.core.bookmark.application.port.out

import com.ssafy.animal.core.bookmark.domain.Bookmark
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

interface BookmarkRepository {
    fun find(userSeq: Int, targetType: BookmarkTargetType, targetSeq: Long): Bookmark?

    /** UNIQUE(uq_bookmark_user_target) 위반은 409 USER_BOOKMARK_ALREADY_EXISTS로 바꿔 던진다 */
    fun saveAndFlush(bookmark: Bookmark): Bookmark

    fun delete(bookmark: Bookmark)

    /** 노출 중인 게시물(신고: 비삭제·작성자 비탈퇴, 보호동물: 비삭제)인지 */
    fun existsVisibleTarget(targetType: BookmarkTargetType, targetSeq: Long): Boolean

    /** 16.1 노출 중인 게시물의 북마크만. 정렬은 regDttm(방향만 sort에서), 동률 bookmarkSeq desc */
    fun findVisible(userSeq: Int, targetType: BookmarkTargetType?, pageable: Pageable): Page<BookmarkRow>
}

data class BookmarkRow(
    val bookmarkSeq: Long,
    val targetType: BookmarkTargetType,
    val targetSeq: Long,
    val regDttm: OffsetDateTime,
    val combinationSeq: Int,
    val gender: String,
    val age: String?,
    val status: String,
    val address: String,
    val occurredDttm: OffsetDateTime?,
    val representativeImageSeq: Long?,
)
