package com.ssafy.animal.core.rescue.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.found.application.port.`in`.ReporterContact
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.rescue.application.port.`in`.CreateRescueCommand
import com.ssafy.animal.core.rescue.application.port.`in`.FoundReportSummary
import com.ssafy.animal.core.rescue.application.port.`in`.RescueAnimal
import com.ssafy.animal.core.rescue.application.port.`in`.RescueDetail
import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.application.port.`in`.RescueItem
import com.ssafy.animal.core.rescue.application.port.`in`.RescuePage
import com.ssafy.animal.core.rescue.application.port.`in`.RescueSource
import com.ssafy.animal.core.rescue.application.port.`in`.RescueUseCase
import com.ssafy.animal.core.rescue.application.port.`in`.UpdateRescueCommand
import com.ssafy.animal.core.rescue.domain.RescueContent
import com.ssafy.animal.core.rescue.domain.RescueErrorCode
import com.ssafy.animal.core.rescue.domain.RescueReasonCode
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebMvcTest(RescueController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class RescueControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var rescueUseCase: RescueUseCase

    private val shelterUser = AuthUser(77, Role.SHELTER, 7)
    private val rescuedDttm = OffsetDateTime.parse("2026-10-23T17:47:00+09:00")
    private val rescue = RescueRecord(
        7, 210, RescueContent(RescueReasonCode.WANDERING, "구미시 인동동 123-12", "송산도서관 인근 마을", rescuedDttm, "포획틀 필요"), 77,
    ).also {
        ReflectionTestUtils.setField(it, "rescueSeq", 301)
        ReflectionTestUtils.setField(it, "regDttm", OffsetDateTime.parse("2026-10-23T18:00:00+09:00"))
    }
    private val summary = FoundReportSummary(
        210, null, "0살", "UNKNOWN", null, "약 3kg", "구미시 인동동", OffsetDateTime.parse("2026-10-19T18:30:00+09:00"),
        null, ReporterContact("홍길동", "01012341234"), "ACTIVE",
    )

    @Test
    fun `10_1은 statusCounts와 page를 함께 내리고 size 기본 8`() {
        val filter = RescueFilter(RescueStatus.PROCESSING, RecentPeriod.RECENT_30D, 1, RescueSource.REPORT, null)
        val animal = RescueAnimal("고양이", "코리안숏헤어", "0살", "UNKNOWN", null, "흰색", "약 3kg", null)
        val counts = mapOf(RescueStatus.PROCESSING to 3L, RescueStatus.DONE to 16L, RescueStatus.FAILED to 3L, RescueStatus.CANCELED to 2L)
        given(rescueUseCase.getPage(7, filter, PageRequest.of(0, 8)))
            .willReturn(RescuePage(counts, PageImpl(listOf(RescueItem(rescue, animal)), PageRequest.of(0, 8), 1)))

        mockMvc.get("/api/core/v1/rescues?status=PROCESSING&period=RECENT_30D&speciesSeq=1&source=REPORT") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.statusCounts.total") { value(24) }
            jsonPath("$.data.statusCounts.PROCESSING") { value(3) }
            jsonPath("$.data.statusCounts.CANCELED") { value(2) }
            jsonPath("$.data.page.size") { value(8) }
            jsonPath("$.data.page.totalElements") { value(1) }
            jsonPath("$.data.page.content[0].rescueSeq") { value(301) }
            jsonPath("$.data.page.content[0].source") { value("REPORT") }
            jsonPath("$.data.page.content[0].rescueMemo") { value("포획틀 필요") }
            jsonPath("$.data.page.content[0].animal.breedName") { value("코리안숏헤어") }
        }
    }

    @Test
    fun `10_1 USER는 403, 허용값 밖 source는 400`() {
        mockMvc.get("/api/core/v1/rescues") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        mockMvc.get("/api/core/v1/rescues?source=ETC") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        verifyNoInteractions(rescueUseCase)
    }

    @Test
    fun `10_1 화이트리스트 밖 정렬은 400`() {
        given(rescueUseCase.getPage(7, RescueFilter(), PageRequest.of(0, 8, Sort.by("rescuedDttm"))))
            .willThrow(BusinessException(CommonErrorCode.COMMON_INVALID_INPUT))

        mockMvc.get("/api/core/v1/rescues?sort=rescuedDttm,asc") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `10_2는 상세와 발견 신고 요약을 내리고 다른 보호소 구조는 404`() {
        given(rescueUseCase.get(7, 301)).willReturn(RescueDetail(rescue, 0, summary))
        given(rescueUseCase.get(7, 302)).willThrow(BusinessException(RescueErrorCode.RESCUE_NOT_FOUND))

        mockMvc.get("/api/core/v1/rescues/301") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.rescueSeq") { value(301) }
            jsonPath("$.data.shelterSeq") { value(7) }
            jsonPath("$.data.rescueAddressDetail") { value("송산도서관 인근 마을") }
            jsonPath("$.data.completedDttm") { value(null as Any?) }
            jsonPath("$.data.linkedAnimalCount") { value(0) }
            jsonPath("$.data.foundReportSummary.reporter.name") { value("홍길동") }
            jsonPath("$.data.foundReportSummary.status") { value("ACTIVE") }
        }
        mockMvc.get("/api/core/v1/rescues/302") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RESCUE_NOT_FOUND") }
        }
    }

    @Test
    fun `10_3은 201로 PROCESSING 접수 결과를 내린다`() {
        val command = CreateRescueCommand(210, RescueContent(RescueReasonCode.WANDERING, "구미시 인동동 123-12", null, rescuedDttm, null))
        given(rescueUseCase.create((ArgumentMatchers.eq(shelterUser) ?: shelterUser), anyArg(CreateRescueCommand::class.java))).willReturn(rescue)

        mockMvc.post("/api/core/v1/rescues") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"foundReportSeq":210,"rescueReasonCode":"WANDERING","rescueAddress":"구미시 인동동 123-12","rescuedDttm":"2026-10-23T17:47:00+09:00"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.data.rescueSeq") { value(301) }
            jsonPath("$.data.status") { value("PROCESSING") }
            jsonPath("$.data.regDttm") { exists() }
        }
        val captor = ArgumentCaptor.forClass(CreateRescueCommand::class.java)
        then(rescueUseCase).should().create((ArgumentMatchers.eq(shelterUser) ?: shelterUser), captor.capture() ?: command)
        assertEquals(command.copy(content = command.content.copy(rescuedDttm = captor.value.content.rescuedDttm)), captor.value)
        assertTrue(captor.value.content.rescuedDttm.isEqual(rescuedDttm))
    }

    @Test
    fun `10_3 정의되지 않은 사유 코드·공백 주소는 400 COMMON_INVALID_INPUT`() {
        mockMvc.post("/api/core/v1/rescues") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rescueReasonCode":"LOST","rescueAddress":"  ","rescuedDttm":"2026-10-23T17:47:00+09:00"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            jsonPath("$.errors.length()") { value(2) }
        }
        verifyNoInteractions(rescueUseCase)
    }

    @Test
    fun `10_3 진행 중 구조가 있으면 409, 삭제된 발견 신고는 404`() {
        given(rescueUseCase.create((ArgumentMatchers.eq(shelterUser) ?: shelterUser), anyArg(CreateRescueCommand::class.java)))
            .willThrow(BusinessException(RescueErrorCode.RESCUE_ACTIVE_ALREADY_EXISTS))
            .willThrow(BusinessException(FoundErrorCode.FOUND_REPORT_NOT_FOUND))

        listOf(409 to "RESCUE_ACTIVE_ALREADY_EXISTS", 404 to "FOUND_REPORT_NOT_FOUND").forEach { (status, code) ->
            mockMvc.post("/api/core/v1/rescues") {
                header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
                contentType = MediaType.APPLICATION_JSON
                content = """{"foundReportSeq":210,"rescueReasonCode":"OTHER","rescueAddress":"구미시","rescuedDttm":"2026-10-23T17:47:00+09:00"}"""
            }.andExpect {
                status { isEqualTo(status) }
                jsonPath("$.code") { value(code) }
            }
        }
    }

    @Test
    fun `10_4는 부분 수정을 넘기고 종료 상태의 메모 외 수정은 400`() {
        val memoOnly = UpdateRescueCommand(rescueMemo = Optional.of("보호소 이송 완료"))
        val withReason = UpdateRescueCommand(rescueReasonCode = Optional.of(RescueReasonCode.OTHER))
        given(rescueUseCase.update(shelterUser, 301, memoOnly)).willReturn(RescueDetail(rescue, 1, null))
        given(rescueUseCase.update(shelterUser, 301, withReason)).willThrow(BusinessException(CommonErrorCode.COMMON_INVALID_INPUT))

        mockMvc.patch("/api/core/v1/rescues/301") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rescueMemo":"보호소 이송 완료"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.linkedAnimalCount") { value(1) }
        }
        mockMvc.patch("/api/core/v1/rescues/301") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rescueReasonCode":"OTHER"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `10_4 정의되지 않은 사유 코드는 400이고 서비스는 호출하지 않는다`() {
        mockMvc.patch("/api/core/v1/rescues/301") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"rescueReasonCode":"LOST"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("rescueReasonCode") }
        }
        verifyNoInteractions(rescueUseCase)
    }

    @Test
    fun `10_5는 종료 결과를 내리고 전이 불가는 409, 사유 누락은 400`() {
        val failed = RescueRecord(7, null, rescue.content, 77).also {
            ReflectionTestUtils.setField(it, "rescueSeq", 301)
            it.complete(RescueStatus.FAILED, "현장 도착 전 이동", OffsetDateTime.parse("2026-10-24T10:00:00+09:00"), 77)
        }
        given(rescueUseCase.changeStatus(shelterUser, 301, RescueStatus.FAILED, "현장 도착 전 이동")).willReturn(failed)
        given(rescueUseCase.changeStatus(shelterUser, 302, RescueStatus.DONE, null))
            .willThrow(BusinessException(RescueErrorCode.RESCUE_INVALID_TRANSITION))
        given(rescueUseCase.changeStatus(shelterUser, 303, RescueStatus.CANCELED, null))
            .willThrow(BusinessException(RescueErrorCode.RESCUE_RESULT_REASON_REQUIRED))

        mockMvc.patch("/api/core/v1/rescues/301/status") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"FAILED","resultReason":"현장 도착 전 이동"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("FAILED") }
            jsonPath("$.data.resultReason") { value("현장 도착 전 이동") }
            jsonPath("$.data.completedDttm") { exists() }
        }
        listOf(Triple(302, """{"status":"DONE"}""", "RESCUE_INVALID_TRANSITION"), Triple(303, """{"status":"CANCELED"}""", "RESCUE_RESULT_REASON_REQUIRED"))
            .forEach { (seq, body, code) ->
                mockMvc.patch("/api/core/v1/rescues/$seq/status") {
                    header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect { jsonPath("$.code") { value(code) } }
            }
    }

    @Test
    fun `10_6은 204, 연결 보호동물이 있으면 409 RESCUE_HAS_ANIMALS`() {
        given(rescueUseCase.delete(shelterUser, 302)).willThrow(BusinessException(RescueErrorCode.RESCUE_HAS_ANIMALS))

        mockMvc.delete("/api/core/v1/rescues/301") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(rescueUseCase).should().delete(shelterUser, 301)
        mockMvc.delete("/api/core/v1/rescues/302") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RESCUE_HAS_ANIMALS") }
        }
    }

    companion object {
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
