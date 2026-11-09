package com.ssafy.animal.core.bookmark.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 16장 북마크(USER 전용, Q452 경로) */
@RestController
@RequestMapping("/api/core/v1/users/me/bookmarks")
@PreAuthorize("hasRole('USER')")
class BookmarkController(
    private val bookmarkUseCase: BookmarkUseCase,
) {

    /** 16.1 필터 targetType, 정렬 regDttm만 */
    @GetMapping
    fun list(@AuthenticationPrincipal user: AuthUser, @RequestParam(required = false) targetType: TargetType?, pageable: Pageable) =
        bookmarkUseCase.list(user.userSeq, targetType, pageable)

    /** 16.2 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: AddBookmarkRequest) =
        bookmarkUseCase.add(user.userSeq, request.targetType!!, request.targetSeq!!)

    /** 16.3 */
    @DeleteMapping("/{targetType}/{targetSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(@AuthenticationPrincipal user: AuthUser, @PathVariable targetType: TargetType, @PathVariable targetSeq: Long) {
        bookmarkUseCase.remove(user.userSeq, targetType, targetSeq)
    }
}

data class AddBookmarkRequest(
    @field:NotNull
    val targetType: TargetType?,
    @field:NotNull
    val targetSeq: Long?,
)
