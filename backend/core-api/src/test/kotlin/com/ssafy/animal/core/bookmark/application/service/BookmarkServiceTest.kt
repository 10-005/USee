package com.ssafy.animal.core.bookmark.application.service

import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.bookmark.adapter.out.persistence.BookmarkPersistenceAdapter
import com.ssafy.animal.core.bookmark.application.port.out.BookmarkRepository
import com.ssafy.animal.core.bookmark.application.port.out.BookmarkRow
import com.ssafy.animal.core.bookmark.domain.Bookmark
import com.ssafy.animal.core.bookmark.domain.BookmarkErrorCode
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BookmarkServiceTest {

    private val bookmarks = mutableListOf<Bookmark>()
    private val visible = setOf(BookmarkTargetType.MISSING to 101L, BookmarkTargetType.SHELTER_ANIMAL to 5L)
    private var lastFilter: BookmarkTargetType? = null

    private val repository = object : BookmarkRepository {
        override fun find(userSeq: Int, targetType: BookmarkTargetType, targetSeq: Long) =
            bookmarks.firstOrNull { it.userSeq == userSeq && it.targetType == targetType && it.targetSeq == targetSeq }
        override fun saveAndFlush(bookmark: Bookmark) = bookmark.also {
            ReflectionTestUtils.setField(it, "bookmarkSeq", 700L + bookmarks.size)
            bookmarks += it
        }
        override fun delete(bookmark: Bookmark) {
            bookmarks.remove(bookmark)
        }
        override fun existsVisibleTarget(targetType: BookmarkTargetType, targetSeq: Long) = (targetType to targetSeq) in visible
        override fun findVisible(userSeq: Int, targetType: BookmarkTargetType?, pageable: Pageable): Page<BookmarkRow> {
            lastFilter = targetType
            val row = BookmarkRow(701, BookmarkTargetType.MISSING, 101, OffsetDateTime.now(), 88, "MALE", "3살", "ACTIVE", "대구", null, 1201)
            return PageImpl(listOf(row), pageable, 1)
        }
    }

    private val catalog = object : CatalogUseCase {
        override fun requireClassification(combinationSeq: Int) = error("not used")
        override fun findClassifications(combinationSeqs: Collection<Int>) =
            combinationSeqs.associateWith { AnimalClassification(it, 1, SpeciesCode.DOG, "개", 2, "말티즈", 3, "흰색") }
        override fun requireDistrict(districtCode: String) = error("not used")
    }

    private val images = object : ImageUseCase {
        override fun findSummary(imageSeq: Long) = ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        override fun requireOwned(userSeq: Long, imageSeq: Long) = error("not used")
        override fun deleteIfUnreferenced(imageSeq: Long) = error("not used")
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
    }

    private val service = BookmarkService(repository, catalog, images)

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `등록·중복 409·해제·없는 해제 404`() {
        val added = service.add(42, TargetType.MISSING, 101)
        assertEquals(700L, added.bookmarkSeq)
        assertEquals(BookmarkTargetType.MISSING, added.targetType)

        assertCode(BookmarkErrorCode.USER_BOOKMARK_ALREADY_EXISTS) { service.add(42, TargetType.MISSING, 101) }
        service.remove(42, TargetType.MISSING, 101)
        assertTrue(bookmarks.isEmpty())
        assertCode(BookmarkErrorCode.USER_BOOKMARK_NOT_FOUND) { service.remove(42, TargetType.MISSING, 101) }
    }

    @Test
    fun `대상 3종 밖은 400, 비노출 게시물은 404`() {
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.add(42, TargetType.USER, 1) }
        assertCode(CommonErrorCode.COMMON_NOT_FOUND) { service.add(42, TargetType.FOUND, 9) }
    }

    @Test
    fun `목록은 분류·대표 사진을 붙여 Page로 돌려준다`() {
        val page = service.list(42, TargetType.MISSING, PageRequest.of(0, 20))

        assertEquals(BookmarkTargetType.MISSING, lastFilter)
        val summary = page.content.single().postSummary
        assertEquals("말티즈", summary.classification?.breedName)
        assertEquals("https://cdn/1201.jpg", summary.representativeImageUrl)
        assertEquals(1, page.totalElements)
    }

    @Test
    fun `정렬은 regDttm만 허용한다`() {
        assertEquals("desc", BookmarkPersistenceAdapter.direction(PageRequest.of(0, 20)))
        assertEquals("asc", BookmarkPersistenceAdapter.direction(PageRequest.of(0, 20, Sort.by("regDttm").ascending())))
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { BookmarkPersistenceAdapter.direction(PageRequest.of(0, 20, Sort.by("targetSeq"))) }
    }
}
