package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.EventQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.EventView
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(SimulationEventController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class SimulationEventControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var eventQueryUseCase: EventQueryUseCase

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private val water = EventScenario(
        1, "물그릇을 엎었어요", "목이 마를 텐데", TriggerContext.HOME, TriggerType.RANDOM, ActionCode.WATER,
        resultText = "시원하게 마셨어요.", neglectText = "물을 채워 줬어야 해요.",
    )
    private val vet = EventScenario(
        8, "축 처져 있어요", "평소와 달라요", TriggerContext.HOME, TriggerType.CONDITION, ActionCode.VET_VISIT,
        triggerCondition = mapOf("stat" to "health", "op" to "<", "value" to 40), resolveCost = 30_000, resultText = "진단서",
    )

    private fun occurrence(seq: Long, scenario: EventScenario) =
        EventOccurrence(3001L, scenario.scenarioSeq, start, start.plusHours(6)).also { ReflectionTestUtils.setField(it, "occurrenceSeq", seq) }

    @Test
    fun `18_12는 상태 필터·페이지를 넘기고 정답은 해결 후에만, 진료비는 병원 이벤트만 보여 준다`() {
        val pageable = PageRequest.of(0, 20)
        val resolved = occurrence(90001L, water).also { it.resolve(start.plusHours(1), 50_001L) }
        val expired = occurrence(90002L, water).also { it.expire() }
        val pendingVet = occurrence(90003L, vet)
        given(eventQueryUseCase.getEvents(17, 3001L, null, pageable))
            .willReturn(PageImpl(listOf(EventView(pendingVet, vet), EventView(expired, water), EventView(resolved, water)), pageable, 3))

        mockMvc.get("/api/simulation/v1/simulations/3001/events") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].occurrenceSeq") { value(90003) }
            jsonPath("$.data.content[0].scenario.title") { value("축 처져 있어요") }
            jsonPath("$.data.content[0].scenario.triggerType") { value("CONDITION") }
            jsonPath("$.data.content[0].vetVisit.cost") { value(30_000) }
            jsonPath("$.data.content[0].status") { value("PENDING") }
            jsonPath("$.data.content[0].resolvedActionCode") { value(null) }
            jsonPath("$.data.content[0].resultText") { value(null) }
            jsonPath("$.data.content[1].status") { value("EXPIRED") }
            jsonPath("$.data.content[1].vetVisit") { value(null) }
            jsonPath("$.data.content[1].resultText") { value("물을 채워 줬어야 해요.") }
            jsonPath("$.data.content[2].status") { value("RESOLVED") }
            jsonPath("$.data.content[2].resolvedActionCode") { value("WATER") }
            jsonPath("$.data.content[2].resolvedDttm") { value("2026-11-01T15:00:00+09:00") }
            jsonPath("$.data.content[2].resultText") { value("시원하게 마셨어요.") }
            jsonPath("$.data.totalElements") { value(3) }
        }
    }

    @Test
    fun `18_12는 상태 값을 넘기고, 없는 세션은 404`() {
        val pageable = PageRequest.of(0, 20)
        given(eventQueryUseCase.getEvents(17, 3001L, OccurrenceStatus.PENDING, pageable)).willReturn(PageImpl(emptyList(), pageable, 0))
        given(eventQueryUseCase.getEvents(77, 3001L, null, pageable)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001/events?status=PENDING") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content") { isEmpty() }
        }
        mockMvc.get("/api/simulation/v1/simulations/3001/events") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
