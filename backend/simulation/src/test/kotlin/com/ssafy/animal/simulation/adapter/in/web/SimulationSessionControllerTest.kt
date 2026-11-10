package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.AbandonSimulationUseCase
import com.ssafy.animal.simulation.application.port.`in`.SessionReportView
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionSummary
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSource
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SpendRecord
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(SimulationSessionController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class SimulationSessionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var startSimulationUseCase: StartSimulationUseCase

    @MockitoBean
    lateinit var sessionQueryUseCase: SimulationSessionQueryUseCase

    @MockitoBean
    lateinit var abandonUseCase: AbandonSimulationUseCase

    private val startedAt = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val maltese = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")

    private fun session() = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = startedAt, endsAt = startedAt.plusDays(7),
        initialStats = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60),
    ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }

    private fun postStart(token: String?, body: String) = mockMvc.post("/api/simulation/v1/simulations") {
        token?.let { header(HttpHeaders.AUTHORIZATION, it) }
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `18_1은 201과 세션 상세를 명세 필드대로 돌려준다`() {
        given(startSimulationUseCase.start(17, StartSimulationCommand(21, "초코", 150_000)))
            .willReturn(SimulationSessionDetail(session(), maltese, Equipment.NONE, activeWalkSeq = null))

        postStart(USER_TOKEN, """{"breedSeq":21,"animalName":"초코","expectedCost":150000}""").andExpect {
            status { isCreated() }
            jsonPath("$.data.sessionSeq") { value(3001) }
            jsonPath("$.data.userSeq") { value(17) }
            jsonPath("$.data.animalName") { value("초코") }
            jsonPath("$.data.breed.breedSeq") { value(21) }
            jsonPath("$.data.breed.breedName") { value("말티즈") }
            jsonPath("$.data.breed.speciesType") { value("DOG") }
            jsonPath("$.data.breed.representativeImageUrl") { value("https://cdn/breeds/maltese.png") }
            jsonPath("$.data.speciesType") { value("DOG") }
            jsonPath("$.data.personality") { value("FRIENDLY") }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
            jsonPath("$.data.expectedCost") { value(150000) }
            jsonPath("$.data.balance") { value(150000) }
            jsonPath("$.data.startedDttm") { value("2026-11-01T14:00:00+09:00") }
            jsonPath("$.data.endsDttm") { value("2026-11-08T14:00:00+09:00") }
            jsonPath("$.data.endedDttm") { value(null) }
            jsonPath("$.data.stats.satiety") { value(70) }
            jsonPath("$.data.stats.hydration") { value(70) }
            jsonPath("$.data.stats.cleanliness") { value(80) }
            jsonPath("$.data.stats.bond") { value(30) }
            jsonPath("$.data.stats.calmness") { value(50) }
            jsonPath("$.data.stats.health") { value(90) }
            jsonPath("$.data.stats.energy") { value(60) }
            jsonPath("$.data.stats.calculatedDttm") { value("2026-11-01T14:00:00+09:00") }
            jsonPath("$.data.lastActionDttm") { value(null) }
            jsonPath("$.data.equipment.foodBowl") { value(false) }
            jsonPath("$.data.equipment.waterBowl") { value(false) }
            jsonPath("$.data.equipment.litterBox") { value(false) }
            jsonPath("$.data.equipment.toy") { value(false) }
            jsonPath("$.data.pendingEvent") { value(null) }
            jsonPath("$.data.activeWalkSeq") { value(null) }
        }
    }

    @Test
    fun `SHELTER는 403 AUTH_FORBIDDEN이고 서비스를 호출하지 않는다`() {
        postStart(SHELTER_TOKEN, """{"breedSeq":21,"animalName":"초코","expectedCost":150000}""").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(startSimulationUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `토큰이 없으면 401 AUTH_UNAUTHORIZED`() {
        postStart(null, """{"breedSeq":21,"animalName":"초코","expectedCost":150000}""").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
    }

    @Test
    fun `필수값 누락·이름 6자 초과·음수 비용은 400 COMMON_INVALID_INPUT`() {
        listOf(
            """{"animalName":"초코","expectedCost":150000}""" to "breedSeq",
            """{"breedSeq":21,"expectedCost":150000}""" to "animalName",
            """{"breedSeq":21,"animalName":"  ","expectedCost":150000}""" to "animalName",
            """{"breedSeq":21,"animalName":"일곱글자이름임","expectedCost":150000}""" to "animalName",
            """{"breedSeq":21,"animalName":"초코"}""" to "expectedCost",
            """{"breedSeq":21,"animalName":"초코","expectedCost":-1}""" to "expectedCost",
        ).forEach { (body, field) ->
            postStart(USER_TOKEN, body).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value(field) }
            }
        }
        then(startSimulationUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `서비스의 도메인 오류를 그대로 응답한다`() {
        given(startSimulationUseCase.start(17, StartSimulationCommand(21, "초코", 150_000)))
            .willThrow(BusinessException(SimulationErrorCode.SIMULATION_ACTIVE_ALREADY_EXISTS))

        postStart(USER_TOKEN, """{"breedSeq":21,"animalName":"초코","expectedCost":150000}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SIMULATION_ACTIVE_ALREADY_EXISTS") }
            jsonPath("$.data") { value(null) }
        }
    }

    @Test
    fun `18_2는 진행 중 세션 상세를 돌려준다`() {
        val equipment = Equipment(foodBowl = true, waterBowl = true, litterBox = false, toy = false)
        given(sessionQueryUseCase.getActive(17)).willReturn(SimulationSessionDetail(session(), maltese, equipment, activeWalkSeq = 8001L))

        mockMvc.get("/api/simulation/v1/simulations/me/active") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.sessionSeq") { value(3001) }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
            jsonPath("$.data.stats.satiety") { value(70) }
            jsonPath("$.data.equipment.foodBowl") { value(true) }
            jsonPath("$.data.equipment.waterBowl") { value(true) }
            jsonPath("$.data.equipment.litterBox") { value(false) }
            jsonPath("$.data.activeWalkSeq") { value(8001) }
            jsonPath("$.data.pendingEvent") { value(null) }
        }
    }

    @Test
    fun `18_2는 진행 중 세션이 없으면 200 data null`() {
        given(sessionQueryUseCase.getActive(17)).willReturn(null)

        mockMvc.get("/api/simulation/v1/simulations/me/active") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("SUCCESS") }
            jsonPath("$.data") { value(null) }
        }
    }

    @Test
    fun `18_2를 SHELTER가 부르면 403 AUTH_FORBIDDEN`() {
        mockMvc.get("/api/simulation/v1/simulations/me/active") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(sessionQueryUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `18_3은 내 세션 목록을 상태·페이지·정렬과 함께 넘기고 목록 DTO로 돌려준다`() {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("startedDttm")))
        given(sessionQueryUseCase.getMySessions(17, SessionStatus.IN_PROGRESS, pageable))
            .willReturn(PageImpl(listOf(SimulationSessionSummary(session(), maltese, hasReport = false)), pageable, 1))

        mockMvc.get("/api/simulation/v1/simulations/me?status=IN_PROGRESS&page=0&size=20&sort=startedDttm,desc") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.hasNext") { value(false) }
            jsonPath("$.data.content[0].sessionSeq") { value(3001) }
            jsonPath("$.data.content[0].animalName") { value("초코") }
            jsonPath("$.data.content[0].breed.breedName") { value("말티즈") }
            jsonPath("$.data.content[0].speciesType") { value("DOG") }
            jsonPath("$.data.content[0].personality") { value("FRIENDLY") }
            jsonPath("$.data.content[0].status") { value("IN_PROGRESS") }
            jsonPath("$.data.content[0].balance") { value(150000) }
            jsonPath("$.data.content[0].endedDttm") { value(null) }
            jsonPath("$.data.content[0].hasReport") { value(false) }
            jsonPath("$.data.content[0].stats") { doesNotExist() }
        }
    }

    @Test
    fun `18_3은 없는 상태 값이면 400 COMMON_INVALID_INPUT, SHELTER는 403`() {
        mockMvc.get("/api/simulation/v1/simulations/me?status=DONE") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        mockMvc.get("/api/simulation/v1/simulations/me") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(sessionQueryUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `18_4는 세션 상세를 돌려준다`() {
        given(sessionQueryUseCase.getDetail(17, 3001L)).willReturn(SimulationSessionDetail(session(), maltese, Equipment.NONE, null))

        mockMvc.get("/api/simulation/v1/simulations/3001") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.sessionSeq") { value(3001) }
            jsonPath("$.data.breed.breedName") { value("말티즈") }
        }
    }

    @Test
    fun `18_4는 SHELTER도 역할로 막지 않고 서비스의 404로 존재를 감춘다`() {
        given(sessionQueryUseCase.getDetail(77, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
    }

    @Test
    fun `18_4는 세션 번호가 숫자가 아니면 400 COMMON_INVALID_INPUT`() {
        mockMvc.get("/api/simulation/v1/simulations/abc") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `18_22는 세션 번호·상태·스탯만 명세 필드대로 돌려준다`() {
        given(sessionQueryUseCase.getStats(17, 3001L)).willReturn(session())

        mockMvc.get("/api/simulation/v1/simulations/3001/stats") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.sessionSeq") { value(3001) }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
            jsonPath("$.data.stats.satiety") { value(70) }
            jsonPath("$.data.stats.hydration") { value(70) }
            jsonPath("$.data.stats.cleanliness") { value(80) }
            jsonPath("$.data.stats.bond") { value(30) }
            jsonPath("$.data.stats.calmness") { value(50) }
            jsonPath("$.data.stats.health") { value(90) }
            jsonPath("$.data.stats.energy") { value(60) }
            jsonPath("$.data.stats.calculatedDttm") { value("2026-11-01T14:00:00+09:00") }
            jsonPath("$.data.breed") { doesNotExist() }
            jsonPath("$.data.equipment") { doesNotExist() }
        }
    }

    @Test
    fun `18_22는 끝난 세션도 200으로 상태와 함께 돌려준다`() {
        val ended = session().also { ReflectionTestUtils.setField(it, "status", SessionStatus.FAILED) }
        given(sessionQueryUseCase.getStats(17, 3001L)).willReturn(ended)

        mockMvc.get("/api/simulation/v1/simulations/3001/stats") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("FAILED") }
        }
    }

    @Test
    fun `18_22는 SHELTER도 역할로 막지 않고 서비스의 404로 존재를 감추며 세션 번호 타입 오류는 400`() {
        given(sessionQueryUseCase.getStats(77, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001/stats") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
        mockMvc.get("/api/simulation/v1/simulations/abc/stats") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `18_5는 포기한 세션과 리포트를 명세 필드대로 돌려준다`() {
        val session = session()
        val endedAt = startedAt.plusDays(1)
        val report = ReportAggregator.aggregate(
            ReportSource(session, listOf(SpendRecord("FOOD", 21_200, startedAt.plusHours(1))), 12, 0, 0, 0),
            sessionDays = 7,
        )
        session.end(SessionStatus.ABANDONED, endedAt)
        given(abandonUseCase.abandon(17, 3001L)).willReturn(SessionReportView(session, maltese, report))

        mockMvc.post("/api/simulation/v1/simulations/3001/abandon") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.sessionSeq") { value(3001) }
            jsonPath("$.data.status") { value("ABANDONED") }
            jsonPath("$.data.endedDttm") { value("2026-11-02T14:00:00+09:00") }
            jsonPath("$.data.report.isFinal") { value(true) }
            jsonPath("$.data.report.status") { value("ABANDONED") }
            jsonPath("$.data.report.breed.breedName") { value("말티즈") }
            jsonPath("$.data.report.totalSpent") { value(21200) }
            jsonPath("$.data.report.difference") { value(150000 - 21200) }
            jsonPath("$.data.report.spentByCategory.FOOD") { value(21200) }
            jsonPath("$.data.report.spentByCategory.EVENT") { value(0) }
            jsonPath("$.data.report.dailySpent.1") { value(21200) }
            jsonPath("$.data.report.finalStats.satiety") { value(70) }
            jsonPath("$.data.report.actionCount") { value(12) }
            jsonPath("$.data.report.walkCount") { value(0) }
            jsonPath("$.data.report.summary") { value(null) }
        }
    }

    @Test
    fun `18_5는 서비스의 404·409를 그대로 응답한다(보호소는 404로 감춰짐)`() {
        given(abandonUseCase.abandon(77, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))
        given(abandonUseCase.abandon(17, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS))

        mockMvc.post("/api/simulation/v1/simulations/3001/abandon") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
        mockMvc.post("/api/simulation/v1/simulations/3001/abandon") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SIMULATION_NOT_IN_PROGRESS") }
        }
    }

    companion object {
        private val USER_TOKEN = WebTestTokens.USER
        private val SHELTER_TOKEN = WebTestTokens.SHELTER

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
