package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.simulation.adapter.out.persistence.SimulationPersistenceAdapter
import com.ssafy.animal.simulation.application.port.`in`.ActionLogQueryUseCase
import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.QSimulationActionLog.Companion.simulationActionLog
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatEffect
import com.ssafy.animal.simulation.domain.Stats
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@WebMvcTest(ActionLogController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ActionLogControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var actionLogQueryUseCase: ActionLogQueryUseCase

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL)

    @Test
    fun `18_7은 행동 이력을 분류·페이지·정렬과 함께 넘기고 명세 필드대로 돌려준다`() {
        val session = SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = Stats(satiety = 64, hydration = 58, cleanliness = 77, bond = 31, calmness = 46, health = 90, energy = 55),
        ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }
        val feed = SimulationActionLog.care(session, ActionCode.FEED, 22L, session.applyEffect(StatEffect.fromJson(mapOf("satiety" to 30)))).also {
            ReflectionTestUtils.setField(it, "actionLogSeq", 50002L)
            ReflectionTestUtils.setField(it, "regDttm", start.plusHours(1))
        }
        val pageable = PageRequest.of(0, 50, Sort.by(Sort.Order.desc("regDttm")))
        given(actionLogQueryUseCase.getActionLogs(17, 3001L, ActionType.CARE, pageable))
            .willReturn(PageImpl(listOf(ActionLogEntry(feed, food)), pageable, 1))

        mockMvc.get("/api/simulation/v1/simulations/3001/action-logs?actionType=CARE&page=0&size=50&sort=regDttm,desc") {
            header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].actionLogSeq") { value(50002) }
            jsonPath("$.data.content[0].actionType") { value("CARE") }
            jsonPath("$.data.content[0].actionCode") { value("FEED") }
            jsonPath("$.data.content[0].item.itemSeq") { value(22) }
            jsonPath("$.data.content[0].item.itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data.content[0].walkSeq") { value(null) }
            jsonPath("$.data.content[0].occurrenceSeq") { value(null) }
            jsonPath("$.data.content[0].statDelta.satiety") { value(30) }
            jsonPath("$.data.content[0].statsAfter.satiety") { value(94) }
            jsonPath("$.data.content[0].statsAfter.energy") { value(55) }
            jsonPath("$.data.content[0].regDttm") { value("2026-11-01T15:00:00+09:00") }
        }
    }

    @Test
    fun `18_7은 없는 분류 값이면 400, 보호소는 서비스의 404로 감춰진다`() {
        given(actionLogQueryUseCase.getActionLogs(77, 3001L, null, PageRequest.of(0, 50)))
            .willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001/action-logs?actionType=PLAY") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            }
        mockMvc.get("/api/simulation/v1/simulations/3001/action-logs") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
    }

    @Test
    fun `18_7 정렬은 regDttm만 허용하고 기본은 regDttm desc, 동률은 PK desc`() {
        val policy = SimulationPersistenceAdapter.ACTION_LOG_SORT_POLICY

        assertEquals(
            listOf(simulationActionLog.regDttm.desc(), simulationActionLog.actionLogSeq.desc()),
            policy.toOrderSpecifiers(Sort.unsorted()).toList(),
        )
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, assertFailsWith<BusinessException> { policy.toOrderSpecifiers(Sort.by("actionCode")) }.errorCode)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
