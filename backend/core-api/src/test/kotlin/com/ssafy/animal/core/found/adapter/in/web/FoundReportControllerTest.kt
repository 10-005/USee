package com.ssafy.animal.core.found.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.found.application.port.`in`.ActorSummary
import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.AssignedFoundReportItem
import com.ssafy.animal.core.found.application.port.`in`.AssignmentMethod
import com.ssafy.animal.core.found.application.port.`in`.FoundReportDetail
import com.ssafy.animal.core.found.application.port.`in`.FoundReportItem
import com.ssafy.animal.core.found.application.port.`in`.FoundReportUseCase
import com.ssafy.animal.core.found.application.port.`in`.FoundSearchFilter
import com.ssafy.animal.core.found.application.port.`in`.ImageRef
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.found.application.port.`in`.RegisterFoundReportCommand
import com.ssafy.animal.core.found.application.port.`in`.RegisteredFoundReport
import com.ssafy.animal.core.found.application.port.`in`.ReporterContact
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.application.port.`in`.UpdateFoundReportCommand
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.found.domain.FoundImageErrorCode
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundReportContent
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.found.domain.Gender
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.verifyNoInteractions
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

@WebMvcTest(FoundReportController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class FoundReportControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var foundReportUseCase: FoundReportUseCase

    private val user = AuthUser(42, Role.USER, null)
    private val shelterUser = AuthUser(77, Role.SHELTER, 7)

    private val content = FoundReportContent(
        88, Gender.UNKNOWN, null, "약 3kg", null, null, "목에 빨간 목줄", "01012345678", false,
        "공원 정문 앞", "경상북도 구미시 인동동", 36.1, 128.4, OffsetDateTime.parse("2026-10-29T10:00:00+09:00"), 
    )
    private val report = FoundReport(42, 7, content, "47190").also {
        ReflectionTestUtils.setField(it, "foundReportSeq", 210)
        ReflectionTestUtils.setField(it, "regDttm", OffsetDateTime.parse("2026-10-29T12:00:00+09:00"))
    }
    private val classification = AnimalClassification(88, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색")
    private val item = FoundReportItem(report, classification, "https://cdn/1.jpg")
    private val detail = FoundReportDetail(
        item, ActorSummary(42, "발견도우미", Role.USER, null), "구미시 동물보호센터", null,
        listOf(ImageRef(1201, "https://cdn/1.jpg", 0)), isBookmarked = true, commentCount = 3,
    )

    @Test
    fun `8_1은 필터를 모아 넘기고 목록 필드를 내린다`() {
        val filter = FoundSearchFilter(
            FoundStatus.ACTIVE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "47190", 1, Gender.UNKNOWN, false, "목줄",
        )
        given(foundReportUseCase.search(filter, PageRequest.of(0, 20))).willReturn(PageImpl(listOf(item), PageRequest.of(0, 20), 1))

        mockMvc.get(
            "/api/core/v1/found-reports?status=ACTIVE&occurredFrom=2026-10-01&occurredTo=2026-10-31&districtCode=47190" +
                "&speciesSeq=1&gender=UNKNOWN&isNeutered=false&keyword=목줄",
        ) { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].foundReportSeq") { value(210) }
            jsonPath("$.data.content[0].classification.breedName") { value("말티즈") }
            jsonPath("$.data.content[0].weight") { value("약 3kg") }
            jsonPath("$.data.content[0].shelterSeq") { value(7) }
            jsonPath("$.data.content[0].representativeImageUrl") { value("https://cdn/1.jpg") }
            jsonPath("$.data.content[0].reporterPhone") { doesNotExist() }
        }
    }

    @Test
    fun `8_1 허용값 밖 gender는 400 COMMON_INVALID_INPUT, 없는 districtCode는 400 CATALOG_INVALID_DISTRICT_CODE`() {
        given(foundReportUseCase.search(FoundSearchFilter(districtCode = "99999"), PageRequest.of(0, 20)))
            .willThrow(BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE))

        mockMvc.get("/api/core/v1/found-reports?gender=BOTH") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        mockMvc.get("/api/core/v1/found-reports?districtCode=99999") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CATALOG_INVALID_DISTRICT_CODE") }
        }
    }

    @Test
    fun `8_2는 상세 필드를 내리고 없으면 404 FOUND_REPORT_NOT_FOUND`() {
        given(foundReportUseCase.get(user, 210)).willReturn(detail)
        given(foundReportUseCase.get(user, 211)).willThrow(BusinessException(FoundErrorCode.FOUND_REPORT_NOT_FOUND))

        mockMvc.get("/api/core/v1/found-reports/210") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.foundReportSeq") { value(210) }
            jsonPath("$.data.reporter.displayName") { value("발견도우미") }
            jsonPath("$.data.shelter.shelterName") { value("구미시 동물보호센터") }
            jsonPath("$.data.reporterPhone") { value(null as Any?) }
            jsonPath("$.data.landmarkDescription") { value("공원 정문 앞") }
            jsonPath("$.data.images[0].displayOrder") { value(0) }
            jsonPath("$.data.isBookmarked") { value(true) }
            jsonPath("$.data.commentCount") { value(3) }
        }
        mockMvc.get("/api/core/v1/found-reports/211") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("FOUND_REPORT_NOT_FOUND") }
        }
    }

    @Test
    fun `8_3은 배열로 내리고 SHELTER는 403`() {
        given(foundReportUseCase.getMine(42)).willReturn(listOf(item))

        mockMvc.get("/api/core/v1/found-reports/me") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].foundReportSeq") { value(210) }
        }
        mockMvc.get("/api/core/v1/found-reports/me") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
    }

    @Test
    fun `8_4는 201로 배정 결과를 내린다`() {
        given(foundReportUseCase.register(ArgumentMatchers.eq(42L), anyArg(RegisterFoundReportCommand::class.java)))
            .willReturn(RegisteredFoundReport(210, FoundStatus.ACTIVE, "47190", 7, AssignmentMethod.DISTRICT))

        mockMvc.post("/api/core/v1/found-reports") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = REGISTER_BODY
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.foundReportSeq") { value(210) }
            jsonPath("$.data.assignedShelterSeq") { value(7) }
            jsonPath("$.data.assignmentMethod") { value("DISTRICT") }
        }
    }

    @Test
    fun `8_4 필수값 누락·shelterSeq 지정은 400이고 서비스는 호출하지 않는다`() {
        mockMvc.post("/api/core/v1/found-reports") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"combinationSeq":88,"gender":"UNKNOWN"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        mockMvc.post("/api/core/v1/found-reports") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = REGISTER_BODY.replace("\"imageSeqs\"", "\"shelterSeq\":3,\"imageSeqs\"")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("shelterSeq") }
        }
        verifyNoInteractions(foundReportUseCase)
    }

    @Test
    fun `8_4 보호소가 없으면 409, 사진 개수 오류는 400 IMAGE_INVALID_COUNT, SHELTER는 403`() {
        given(foundReportUseCase.register(ArgumentMatchers.eq(42L), anyArg(RegisterFoundReportCommand::class.java)))
            .willThrow(BusinessException(FoundErrorCode.FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE))
            .willThrow(BusinessException(FoundImageErrorCode.IMAGE_INVALID_COUNT))

        listOf(409 to "FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE", 400 to "IMAGE_INVALID_COUNT").forEach { (status, code) ->
            mockMvc.post("/api/core/v1/found-reports") {
                header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = REGISTER_BODY
            }.andExpect {
                status { isEqualTo(status) }
                jsonPath("$.code") { value(code) }
            }
        }
        mockMvc.post("/api/core/v1/found-reports") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = REGISTER_BODY
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `8_5는 부분 수정 명령을 넘기고 CLOSED면 409 FOUND_REPORT_CLOSED`() {
        val command = UpdateFoundReportCommand(weight = Optional.empty(), imageSeqs = Optional.of(listOf(1201L, 1202L)))
        given(foundReportUseCase.update(user, 210, command)).willReturn(detail)
        given(foundReportUseCase.update(user, 211, command)).willThrow(BusinessException(FoundErrorCode.FOUND_REPORT_CLOSED))

        mockMvc.patch("/api/core/v1/found-reports/210") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"weight":null,"imageSeqs":[1201,1202]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.foundReportSeq") { value(210) }
        }
        mockMvc.patch("/api/core/v1/found-reports/211") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"weight":null,"imageSeqs":[1201,1202]}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("FOUND_REPORT_CLOSED") }
        }
    }

    @Test
    fun `8_6 담당 보호소의 종료는 200, 재개는 403 AUTH_FORBIDDEN`() {
        val closed = FoundReport(42, 7, content, "47190").also {
            ReflectionTestUtils.setField(it, "foundReportSeq", 210)
            it.changeStatus(FoundStatus.CLOSED, "보호자가 직접 찾아감", OffsetDateTime.parse("2026-10-30T09:00:00+09:00"), 77)
        }
        given(foundReportUseCase.changeStatus(shelterUser, 210, FoundStatus.CLOSED, "보호자가 직접 찾아감")).willReturn(closed)
        given(foundReportUseCase.changeStatus(shelterUser, 210, FoundStatus.ACTIVE, null))
            .willThrow(BusinessException(CommonErrorCode.AUTH_FORBIDDEN))

        mockMvc.patch("/api/core/v1/found-reports/210/status") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"CLOSED","closedReason":"보호자가 직접 찾아감"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CLOSED") }
            jsonPath("$.data.closedReason") { value("보호자가 직접 찾아감") }
            jsonPath("$.data.closedDttm") { exists() }
        }
        mockMvc.patch("/api/core/v1/found-reports/210/status") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"ACTIVE"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
    }

    @Test
    fun `8_7은 204 본문 없음, 남의 신고는 404`() {
        given(foundReportUseCase.delete(42, 211)).willThrow(BusinessException(FoundErrorCode.FOUND_REPORT_NOT_FOUND))

        mockMvc.delete("/api/core/v1/found-reports/210") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(foundReportUseCase).should().delete(42, 210)
        mockMvc.delete("/api/core/v1/found-reports/211") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("FOUND_REPORT_NOT_FOUND") }
        }
    }

    @Test
    fun `8_8은 size 기본 8, 신고자 실명·구조 상태를 내리고 USER는 403`() {
        val filter = AssignedFilter(FoundStatus.ACTIVE, RecentPeriod.RECENT_30D, null, null, RescueProgress.PROCESSING)
        val assigned = AssignedFoundReportItem(item, ReporterContact("홍길동", "01012345678"), RescueProgress.PROCESSING, 301)
        given(foundReportUseCase.getAssigned(7, filter, PageRequest.of(0, 8))).willReturn(PageImpl(listOf(assigned), PageRequest.of(0, 8), 1))

        mockMvc.get("/api/core/v1/found-reports/assigned?status=ACTIVE&period=RECENT_30D&rescueStatus=PROCESSING") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.size") { value(8) }
            jsonPath("$.data.content[0].foundReportSeq") { value(210) }
            jsonPath("$.data.content[0].reporter.name") { value("홍길동") }
            jsonPath("$.data.content[0].reporter.phone") { value("01012345678") }
            jsonPath("$.data.content[0].rescueStatus") { value("PROCESSING") }
            jsonPath("$.data.content[0].activeRescueSeq") { value(301) }
        }
        mockMvc.get("/api/core/v1/found-reports/assigned") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `8_8 허용값 밖 rescueStatus는 400 COMMON_INVALID_INPUT`() {
        mockMvc.get("/api/core/v1/found-reports/assigned?rescueStatus=FAILED") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    companion object {
        private val REGISTER_BODY = """
            {"combinationSeq":88,"gender":"UNKNOWN","age":null,"weight":"약 3kg","isNeutered":null,"description":"목에 빨간 목줄",
             "reporterPhone":"01012345678","isPhonePublic":false,"landmarkDescription":"공원 정문 앞","address":"경상북도 구미시 인동동",
             "latitude":36.1,"longitude":128.4,"occurredDttm":"2026-10-29T10:00:00+09:00","imageSeqs":[1201]}
        """.trimIndent()

        /** Kotlin 비null 파라미터에 Mockito any()를 넘기기 위한 도우미(any()는 null을 돌려준다) */
        @Suppress("UNCHECKED_CAST")
        private fun <T> anyArg(type: Class<T>): T {
            ArgumentMatchers.any(type)
            return null as T
        }

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
