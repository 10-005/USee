package com.ssafy.animal.core.bookmark.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.bookmark.application.port.`in`.AddedBookmark
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkItem
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkUseCase
import com.ssafy.animal.core.bookmark.application.port.`in`.PostSummary
import com.ssafy.animal.core.bookmark.domain.BookmarkErrorCode
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date

@WebMvcTest(BookmarkController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class BookmarkControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var bookmarkUseCase: BookmarkUseCase

    private val now = OffsetDateTime.of(2026, 11, 9, 10, 0, 0, 0, ZoneOffset.ofHours(9))

    @Test
    fun `16_1은 Page 형식으로 targetType 필터를 넘긴다`() {
        val item = BookmarkItem(
            701, BookmarkTargetType.MISSING, 101, now,
            PostSummary(null, "MALE", "3살", "ACTIVE", "대구광역시 중구", now, null),
        )
        given(bookmarkUseCase.list(eq(42L), eq(TargetType.MISSING), any(Pageable::class.java) ?: Pageable.unpaged()))
            .willReturn(PageResponse(listOf(item), 0, 20, 1, 1, false))

        mockMvc.get("/api/core/v1/users/me/bookmarks?targetType=MISSING") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.content[0].bookmarkSeq") { value(701) }
                jsonPath("$.data.content[0].targetType") { value("MISSING") }
                jsonPath("$.data.content[0].postSummary.status") { value("ACTIVE") }
                jsonPath("$.data.totalElements") { value(1) }
            }
    }

    @Test
    fun `16_1 SHELTER는 403 AUTH_FORBIDDEN`() {
        mockMvc.get("/api/core/v1/users/me/bookmarks") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(bookmarkUseCase).should(never()).list(anyLong(), any(), any(Pageable::class.java) ?: Pageable.unpaged())
    }

    @Test
    fun `16_2 성공은 201, 중복은 409 USER_BOOKMARK_ALREADY_EXISTS`() {
        given(bookmarkUseCase.add(42, TargetType.SHELTER_ANIMAL, 5)).willReturn(AddedBookmark(702, BookmarkTargetType.SHELTER_ANIMAL, 5))
        given(bookmarkUseCase.add(42, TargetType.MISSING, 101)).willThrow(BusinessException(BookmarkErrorCode.USER_BOOKMARK_ALREADY_EXISTS))

        post("""{"targetType":"SHELTER_ANIMAL","targetSeq":5}""").andExpect {
            status { isCreated() }
            jsonPath("$.data.bookmarkSeq") { value(702) }
            jsonPath("$.data.targetType") { value("SHELTER_ANIMAL") }
            jsonPath("$.data.targetSeq") { value(5) }
        }
        post("""{"targetType":"MISSING","targetSeq":101}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USER_BOOKMARK_ALREADY_EXISTS") }
        }
    }

    @Test
    fun `16_2 허용 밖 대상은 400, 없는 게시물은 404`() {
        given(bookmarkUseCase.add(42, TargetType.USER, 1)).willThrow(BusinessException(CommonErrorCode.COMMON_INVALID_INPUT))
        given(bookmarkUseCase.add(42, TargetType.FOUND, 999)).willThrow(BusinessException(CommonErrorCode.COMMON_NOT_FOUND))

        post("""{"targetType":"USER","targetSeq":1}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        post("""{"targetType":"FOUND","targetSeq":999}""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMON_NOT_FOUND") }
        }
    }

    @Test
    fun `16_3 성공은 204, 없는 북마크는 404 USER_BOOKMARK_NOT_FOUND`() {
        willThrow(BusinessException(BookmarkErrorCode.USER_BOOKMARK_NOT_FOUND)).given(bookmarkUseCase).remove(42, TargetType.FOUND, 3)

        mockMvc.delete("/api/core/v1/users/me/bookmarks/MISSING/101") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(bookmarkUseCase).should().remove(42, TargetType.MISSING, 101)

        mockMvc.delete("/api/core/v1/users/me/bookmarks/FOUND/3") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_BOOKMARK_NOT_FOUND") }
        }
    }

    private fun post(body: String) = mockMvc.post("/api/core/v1/users/me/bookmarks") {
        header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        private val USER_TOKEN = bearer(mapOf("userSeq" to 42, "role" to "USER"))
        private val SHELTER_TOKEN = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
