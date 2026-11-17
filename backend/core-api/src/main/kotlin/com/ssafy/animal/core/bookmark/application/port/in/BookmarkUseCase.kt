package com.ssafy.animal.core.bookmark.application.port.`in`

import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import io.swagger.v3.oas.annotations.media.Schema
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
    @field:Schema(description = "북마크 번호", example = "701")
    val bookmarkSeq: Long,
    @field:Schema(description = "대상 종류. MISSING: 실종 신고, FOUND: 발견 신고, SHELTER_ANIMAL: 보호동물", example = "MISSING")
    val targetType: BookmarkTargetType,
    @field:Schema(description = "대상 게시물 번호", example = "101")
    val targetSeq: Long,
)

data class BookmarkItem(
    @field:Schema(description = "북마크 번호", example = "701")
    val bookmarkSeq: Long,
    @field:Schema(description = "대상 종류. MISSING: 실종 신고, FOUND: 발견 신고, SHELTER_ANIMAL: 보호동물", example = "MISSING")
    val targetType: BookmarkTargetType,
    @field:Schema(description = "대상 게시물 번호", example = "101")
    val targetSeq: Long,
    @field:Schema(description = "북마크한 시각", example = "2026-11-09T10:15:30+09:00")
    val regDttm: OffsetDateTime,
    @field:Schema(description = "대상 게시물 카드 요약")
    val postSummary: PostSummary,
)

/**
 * [초안] 명세가 {...}로만 둔 카드 요약. 7.1·8.1·11.1 목록 카드와 같은 이름의 공통 필드만 담는다.
 * status는 신고면 ACTIVE/CLOSED, 보호동물이면 protection_status. address는 보호동물이면 보호소 주소, occurredDttm은 보호동물이면 null
 */
data class PostSummary(
    @field:Schema(description = "종·품종·색상. 조합을 찾지 못하면 null", nullable = true)
    val classification: AnimalClassification?,
    @field:Schema(description = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름", example = "MALE")
    val gender: String,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "3살")
    val age: String?,
    @field:Schema(
        description = "게시물 상태. 실종·발견 신고는 ACTIVE(진행 중)·CLOSED(종료), 보호동물은 보호 상태 PROTECTING(보호 중)·RETURNED(반환)·ADOPTED(입양)·ENDED(종료)",
        example = "ACTIVE",
    )
    val status: String,
    @field:Schema(description = "주소. 실종·발견 신고는 발생 장소, 보호동물은 보호소 주소", example = "서울특별시 강남구 역삼동")
    val address: String,
    @field:Schema(description = "실종·발견 시각. 보호동물이면 null", nullable = true, example = "2026-11-08T18:30:00+09:00")
    val occurredDttm: OffsetDateTime?,
    @field:Schema(description = "대표 사진 URL. 없으면 null", nullable = true, example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val representativeImageUrl: String?,
)
