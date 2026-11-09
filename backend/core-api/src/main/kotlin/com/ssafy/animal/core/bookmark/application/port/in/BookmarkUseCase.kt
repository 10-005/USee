package com.ssafy.animal.core.bookmark.application.port.`in`

import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

interface BookmarkUseCase {
    /** 16.1 원본이 삭제됐거나 작성자가 탈퇴한 게시물은 빠진다 */
    fun list(userSeq: Long, targetType: TargetType?, pageable: Pageable): PageResponse<BookmarkItem>

    /** 16.2 중복 409 USER_BOOKMARK_ALREADY_EXISTS */
    fun add(userSeq: Long, targetType: TargetType, targetSeq: Long): AddedBookmark

    /** 16.3 물리 삭제. 없으면 404 USER_BOOKMARK_NOT_FOUND */
    fun remove(userSeq: Long, targetType: TargetType, targetSeq: Long)
}

data class AddedBookmark(
    val bookmarkSeq: Long,
    val targetType: BookmarkTargetType,
    val targetSeq: Long,
)

data class BookmarkItem(
    val bookmarkSeq: Long,
    val targetType: BookmarkTargetType,
    val targetSeq: Long,
    val regDttm: OffsetDateTime,
    val postSummary: PostSummary,
)

/**
 * [초안] 명세가 {...}로만 둔 카드 요약. 7.1·8.1·11.1 목록 카드와 같은 이름의 공통 필드만 담는다.
 * status는 신고면 ACTIVE/CLOSED, 보호동물이면 protection_status. address는 보호동물이면 보호소 주소, occurredDttm은 보호동물이면 null
 */
data class PostSummary(
    val classification: AnimalClassification?,
    val gender: String,
    val age: String?,
    val status: String,
    val address: String,
    val occurredDttm: OffsetDateTime?,
    val representativeImageUrl: String?,
)
