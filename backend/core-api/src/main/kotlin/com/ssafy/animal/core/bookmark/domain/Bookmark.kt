package com.ssafy.animal.core.bookmark.domain

import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.http.HttpStatus

/** 16장 북마크 대상 3종. DB CHECK ck_bookmark_target_type과 같다 */
enum class BookmarkTargetType { MISSING, FOUND, SHELTER_ANIMAL }

/** 명세 19장: 북마크는 user 도메인(R12)이라 접두어가 USER_ */
enum class BookmarkErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    USER_BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 저장한 게시물입니다."),
    USER_BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크를 찾을 수 없습니다."),
}

/** DB 명세 32. bookmarks. 해제는 물리 삭제 */
@Entity
@Table(name = "bookmarks")
class Bookmark(
    userSeq: Int,
    targetType: BookmarkTargetType,
    targetSeq: Long,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var bookmarkSeq: Long? = null
        protected set

    @Column(nullable = false, updatable = false)
    var userSeq: Int = userSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    var targetType: BookmarkTargetType = targetType
        protected set

    @Column(nullable = false, updatable = false)
    var targetSeq: Long = targetSeq
        protected set
}
