package com.ssafy.animal.core.adoption.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.adoption.application.port.`in`.AdoptionUseCase
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantView
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicationDetail
import com.ssafy.animal.core.adoption.application.port.`in`.Completed
import com.ssafy.animal.core.adoption.application.port.`in`.DraftCreated
import com.ssafy.animal.core.adoption.application.port.`in`.DraftSaved
import com.ssafy.animal.core.adoption.application.port.`in`.Failed
import com.ssafy.animal.core.adoption.application.port.`in`.FormInput
import com.ssafy.animal.core.adoption.application.port.`in`.FormPatch
import com.ssafy.animal.core.adoption.application.port.`in`.MyApplicationItem
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionBrief
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionList
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionView
import com.ssafy.animal.core.adoption.application.port.`in`.ReceivedItem
import com.ssafy.animal.core.adoption.application.port.`in`.ReviewDecision
import com.ssafy.animal.core.adoption.application.port.`in`.Reviewed
import com.ssafy.animal.core.adoption.application.port.`in`.StatusChanged
import com.ssafy.animal.core.adoption.application.port.`in`.Submitted
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.adoption.domain.QuestionType
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSummary
import com.ssafy.animal.core.animal.domain.Gender
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
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

@WebMvcTest(AdoptionController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class AdoptionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var useCase: AdoptionUseCase

    private val now = OffsetDateTime.parse("2026-10-29T17:50:00+09:00")
    private val summary = AnimalSummary(
        700, "개", "믹스견", "갈색", Gender.FEMALE, "2026년생", "3kg", true, "410987654321098", null,
        "https://cdn/1701.jpg", 7, "박범준보호소", "경북 구미시 3공단 3로 302",
    )
    private val visit = LocalDate.of(2026, 12, 10)
    private val form = FormInput(mapOf("1" to "아파트", "3" to listOf("개")), "구미시", "7층", "a@b.com", visit)
    private val formJson = """{"animalSeq":700,"answers":{"1":"아파트","3":["개"]},"careAddress":"구미시","careAddressDetail":"7층","email":"a@b.com","visitHopeDate":"2026-12-10"}"""

    private fun json(request: org.springframework.test.web.servlet.MockHttpServletRequestDsl, token: String, body: String) = with(request) {
        header(HttpHeaders.AUTHORIZATION, token)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `12_1은 문항 목록을 questions로 감싼다(두 역할 모두)`() {
        val condition = mapOf("depends_on" to 17, "operator" to "EQ", "value" to "이수", "required_when_matched" to true)
        given(useCase.getQuestions()).willReturn(
            QuestionList(listOf(QuestionView(20, 20, "이수일", QuestionType.DATE, true, null, condition))),
        )
        listOf(USER_TOKEN, SHELTER_TOKEN).forEach { token ->
            mockMvc.get("/api/core/v1/adoption/questions") { header(HttpHeaders.AUTHORIZATION, token) }.andExpect {
                status { isOk() }
                jsonPath("$.data.questions[0].questionType") { value("DATE") }
                jsonPath("$.data.questions[0].condition.depends_on") { value(17) }
                jsonPath("$.data.questions[0].condition.required_when_matched") { value(true) }
            }
        }
    }

    @Test
    fun `12_2는 status·animalSeq를 넘기고 sort는 무시한다, SHELTER는 403`() {
        given(useCase.getMyApplications(42, ApplicationStatus.DRAFT, 700, PageRequest.of(0, 20)))
            .willReturn(PageImpl(listOf(MyApplicationItem(901, ApplicationStatus.DRAFT, now, null, null, summary)), PageRequest.of(0, 20), 1))

        mockMvc.get("/api/core/v1/adoption-applications?status=DRAFT&animalSeq=700&sort=regDttm,asc") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].applicationSeq") { value(901) }
            jsonPath("$.data.content[0].displayDttm") { value("2026-10-29T17:50:00+09:00") }
            jsonPath("$.data.content[0].animalSummary.shelterName") { value("박범준보호소") }
            jsonPath("$.data.totalElements") { value(1) }
        }
        mockMvc.get("/api/core/v1/adoption-applications") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `12_3은 상세를 돌려주고 볼 수 없으면 404`() {
        val detail = ApplicationDetail(
            901, 42, ApplicationStatus.SUBMITTED, summary, "구미시", "7층", "a@b.com", visit, mapOf("1" to "아파트"),
            listOf(QuestionBrief(1, "주거 형태", QuestionType.SINGLE_CHOICE, 1)), ApplicantView("김가람", LocalDate.of(1997, 5, 12), "01012345678"),
            now, null, null, null,
        )
        given(useCase.getApplication(SHELTER, 901)).willReturn(detail)
        given(useCase.getApplication(SHELTER, 902)).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND))

        mockMvc.get("/api/core/v1/adoption-applications/901") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.answers.1") { value("아파트") }
            jsonPath("$.data.applicant.birthDate") { value("1997-05-12") }
            jsonPath("$.data.reviewer") { value(null as Any?) }
        }
        mockMvc.get("/api/core/v1/adoption-applications/902") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ADOPTION_APPLICATION_NOT_FOUND") }
        }
    }

    @Test
    fun `12_4는 201 DRAFT, animalSeq가 없으면 400, 중복이면 409`() {
        given(useCase.createDraft(42, 700, form)).willReturn(DraftCreated(901, ApplicationStatus.DRAFT, 700))
        given(useCase.createDraft(42, 701, FormInput())).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS))

        mockMvc.post("/api/core/v1/adoption-applications") { json(this, USER_TOKEN, formJson) }.andExpect {
            status { isCreated() }
            jsonPath("$.data.status") { value("DRAFT") }
            jsonPath("$.data.animalSeq") { value(700) }
        }
        mockMvc.post("/api/core/v1/adoption-applications") { json(this, USER_TOKEN, """{"animalSeq":701}""") }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS") }
        }
        mockMvc.post("/api/core/v1/adoption-applications") { json(this, USER_TOKEN, """{"answers":{}}""") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("animalSeq") }
        }
        mockMvc.post("/api/core/v1/adoption-applications") { json(this, SHELTER_TOKEN, formJson) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `12_5는 미전달·명시적 null을 구분해 넘긴다, DRAFT가 아니면 409`() {
        val patch = FormPatch(answers = mapOf("1" to "아파트"), email = Optional.empty())
        given(useCase.saveDraft(42, 901, patch)).willReturn(DraftSaved(901, ApplicationStatus.DRAFT, 1))
        given(useCase.saveDraft(42, 902, FormPatch())).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_DRAFT))

        mockMvc.patch("/api/core/v1/adoption-applications/901") { json(this, USER_TOKEN, """{"answers":{"1":"아파트"},"email":null}""") }.andExpect {
            status { isOk() }
            jsonPath("$.data.savedQuestionCount") { value(1) }
        }
        mockMvc.patch("/api/core/v1/adoption-applications/902") { json(this, USER_TOKEN, "{}") }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ADOPTION_APPLICATION_NOT_DRAFT") }
        }
    }

    @Test
    fun `12_6은 200으로 제출 결과를 돌려준다, 필수 누락은 400`() {
        given(useCase.submit(42, 700, form)).willReturn(Submitted(901, ApplicationStatus.SUBMITTED, now))
        given(useCase.submit(42, 701, FormInput())).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_REQUIRED_ANSWER_MISSING))

        mockMvc.post("/api/core/v1/adoption-applications/submit") { json(this, USER_TOKEN, formJson) }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("SUBMITTED") }
            jsonPath("$.data.submittedDttm") { value("2026-10-29T17:50:00+09:00") }
        }
        mockMvc.post("/api/core/v1/adoption-applications/submit") { json(this, USER_TOKEN, """{"animalSeq":701}""") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ADOPTION_REQUIRED_ANSWER_MISSING") }
        }
    }

    @Test
    fun `12_7은 204 본문 없음, DRAFT가 아니면 409`() {
        given(useCase.deleteDraft(42, 902)).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_DRAFT))

        mockMvc.delete("/api/core/v1/adoption-applications/901") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(useCase).should().deleteDraft(42, 901)
        mockMvc.delete("/api/core/v1/adoption-applications/902") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect { status { isConflict() } }
    }

    @Test
    fun `12_8은 CANCELED를 돌려준다, SUBMITTED가 아니면 409`() {
        given(useCase.cancel(42, 901)).willReturn(StatusChanged(901, ApplicationStatus.CANCELED))
        given(useCase.cancel(42, 902)).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_SUBMITTED))

        mockMvc.post("/api/core/v1/adoption-applications/901/cancel") { json(this, USER_TOKEN, "{}") }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CANCELED") }
        }
        mockMvc.post("/api/core/v1/adoption-applications/902/cancel") { json(this, USER_TOKEN, "{}") }.andExpect { status { isConflict() } }
    }

    @Test
    fun `12_9는 정렬을 넘기고 신청자 실명을 내린다, USER는 403`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "regDttm"))
        given(useCase.getReceived(SHELTER, ApplicationStatus.SUBMITTED, 700, pageable))
            .willReturn(PageImpl(listOf(ReceivedItem(901, ApplicationStatus.SUBMITTED, "김가람", now, summary)), pageable, 1))

        mockMvc.get("/api/core/v1/adoption-applications/received?status=SUBMITTED&animalSeq=700&sort=regDttm,asc") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].applicantName") { value("김가람") }
            jsonPath("$.data.content[0].animalSummary.registrationNumber") { value(null as Any?) }
        }
        mockMvc.get("/api/core/v1/adoption-applications/received") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `12_16은 total과 네 상태를 돌려준다`() {
        given(useCase.countReceived(SHELTER, 700)).willReturn(linkedMapOf("total" to 23L, "SUBMITTED" to 3L, "APPROVED" to 16L, "ADOPTED" to 2L, "REJECTED" to 2L))

        mockMvc.get("/api/core/v1/adoption-applications/received/status-counts?animalSeq=700") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.total") { value(23) }
            jsonPath("$.data.APPROVED") { value(16) }
        }
    }

    @Test
    fun `12_10은 decision을 바꿔 넘긴다, 허용값 밖 decision은 400`() {
        given(useCase.review(SHELTER, 901, ReviewDecision.REJECT, "환경 부적합")).willReturn(Reviewed(901, ApplicationStatus.REJECTED, now, 77))
        given(useCase.review(SHELTER, 902, ReviewDecision.REJECT, null)).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_REJECTION_REASON_REQUIRED))

        mockMvc.post("/api/core/v1/adoption-applications/901/review") { json(this, SHELTER_TOKEN, """{"decision":"REJECT","rejectionReason":"환경 부적합"}""") }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("REJECTED") }
            jsonPath("$.data.reviewedUsrSeq") { value(77) }
        }
        mockMvc.post("/api/core/v1/adoption-applications/902/review") { json(this, SHELTER_TOKEN, """{"decision":"REJECT"}""") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ADOPTION_REJECTION_REASON_REQUIRED") }
        }
        mockMvc.post("/api/core/v1/adoption-applications/901/review") { json(this, SHELTER_TOKEN, """{"decision":"HOLD"}""") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        mockMvc.post("/api/core/v1/adoption-applications/901/review") { json(this, USER_TOKEN, """{"decision":"APPROVE"}""") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `12_11은 완료 결과를, 이미 다른 신청서로 끝난 동물은 409`() {
        given(useCase.complete(SHELTER, 901)).willReturn(Completed(901, ApplicationStatus.ADOPTED, 700, "ADOPTED", "ADOPTED", 901, 3))
        given(useCase.complete(SHELTER, 902)).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_ANIMAL_ALREADY_FINISHED))

        mockMvc.post("/api/core/v1/adoption-applications/901/complete") { json(this, SHELTER_TOKEN, "{}") }.andExpect {
            status { isOk() }
            jsonPath("$.data.applicationStatus") { value("ADOPTED") }
            jsonPath("$.data.completedApplicationSeq") { value(901) }
            jsonPath("$.data.otherApplicationsAutoRejected") { value(3) }
        }
        mockMvc.post("/api/core/v1/adoption-applications/902/complete") { json(this, SHELTER_TOKEN, "{}") }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ADOPTION_ANIMAL_ALREADY_FINISHED") }
        }
    }

    @Test
    fun `12_13은 REJECTED와 사유를 돌려준다, 승인 전이면 409`() {
        given(useCase.fail(SHELTER, 901, "진행 실패")).willReturn(Failed(901, ApplicationStatus.REJECTED, "진행 실패"))
        given(useCase.fail(SHELTER, 902, "진행 실패")).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_APPROVED))

        mockMvc.post("/api/core/v1/adoption-applications/901/fail") { json(this, SHELTER_TOKEN, """{"rejectionReason":"진행 실패"}""") }.andExpect {
            status { isOk() }
            jsonPath("$.data.rejectionReason") { value("진행 실패") }
        }
        mockMvc.post("/api/core/v1/adoption-applications/902/fail") { json(this, SHELTER_TOKEN, """{"rejectionReason":"진행 실패"}""") }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ADOPTION_APPLICATION_NOT_APPROVED") }
        }
    }

    @Test
    fun `토큰이 없으면 401`() {
        mockMvc.get("/api/core/v1/adoption/questions").andExpect { status { isUnauthorized() } }
        verifyNoInteractions(useCase)
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        private val SHELTER = AuthUser(77, Role.SHELTER, 7)
        private val USER_TOKEN = bearer(mapOf("userSeq" to 42, "role" to "USER"))
        private val SHELTER_TOKEN = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
