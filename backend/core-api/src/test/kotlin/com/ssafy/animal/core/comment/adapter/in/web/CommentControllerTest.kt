package com.ssafy.animal.core.comment.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.comment.application.port.`in`.CommentAuthor
import com.ssafy.animal.core.comment.application.port.`in`.CommentDetail
import com.ssafy.animal.core.comment.application.port.`in`.CommentUseCase
import com.ssafy.animal.core.comment.application.port.`in`.CreateCommentCommand
import com.ssafy.animal.core.comment.application.port.`in`.CreatedComment
import com.ssafy.animal.core.comment.application.port.`in`.UpdateCommentCommand
import com.ssafy.animal.core.comment.domain.CommentErrorCode
import com.ssafy.animal.core.comment.domain.CommentType
import com.ssafy.animal.core.image.domain.ImageErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.Optional

@WebMvcTest(CommentController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class CommentControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var commentUseCase: CommentUseCase

    private val detail = CommentDetail(
        commentType = CommentType.MISSING_COMMENT,
        commentSeq = 44,
        parentCommentSeq = null,
        targetType = TargetType.MISSING,
        targetSeq = 101,
        author = CommentAuthor(17, "반려가족", Role.USER, null),
        body = "근처에서 본 것 같아요.",
        image = null,
        isDeleted = false,
        replyCount = 2,
        regDttm = OffsetDateTime.of(2026, 10, 29, 17, 50, 0, 0, ZoneOffset.ofHours(9)),
        modDttm = null,
    )

    @Test
    fun `13_1은 루트 댓글을 배열로 돌려준다`() {
        given(commentUseCase.listRoots(TargetType.MISSING, 101)).willReturn(listOf(detail))

        mockMvc.get("/api/core/v1/comments?targetType=MISSING&targetSeq=101") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data[0].commentType") { value("MISSING_COMMENT") }
                jsonPath("$.data[0].commentSeq") { value(44) }
                jsonPath("$.data[0].targetType") { value("MISSING") }
                jsonPath("$.data[0].author.displayName") { value("반려가족") }
                jsonPath("$.data[0].replyCount") { value(2) }
                jsonPath("$.data[0].isDeleted") { value(false) }
                jsonPath("$.data[0].regDttm") { value("2026-10-29T17:50:00+09:00") }
            }
    }

    @Test
    fun `13_1 targetSeq 누락은 400, 없는 게시물은 404 COMMENT_TARGET_NOT_FOUND`() {
        mockMvc.get("/api/core/v1/comments?targetType=MISSING") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        given(commentUseCase.listRoots(TargetType.FOUND, 9)).willThrow(BusinessException(CommentErrorCode.COMMENT_TARGET_NOT_FOUND))
        mockMvc.get("/api/core/v1/comments?targetType=FOUND&targetSeq=9") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMENT_TARGET_NOT_FOUND") }
        }
    }

    @Test
    fun `토큰이 없으면 401 AUTH_UNAUTHORIZED`() {
        mockMvc.get("/api/core/v1/comments?targetType=MISSING&targetSeq=101").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
    }

    @Test
    fun `13_2는 답글 배열, 답글을 루트로 지정하면 404 COMMENT_NOT_FOUND`() {
        given(commentUseCase.listReplies(TargetType.MISSING_COMMENT, 44))
            .willReturn(listOf(detail.copy(commentSeq = 45, parentCommentSeq = 44, replyCount = 0)))
        given(commentUseCase.listReplies(TargetType.MISSING_COMMENT, 45)).willThrow(BusinessException(CommentErrorCode.COMMENT_NOT_FOUND))

        mockMvc.get("/api/core/v1/comments/MISSING_COMMENT/44/replies") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data[0].parentCommentSeq") { value(44) }
            }
        mockMvc.get("/api/core/v1/comments/MISSING_COMMENT/45/replies") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("COMMENT_NOT_FOUND") }
            }
    }

    @Test
    fun `13_2 정의되지 않은 commentType은 400 COMMON_INVALID_INPUT`() {
        mockMvc.get("/api/core/v1/comments/SHELTER_ANIMAL_COMMENT/1/replies") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            }
    }

    @Test
    fun `13_6은 단건, 삭제된 댓글은 body null로 200`() {
        given(commentUseCase.get(TargetType.FOUND_COMMENT, 7))
            .willReturn(detail.copy(commentType = CommentType.FOUND_COMMENT, commentSeq = 7, body = null, isDeleted = true))
        given(commentUseCase.get(TargetType.FOUND_COMMENT, 8)).willThrow(BusinessException(CommentErrorCode.COMMENT_NOT_FOUND))

        mockMvc.get("/api/core/v1/comments/FOUND_COMMENT/7") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.isDeleted") { value(true) }
            jsonPath("$.data.body") { value(null as Any?) }
        }
        mockMvc.get("/api/core/v1/comments/FOUND_COMMENT/8") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMENT_NOT_FOUND") }
        }
    }

    @Test
    fun `13_3 성공은 201과 생성된 댓글 키`() {
        val command = CreateCommentCommand(TargetType.MISSING, 101, 44, null, 1701)
        given(commentUseCase.create(42, command)).willReturn(CreatedComment(CommentType.MISSING_COMMENT, 45, 44))

        post("""{"targetType":"MISSING","targetSeq":101,"parentCommentSeq":44,"body":null,"imageSeq":1701}""").andExpect {
            status { isCreated() }
            jsonPath("$.data.commentType") { value("MISSING_COMMENT") }
            jsonPath("$.data.commentSeq") { value(45) }
            jsonPath("$.data.parentCommentSeq") { value(44) }
        }
    }

    @Test
    fun `13_3 실패 코드를 그대로 전달한다`() {
        val cases = mapOf(
            1L to CommentErrorCode.COMMENT_EMPTY,
            2L to CommentErrorCode.COMMENT_DEPTH_EXCEEDED,
            3L to CommentErrorCode.COMMENT_TARGET_NOT_FOUND,
            4L to CommentErrorCode.COMMENT_PARENT_UNAVAILABLE,
            5L to ImageErrorCode.IMAGE_NOT_OWNED,
            6L to CommonErrorCode.COMMON_INVALID_INPUT,
        )
        cases.forEach { (seq, code) ->
            given(commentUseCase.create(42, CreateCommentCommand(TargetType.FOUND, seq, null, "x", null))).willThrow(BusinessException(code))
            post("""{"targetType":"FOUND","targetSeq":$seq,"body":"x"}""").andExpect {
                status { isEqualTo(code.status.value()) }
                jsonPath("$.code") { value(code.name) }
            }
        }
    }

    @Test
    fun `13_3 targetType 누락은 400 COMMON_INVALID_INPUT`() {
        post("""{"targetSeq":101,"body":"x"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `13_4는 미전달과 명시적 null을 구분해 넘기고 수정된 댓글을 돌려준다`() {
        val command = UpdateCommentCommand(body = Optional.of("수정된 설명"), imageSeq = Optional.empty())
        given(commentUseCase.update(42, TargetType.MISSING_COMMENT, 44, command)).willReturn(detail.copy(body = "수정된 설명"))

        mockMvc.patch("/api/core/v1/comments/MISSING_COMMENT/44") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"body":"수정된 설명","imageSeq":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.body") { value("수정된 설명") }
        }
    }

    @Test
    fun `13_4 타인 댓글은 404, 비우면 400 COMMENT_EMPTY`() {
        given(commentUseCase.update(42, TargetType.MISSING_COMMENT, 50, UpdateCommentCommand(body = Optional.of("x"))))
            .willThrow(BusinessException(CommentErrorCode.COMMENT_NOT_FOUND))
        given(commentUseCase.update(42, TargetType.MISSING_COMMENT, 44, UpdateCommentCommand(body = Optional.empty())))
            .willThrow(BusinessException(CommentErrorCode.COMMENT_EMPTY))

        patch("/api/core/v1/comments/MISSING_COMMENT/50", """{"body":"x"}""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMENT_NOT_FOUND") }
        }
        patch("/api/core/v1/comments/MISSING_COMMENT/44", """{"body":null}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMENT_EMPTY") }
        }
    }

    @Test
    fun `13_5 성공은 본문 없는 204, 타인 댓글은 404`() {
        willThrow(BusinessException(CommentErrorCode.COMMENT_NOT_FOUND)).given(commentUseCase).delete(42, TargetType.FOUND_COMMENT, 9)

        mockMvc.delete("/api/core/v1/comments/MISSING_COMMENT/44") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(commentUseCase).should().delete(42, TargetType.MISSING_COMMENT, 44)

        mockMvc.delete("/api/core/v1/comments/FOUND_COMMENT/9") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COMMENT_NOT_FOUND") }
        }
    }

    private fun post(body: String) = mockMvc.post("/api/core/v1/comments") {
        header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun patch(path: String, body: String) = mockMvc.patch(path) {
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
