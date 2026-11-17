package com.ssafy.animal.core.report.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.report.application.port.`in`.ReportCommand
import com.ssafy.animal.core.report.application.port.`in`.ReportReceipt
import com.ssafy.animal.core.report.application.port.`in`.ReportUseCase
import com.ssafy.animal.core.report.domain.ReportErrorCode
import com.ssafy.animal.core.report.domain.ReportStatus
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date

@WebMvcTest(ReportController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ReportControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var reportUseCase: ReportUseCase

    @Test
    fun `15_1 성공은 201 PENDING, SHELTER도 신고할 수 있다`() {
        given(reportUseCase.report(77, ReportCommand("MISSING_COMMENT", 44, "HARASSMENT"))).willReturn(ReportReceipt(601, ReportStatus.PENDING))

        post(SHELTER_TOKEN, """{"targetType":"MISSING_COMMENT","targetSeq":44,"reasonCode":"HARASSMENT"}""").andExpect {
            status { isCreated() }
            jsonPath("$.data.reportSeq") { value(601) }
            jsonPath("$.data.status") { value("PENDING") }
        }
    }

    @Test
    fun `15_1 실패 코드를 그대로 전달한다`() {
        val cases = mapOf<Long, ErrorCode>(
            1L to ReportErrorCode.REPORT_TARGET_NOT_ALLOWED,
            2L to ReportErrorCode.REPORT_REASON_REQUIRED,
            3L to ReportErrorCode.REPORT_REASON_NOT_ALLOWED,
            4L to ReportErrorCode.REPORT_ALREADY_EXISTS,
            5L to ReportErrorCode.REPORT_TARGET_NOT_FOUND,
            6L to CommonErrorCode.COMMON_INVALID_INPUT,
        )
        cases.forEach { (seq, code) ->
            given(reportUseCase.report(42, ReportCommand("USER", seq, "OTHER"))).willThrow(BusinessException(code))
            post(USER_TOKEN, """{"targetType":"USER","targetSeq":$seq,"reasonCode":"OTHER"}""").andExpect {
                status { isEqualTo(code.status.value()) }
                jsonPath("$.code") { value(code.name) }
            }
        }
    }

    @Test
    fun `15_1 targetSeq 누락은 400 COMMON_INVALID_INPUT`() {
        post(USER_TOKEN, """{"targetType":"USER","reasonCode":"OTHER"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    private fun post(token: String, body: String) = mockMvc.post("/api/core/v1/reports") {
        header(HttpHeaders.AUTHORIZATION, token)
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
