package com.ssafy.animal.core.missing.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.missing.application.port.`in`.CreateMissingCommand
import com.ssafy.animal.core.missing.application.port.`in`.CreatedMissing
import com.ssafy.animal.core.missing.application.port.`in`.MissingAuthor
import com.ssafy.animal.core.missing.application.port.`in`.MissingDetail
import com.ssafy.animal.core.missing.application.port.`in`.MissingImageRef
import com.ssafy.animal.core.missing.application.port.`in`.MissingReportUseCase
import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.application.port.`in`.MissingSummary
import com.ssafy.animal.core.missing.application.port.`in`.UpdateMissingCommand
import com.ssafy.animal.core.missing.domain.MissingErrorCode
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingReport
import com.ssafy.animal.core.missing.domain.MissingStatus
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date
import java.util.Optional

@WebMvcTest(MissingReportController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class MissingReportControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var useCase: MissingReportUseCase

    private val classification = AnimalClassification(88, 1, SpeciesCode.DOG, "개", 3, "말티즈", 2, "흰색")
    private val occurred = OffsetDateTime.parse("2026-10-28T18:00:00+09:00")

    private val report = MissingReport(
        42, 88, MissingGender.MALE, "3살", "4.5kg", true, null, "파란 목줄",
        "대구광역시 중구", 35.87, 128.60, occurred, "27110", "01012345678", false,
    ).also { ReflectionTestUtils.setField(it, "missingReportSeq", 101) }

    private val summary = MissingSummary(report, classification, "https://cdn/1001.jpg")

    private val detail = MissingDetail(
        report, classification, listOf(MissingImageRef(1001, "https://cdn/1001.jpg", 1)),
        MissingAuthor(42, "반려가족", Role.USER, null), contactPhone = null, isBookmarked = true, commentCount = 3,
    )

    @Test
    fun `7_1은 필터를 그대로 넘기고 페이지로 돌려준다`() {
        val filter = MissingSearchFilter(
            MissingStatus.ACTIVE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "27110", 1, MissingGender.MALE, true, "목줄",
        )
        val pageable = PageRequest.of(0, 20)
        given(useCase.search(filter, pageable)).willReturn(PageImpl(listOf(summary), pageable, 1))

        mockMvc.get(
            "/api/core/v1/missing-reports?status=ACTIVE&occurredFrom=2026-10-01&occurredTo=2026-10-31" +
                "&districtCode=27110&speciesSeq=1&gender=MALE&isNeutered=true&keyword=목줄",
        ) { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].missingReportSeq") { value(101) }
            jsonPath("$.data.content[0].classification.speciesName") { value("개") }
            jsonPath("$.data.content[0].age") { value("3살") }
            jsonPath("$.data.content[0].isNeutered") { value(true) }
            jsonPath("$.data.content[0].weight") { value("4.5kg") }
            jsonPath("$.data.content[0].status") { value("ACTIVE") }
            jsonPath("$.data.content[0].districtCode") { value("27110") }
            jsonPath("$.data.content[0].occurredDttm") { value("2026-10-28T18:00:00+09:00") }
            jsonPath("$.data.content[0].representativeImageUrl") { value("https://cdn/1001.jpg") }
            jsonPath("$.data.content[0].contactPhone") { doesNotExist() }
        }
    }

    @Test
    fun `7_1 허용값 밖 파라미터는 400, 없는 districtCode는 400 CATALOG_INVALID_DISTRICT_CODE`() {
        listOf("gender=BOTH", "isNeutered=maybe", "occurredFrom=2026-13-01", "status=OPEN").forEach { query ->
            mockMvc.get("/api/core/v1/missing-reports?$query") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            }
        }
        willThrow(BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE))
            .given(useCase).search(MissingSearchFilter(districtCode = "99999"), PageRequest.of(0, 20))
        mockMvc.get("/api/core/v1/missing-reports?districtCode=99999") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CATALOG_INVALID_DISTRICT_CODE") }
        }
    }

    @Test
    fun `7_2는 상세를 돌려주고 없으면 404 MISSING_REPORT_NOT_FOUND`() {
        given(useCase.get(42, 101)).willReturn(detail)
        given(useCase.get(42, 102)).willThrow(BusinessException(MissingErrorCode.MISSING_REPORT_NOT_FOUND))

        mockMvc.get("/api/core/v1/missing-reports/101") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.user.userSeq") { value(42) }
            jsonPath("$.data.user.displayName") { value("반려가족") }
            jsonPath("$.data.user.role") { value("USER") }
            jsonPath("$.data.latitude") { value(35.87) }
            jsonPath("$.data.images[0].displayOrder") { value(1) }
            jsonPath("$.data.isPhonePublic") { value(false) }
            jsonPath("$.data.contactPhone") { value(null as Any?) }
            jsonPath("$.data.isBookmarked") { value(true) }
            jsonPath("$.data.commentCount") { value(3) }
            jsonPath("$.data.name") { doesNotExist() }
            jsonPath("$.data.petSeq") { doesNotExist() }
        }
        mockMvc.get("/api/core/v1/missing-reports/102") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MISSING_REPORT_NOT_FOUND") }
        }
    }

    @Test
    fun `7_3은 페이지 없이 배열, SHELTER는 403`() {
        given(useCase.getMine(42)).willReturn(listOf(summary))

        mockMvc.get("/api/core/v1/missing-reports/me") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].missingReportSeq") { value(101) }
            jsonPath("$.data.content") { doesNotExist() }
        }
        mockMvc.get("/api/core/v1/missing-reports/me") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
    }

    @Test
    fun `7_4는 201로 등록 결과를 돌려준다`() {
        given(useCase.create(42, command(listOf(1001, 1002)))).willReturn(CreatedMissing(101, MissingStatus.ACTIVE, "27110", true))

        mockMvc.post("/api/core/v1/missing-reports") { createBody(USER_TOKEN, listOf(1001, 1002)) }.andExpect {
            status { isCreated() }
            jsonPath("$.data.missingReportSeq") { value(101) }
            jsonPath("$.data.status") { value("ACTIVE") }
            jsonPath("$.data.districtCode") { value("27110") }
            jsonPath("$.data.aiMatchingRequested") { value(true) }
        }
    }

    @Test
    fun `7_4 실패 코드 - SHELTER 403, 필수값 400, 서비스 오류는 명세 코드 그대로`() {
        mockMvc.post("/api/core/v1/missing-reports") { createBody(SHELTER_TOKEN, listOf(1)) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        mockMvc.post("/api/core/v1/missing-reports") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"combinationSeq":88,"gender":"MALE","imageSeqs":[1]}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        then(useCase).should(never()).create(anyLong(), any())

        listOf(
            listOf(1L) to BusinessException(MissingErrorCode.MISSING_ACTIVE_ALREADY_EXISTS),
            listOf(2L) to BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND),
            listOf(3L) to BusinessException(ImageErrorCode.IMAGE_NOT_FOUND),
            listOf(4L) to BusinessException(ImageErrorCode.IMAGE_NOT_OWNED),
            emptyList<Long>() to BusinessException(MissingErrorCode.IMAGE_INVALID_COUNT),
        ).forEach { (imageSeqs, error) ->
            willThrow(error).given(useCase).create(42, command(imageSeqs))
            mockMvc.post("/api/core/v1/missing-reports") { createBody(USER_TOKEN, imageSeqs) }.andExpect {
                status { isEqualTo(error.errorCode.status.value()) }
                jsonPath("$.code") { value(error.errorCode.toString()) }
            }
        }
    }

    @Test
    fun `7_5는 부분 수정을 넘기고 상세를 돌려주며 CLOSED는 409`() {
        val command = UpdateMissingCommand(description = Optional.empty(), latitude = 36.0, longitude = 128.0, imageSeqs = listOf(1002))
        given(useCase.update(42, 101, command)).willReturn(detail)
        willThrow(BusinessException(MissingErrorCode.MISSING_REPORT_CLOSED)).given(useCase).update(42, 102, UpdateMissingCommand(age = Optional.of("4살")))
        willThrow(BusinessException(MissingErrorCode.MISSING_REPORT_NOT_FOUND)).given(useCase).update(42, 103, UpdateMissingCommand(age = Optional.of("4살")))

        mockMvc.patch("/api/core/v1/missing-reports/101") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"description":null,"latitude":36.0,"longitude":128.0,"imageSeqs":[1002]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.missingReportSeq") { value(101) }
        }
        mapOf(102 to "MISSING_REPORT_CLOSED", 103 to "MISSING_REPORT_NOT_FOUND").forEach { (seq, code) ->
            mockMvc.patch("/api/core/v1/missing-reports/$seq") {
                header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"age":"4살"}"""
            }.andExpect { jsonPath("$.code") { value(code) } }
        }
    }

    @Test
    fun `7_6은 상태를 바꾸고 재개 충돌은 409, 같은 상태는 400`() {
        given(useCase.changeStatus(42, 101, MissingStatus.CLOSED)).willReturn(report.also { it.changeStatus(MissingStatus.CLOSED, 42) })
        willThrow(BusinessException(MissingErrorCode.MISSING_ACTIVE_ALREADY_EXISTS)).given(useCase).changeStatus(42, 102, MissingStatus.ACTIVE)
        willThrow(BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("status", "같음"))))
            .given(useCase).changeStatus(42, 103, MissingStatus.ACTIVE)

        mockMvc.patch("/api/core/v1/missing-reports/101/status") { statusBody("CLOSED") }.andExpect {
            status { isOk() }
            jsonPath("$.data.missingReportSeq") { value(101) }
            jsonPath("$.data.status") { value("CLOSED") }
        }
        mockMvc.patch("/api/core/v1/missing-reports/102/status") { statusBody("ACTIVE") }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("MISSING_ACTIVE_ALREADY_EXISTS") }
        }
        mockMvc.patch("/api/core/v1/missing-reports/103/status") { statusBody("ACTIVE") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("status") }
        }
        mockMvc.patch("/api/core/v1/missing-reports/101/status") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `7_7은 204 본문 없음, 타인 것은 404`() {
        willThrow(BusinessException(MissingErrorCode.MISSING_REPORT_NOT_FOUND)).given(useCase).delete(42, 102)

        mockMvc.delete("/api/core/v1/missing-reports/101") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(useCase).should().delete(42, 101)
        mockMvc.delete("/api/core/v1/missing-reports/102") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MISSING_REPORT_NOT_FOUND") }
        }
    }

    private fun command(imageSeqs: List<Long>) = CreateMissingCommand(
        combinationSeq = 88, gender = MissingGender.MALE, weight = "4.5kg", address = "대구광역시 중구",
        latitude = 35.87, longitude = 128.60, occurredDttm = occurred, contactPhone = "01012345678", imageSeqs = imageSeqs,
    )

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.createBody(token: String, imageSeqs: List<Long>) {
        header(HttpHeaders.AUTHORIZATION, token)
        contentType = MediaType.APPLICATION_JSON
        content = """
            {"combinationSeq":88,"gender":"MALE","weight":"4.5kg","address":"대구광역시 중구","latitude":35.87,"longitude":128.60,
             "occurredDttm":"2026-10-28T18:00:00+09:00","contactPhone":"01012345678","imageSeqs":$imageSeqs}
        """
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.statusBody(status: String) {
        header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
        contentType = MediaType.APPLICATION_JSON
        content = """{"status":"$status"}"""
    }

    // Kotlin non-null 파라미터에 Mockito any()를 넘기기 위한 도우미
    private fun <T> any(): T {
        org.mockito.ArgumentMatchers.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
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
