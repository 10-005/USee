package com.ssafy.animal.core.image.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.domain.Image
import com.ssafy.animal.core.image.domain.ImageErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date

@WebMvcTest(ImageController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ImageControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var imageUseCase: ImageUseCase

    @Test
    fun `4_1은 토큰의 userSeq로 발급하고 200 봉투로 응답한다`() {
        given(imageUseCase.issueUploadUrl(IssueUploadUrlCommand(42, "dog.jpg", "image/jpeg", 1234567)))
            .willReturn(UploadUrl("https://s3/put", "images/2026/11/a.jpg", 600))

        mockMvc.post("/api/core/v1/images/upload-urls") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fileName":"dog.jpg","contentType":"image/jpeg","byteSize":1234567}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.uploadUrl") { value("https://s3/put") }
            jsonPath("$.data.imageKey") { value("images/2026/11/a.jpg") }
            jsonPath("$.data.expiresInSeconds") { value(600) }
        }
    }

    @Test
    fun `4_1 필수값 누락과 0바이트는 400`() {
        listOf("""{"byteSize":10}""", """{"contentType":"image/jpeg","byteSize":0}""").forEach { body ->
            mockMvc.post("/api/core/v1/images/upload-urls") {
                header(HttpHeaders.AUTHORIZATION, BEARER)
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            }
        }
    }

    @Test
    fun `4_1 fileName은 255자까지, 256자는 400 COMMON_INVALID_INPUT`() {
        given(imageUseCase.issueUploadUrl(IssueUploadUrlCommand(42, "a".repeat(255), "image/jpeg", 1)))
            .willReturn(UploadUrl("u", "k", 600))
        mockMvc.post("/api/core/v1/images/upload-urls") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fileName":"${"a".repeat(255)}","contentType":"image/jpeg","byteSize":1}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/core/v1/images/upload-urls") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"fileName":"${"a".repeat(256)}","contentType":"image/jpeg","byteSize":1}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("fileName") }
        }
    }

    @Test
    fun `저장소 미설정은 503 COMMON_SERVICE_UNAVAILABLE`() {
        willThrow(BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)).given(imageUseCase).confirm(42, "k")

        mockMvc.post("/api/core/v1/images") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"imageKey":"k"}"""
        }.andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("COMMON_SERVICE_UNAVAILABLE") }
        }
    }

    @Test
    fun `4_1 도메인 오류는 413 IMAGE_TOO_LARGE`() {
        willThrow(BusinessException(ImageErrorCode.IMAGE_TOO_LARGE))
            .given(imageUseCase).issueUploadUrl(IssueUploadUrlCommand(42, null, "image/jpeg", 99_999_999))

        mockMvc.post("/api/core/v1/images/upload-urls") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"image/jpeg","byteSize":99999999}"""
        }.andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("IMAGE_TOO_LARGE") }
        }
    }

    @Test
    fun `4_2는 201과 imageSeq, imageUrl을 돌려준다`() {
        val image = Image("images/2026/11/a.jpg", "image/jpeg", "dog.jpg", 1234567, 42)
        ReflectionTestUtils.setField(image, "imageSeq", 1001L)
        given(imageUseCase.confirm(42, "images/2026/11/a.jpg")).willReturn(ConfirmedImage(image, "https://cdn/images/2026/11/a.jpg"))

        mockMvc.post("/api/core/v1/images") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"imageKey":"images/2026/11/a.jpg"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(201) }
            jsonPath("$.data.imageSeq") { value(1001) }
            jsonPath("$.data.imageUrl") { value("https://cdn/images/2026/11/a.jpg") }
            jsonPath("$.data.mimeType") { value("image/jpeg") }
            jsonPath("$.data.byteSize") { value(1234567) }
        }
    }

    @Test
    fun `4_2 빈 imageKey는 400, 토큰 없으면 401`() {
        mockMvc.post("/api/core/v1/images") {
            header(HttpHeaders.AUTHORIZATION, BEARER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"imageKey":" "}"""
        }.andExpect { status { isBadRequest() } }

        mockMvc.post("/api/core/v1/images") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"imageKey":"k"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val BEARER = "Bearer " + Jwts.builder().claim("userSeq", 42).claim("role", "USER").claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
