package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.ReportListItem
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.`in`.SimulationReportQueryUseCase
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.CareFixtures
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
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
        val care = CareFixtures.care(
            startedAt, evaluatedDays = 2,
            logs = listOf(CareFixtures.log(ActionCode.FEED, startedAt.plusHours(1)), CareFixtures.log(ActionCode.GIVE_WATER, startedAt.plusDays(1))),
        )
        given(reportQueryUseCase.getReport(17, Role.USER, 3001L)).willReturn(SessionReportView(session, maltese, report(session), care, isFinal = false))

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
            jsonPath("$.data.evaluatedDays") { value(2) }
            jsonPath("$.data.careGuideline.FEED") { value(3) }
            jsonPath("$.data.careGuideline.WALK") { value(1) }
            jsonPath("$.data.dailyCare.1.FEED") { value(1) }
            jsonPath("$.data.dailyCare.2.WATER") { value(1) }
            jsonPath("$.data.dailyCare.7.WALK") { value(0) }
        }
    }

    @Test
    fun `18_18은 SHELTER 토큰이면 보호소 역할로 종료 리포트를 조회한다`() {
        val session = session().apply { end(SessionStatus.COMPLETED, startedAt.plusDays(7)) }
        given(reportQueryUseCase.getReport(77, Role.SHELTER, 3001L)).willReturn(SessionReportView(session, maltese, report(session), CareFixtures.care(startedAt)))

        mockMvc.get("/api/simulation/v1/simulations/3001/report") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("COMPLETED") }
            jsonPath("$.data.isFinal") { value(true) }
            jsonPath("$.data.endedDttm") { value("2026-11-08T14:00:00+09:00") }
            jsonPath("$.data.evaluatedDays") { value(7) }
            jsonPath("$.data.dailyCare.7.FEED") { value(0) }
        }
    }

    @Test
    fun `18_18 고양이 세션의 권장 횟수와 일차별 돌봄에는 WALK 키가 없다`() {
        val session = SimulationSession.start(
            userSeq = 17, breedSeq = 31, animalName = "나비", speciesType = SpeciesType.CAT, personality = Personality.TIMID,
            expectedCost = 120_000, startedAt = startedAt, endsAt = startedAt.plusDays(7), initialStats = CareFixtures.STATS,
        ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }.apply { end(SessionStatus.ABANDONED, startedAt.plusDays(2)) }
        val care = CareFixtures.care(
            startedAt, species = SpeciesType.CAT, evaluatedDays = 3, logs = listOf(CareFixtures.log(ActionCode.GIVE_CATNIP, startedAt.plusHours(2))),
        )
        given(reportQueryUseCase.getReport(17, Role.USER, 3001L)).willReturn(SessionReportView(session, maltese, report(session), care))

        mockMvc.get("/api/simulation/v1/simulations/3001/report") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.evaluatedDays") { value(3) }
            jsonPath("$.data.careGuideline.PLAY") { value(2) }
            jsonPath("$.data.careGuideline.WALK") { doesNotExist() }
            jsonPath("$.data.dailyCare.1.PLAY") { value(1) }
            jsonPath("$.data.dailyCare.1.WALK") { doesNotExist() }
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

    private fun endedItem(user: UserInfo? = null): ReportListItem {
        val session = session().apply { end(SessionStatus.COMPLETED, startedAt.plusDays(7)) }
        return ReportListItem(session, maltese, report(session), user)
    }

    @Test
    fun `18_19는 내 리포트 목록을 페이지·정렬과 함께 넘기고 목록 DTO로 돌려준다`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("endedDttm")))
        given(reportQueryUseCase.getMyReports(17, pageable)).willReturn(PageImpl(listOf(endedItem()), pageable, 1))

        mockMvc.get("/api/simulation/v1/simulations/reports?sort=endedDttm,desc") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].sessionSeq") { value(3001) }
            jsonPath("$.data.content[0].animalName") { value("초코") }
            jsonPath("$.data.content[0].breed.breedName") { value("말티즈") }
            jsonPath("$.data.content[0].status") { value("COMPLETED") }
            jsonPath("$.data.content[0].endedDttm") { value("2026-11-08T14:00:00+09:00") }
            jsonPath("$.data.content[0].totalSpent") { value(21_200) }
            jsonPath("$.data.content[0].difference") { value(128_800) }
            jsonPath("$.data.content[0].user") { doesNotExist() }
            jsonPath("$.data.totalElements") { value(1) }
        }
    }

    @Test
    fun `18_19는 보호소면 403, 18_20은 일반 회원이면 403`() {
        mockMvc.get("/api/simulation/v1/simulations/reports") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        mockMvc.get("/api/simulation/v1/simulations/reports/shelter") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(reportQueryUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `18_20은 회원 필터를 넘기고 회원 표시 정보를 붙인다`() {
        val pageable = PageRequest.of(0, 20)
        val user = UserInfo(17, "반려가족", "USER", isDeleted = false, profileImageSeq = 1301, profileImageUrl = "https://cdn/1301.jpg")
        given(reportQueryUseCase.getReportsForShelter(17, pageable)).willReturn(PageImpl(listOf(endedItem(user)), pageable, 1))

        mockMvc.get("/api/simulation/v1/simulations/reports/shelter?userSeq=17") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].sessionSeq") { value(3001) }
            jsonPath("$.data.content[0].user.userSeq") { value(17) }
            jsonPath("$.data.content[0].user.displayName") { value("반려가족") }
            jsonPath("$.data.content[0].user.role") { value("USER") }
            jsonPath("$.data.content[0].user.profileImage.imageSeq") { value(1301) }
            jsonPath("$.data.content[0].user.profileImage.imageUrl") { value("https://cdn/1301.jpg") }
            jsonPath("$.data.content[0].breed.breedName") { value("말티즈") }
        }
    }

    @Test
    fun `18_20은 탈퇴했거나 회원 행이 없으면 (탈퇴한 사용자)로 사진 없이 보여 준다`() {
        val pageable = PageRequest.of(0, 20)
        val withdrawn = UserInfo(17, "반려가족", "USER", isDeleted = true, profileImageSeq = 1301, profileImageUrl = "https://cdn/1301.jpg")
        given(reportQueryUseCase.getReportsForShelter(null, pageable)).willReturn(PageImpl(listOf(endedItem(withdrawn), endedItem(null)), pageable, 2))

        mockMvc.get("/api/simulation/v1/simulations/reports/shelter") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].user.displayName") { value("(탈퇴한 사용자)") }
            jsonPath("$.data.content[0].user.profileImage") { value(null) }
            jsonPath("$.data.content[1].user.userSeq") { value(17) }
            jsonPath("$.data.content[1].user.displayName") { value("(탈퇴한 사용자)") }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
