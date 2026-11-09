package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.`in`.SimulationReportQueryUseCase
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSource
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.SpendRecord
import com.ssafy.animal.simulation.domain.Stats
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(SimulationReportController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class SimulationReportControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var reportQueryUseCase: SimulationReportQueryUseCase

    private val startedAt = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val maltese = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")

    private fun session() = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = startedAt, endsAt = startedAt.plusDays(7),
        initialStats = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60),
    ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }

    private fun report(session: SimulationSession) = ReportAggregator.aggregate(
        ReportSource(session, listOf(SpendRecord("FOOD", 21_200, startedAt.plusHours(1))), 12, 2, 1, 0),
        sessionDays = 7,
    )

    @Test
    fun `18_18은 USER 토큰의 회원 번호·역할로 중간 리포트를 돌려준다`() {
        val session = session()
        given(reportQueryUseCase.getReport(17, Role.USER, 3001L)).willReturn(SessionReportView(session, maltese, report(session), isFinal = false))

        mockMvc.get("/api/simulation/v1/simulations/3001/report") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.sessionSeq") { value(3001) }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
            jsonPath("$.data.isFinal") { value(false) }
            jsonPath("$.data.endedDttm") { value(null) }
            jsonPath("$.data.breed.breedName") { value("말티즈") }
            jsonPath("$.data.totalSpent") { value(21_200) }
            jsonPath("$.data.difference") { value(128_800) }
            jsonPath("$.data.spentByCategory.FOOD") { value(21_200) }
            jsonPath("$.data.spentByCategory.EVENT") { value(0) }
            jsonPath("$.data.dailySpent.7") { value(0) }
            jsonPath("$.data.finalStats.satiety") { value(70) }
            jsonPath("$.data.actionCount") { value(12) }
            jsonPath("$.data.eventCount") { value(2) }
            jsonPath("$.data.eventResolvedCount") { value(1) }
            jsonPath("$.data.summary") { value(null) }
        }
    }

    @Test
    fun `18_18은 SHELTER 토큰이면 보호소 역할로 종료 리포트를 조회한다`() {
        val session = session().apply { end(SessionStatus.COMPLETED, startedAt.plusDays(7)) }
        given(reportQueryUseCase.getReport(77, Role.SHELTER, 3001L)).willReturn(SessionReportView(session, maltese, report(session)))

        mockMvc.get("/api/simulation/v1/simulations/3001/report") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("COMPLETED") }
            jsonPath("$.data.isFinal") { value(true) }
            jsonPath("$.data.endedDttm") { value("2026-11-08T14:00:00+09:00") }
        }
    }

    @Test
    fun `18_18은 볼 수 없는 리포트면 404, 토큰이 없으면 401`() {
        given(reportQueryUseCase.getReport(77, Role.SHELTER, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001/report") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
        mockMvc.get("/api/simulation/v1/simulations/3001/report").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
