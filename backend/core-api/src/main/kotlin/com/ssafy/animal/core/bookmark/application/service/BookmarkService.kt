package com.ssafy.animal.core.bookmark.application.service

import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.bookmark.application.port.`in`.AddedBookmark
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkItem
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkUseCase
import com.ssafy.animal.core.bookmark.application.port.`in`.PostSummary
import com.ssafy.animal.core.bookmark.application.port.out.BookmarkRepository
import com.ssafy.animal.core.bookmark.domain.Bookmark
import com.ssafy.animal.core.bookmark.domain.BookmarkErrorCode
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BookmarkService(
    private val bookmarkRepository: BookmarkRepository,
    private val catalogUseCase: CatalogUseCase,
    private val imageUseCase: ImageUseCase,
) : BookmarkUseCase {

    @Transactional(readOnly = true)
    override fun list(userSeq: Long, targetType: TargetType?, pageable: Pageable): PageResponse<BookmarkItem> {
        val page = bookmarkRepository.findVisible(Math.toIntExact(userSeq), targetType?.let(::bookmarkType), pageable)
        val classifications = catalogUseCase.findClassifications(page.content.map { it.combinationSeq }.toSet())
        val images = imageUseCase.findSummaries(page.content.mapNotNull { it.representativeImageSeq })
        return PageResponse.of(
            page.map {
                BookmarkItem(
                    it.bookmarkSeq, it.targetType, it.targetSeq, it.regDttm,
                    PostSummary(
                        classification = classifications[it.combinationSeq],
                        gender = it.gender,
                        age = it.age,
                        status = it.status,
                        address = it.address,
                        occurredDttm = it.occurredDttm,
                        representativeImageUrl = it.representativeImageSeq?.let { seq -> images[seq] }?.imageUrl,
                    ),
                )
            },
        )
    }

    @Transactional
    override fun add(userSeq: Long, targetType: TargetType, targetSeq: Long): AddedBookmark {
        val type = bookmarkType(targetType)
        // 명세에 대상 없음 코드가 없어 공통 404를 쓴다(보고서 "명세 확인 필요")
        if (!bookmarkRepository.existsVisibleTarget(type, targetSeq)) throw BusinessException(CommonErrorCode.COMMON_NOT_FOUND)
        val user = Math.toIntExact(userSeq)
        if (bookmarkRepository.find(user, type, targetSeq) != null) {
            throw BusinessException(BookmarkErrorCode.USER_BOOKMARK_ALREADY_EXISTS)
        }
        val bookmark = bookmarkRepository.saveAndFlush(Bookmark(user, type, targetSeq))
        return AddedBookmark(bookmark.bookmarkSeq!!, type, targetSeq)
    }

    @Transactional
    override fun remove(userSeq: Long, targetType: TargetType, targetSeq: Long) {
        val bookmark = bookmarkRepository.find(Math.toIntExact(userSeq), bookmarkType(targetType), targetSeq)
            ?: throw BusinessException(BookmarkErrorCode.USER_BOOKMARK_NOT_FOUND)
        bookmarkRepository.delete(bookmark)
    }

    private fun bookmarkType(targetType: TargetType) =
        BookmarkTargetType.entries.firstOrNull { it.name == targetType.name } ?: throw BusinessException(
            CommonErrorCode.COMMON_INVALID_INPUT,
            errors = listOf(ErrorDetail("targetType", "북마크할 수 없는 대상입니다: $targetType")),
        )
}
