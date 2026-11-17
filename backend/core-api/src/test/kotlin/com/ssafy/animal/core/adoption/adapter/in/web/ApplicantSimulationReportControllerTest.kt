package com.ssafy.animal.core.adoption.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantSimulationReport
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantSimulationReportUseCase
import com.ssafy.animal.core.adoption.application.port.`in`.SimulationBreed
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.security.KeyPairGenerator
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date

@WebMvcTest(ApplicantSimulationReportController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ApplicantSimulationReportControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var useCase: ApplicantSimulationReportUseCase

    private val path = "/api/core/v1/adoption-applications/901/simulation-reports"
    private val start = OffsetDateTime.parse("2026-11-01T14:00:00+09:00")

    @Test
    fun `12_15는 보호소 토큰으로 신청자의 리포트 목록을 동물 정보·기간·총지출로 돌려준다`() {
        given(useCase.getApplicantReports(SHELTER, 901)).willReturn(
            listOf(
                ApplicantSimulationReport(
                    3001, "초코", SimulationBreed(21, "말티즈", SpeciesCode.DOG, "https://cdn/21.jpg"), start, start.plusDays(7), 182_000,
                ),
            ),
        )

        mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].sessionSeq") { value(3001) }
            jsonPath("$.data[0].animalName") { value("초코") }
            jsonPath("$.data[0].breed.breedName") { value("말티즈") }
            jsonPath("$.data[0].breed.speciesType") { value("DOG") }
            jsonPath("$.data[0].breed.representativeImageUrl") { value("https://cdn/21.jpg") }
            jsonPath("$.data[0].startedDttm") { value("2026-11-01T14:00:00+09:00") }
            jsonPath("$.data[0].endedDttm") { value("2026-11-08T14:00:00+09:00") }
            jsonPath("$.data[0].totalSpent") { value(182_000) }
            jsonPath("$.data[0].status") { doesNotExist() }
            jsonPath("$.data[0].summary") { doesNotExist() }
        }
    }

    @Test
    fun `12_15는 일반 회원이면 403, 볼 수 없는 신청서면 404`() {
        mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(useCase).shouldHaveNoInteractions()

        given(useCase.getApplicantReports(SHELTER, 901)).willThrow(BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND))
        mockMvc.get(path) { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ADOPTION_APPLICATION_NOT_FOUND") }
        }
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
