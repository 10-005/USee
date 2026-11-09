package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.CareActionResult
import com.ssafy.animal.simulation.application.port.`in`.CareActionUseCase
import com.ssafy.animal.simulation.application.port.`in`.ConsumedItem
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stat
import com.ssafy.animal.simulation.domain.StatEffect
import com.ssafy.animal.simulation.domain.Stats
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(CareActionController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class CareActionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var careActionUseCase: CareActionUseCase

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, effects = mapOf("satiety" to 30))

    private fun post(token: String, body: String) = mockMvc.post("/api/simulation/v1/simulations/3001/actions") {
        header(HttpHeaders.AUTHORIZATION, token)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `18_6은 행동 결과를 명세 필드대로 돌려준다`() {
        val session = SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = Stats(satiety = 64, hydration = 58, cleanliness = 77, bond = 31, calmness = 46, health = 90, energy = 55),
        ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }
        val effect = session.applyEffect(StatEffect(mapOf(Stat.SATIETY to 30)))
        val log = SimulationActionLog.care(session, ActionCode.FEED, 22L, effect).also { ReflectionTestUtils.setField(it, "actionLogSeq", 50001L) }
        given(careActionUseCase.act(17, 3001L, CareActionCommand(ActionCode.FEED, 22L)))
            .willReturn(CareActionResult(log, session, ConsumedItem(food, remainingQuantity = 11), effect.delta))

        post(WebTestTokens.USER, """{"actionCode":"FEED","itemSeq":22}""").andExpect {
            status { isOk() }
            jsonPath("$.data.actionLogSeq") { value(50001) }
            jsonPath("$.data.actionType") { value("CARE") }
            jsonPath("$.data.actionCode") { value("FEED") }
            jsonPath("$.data.consumedItem.itemSeq") { value(22) }
            jsonPath("$.data.consumedItem.itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data.consumedItem.remainingQuantity") { value(11) }
            jsonPath("$.data.statDelta.satiety") { value(30) }
            jsonPath("$.data.stats.satiety") { value(94) }
            jsonPath("$.data.stats.calculatedDttm") { value("2026-11-01T14:00:00+09:00") }
            jsonPath("$.data.balance") { value(150000) }
            jsonPath("$.data.resolvedEvent") { value(null) }
        }
    }

    @Test
    fun `actionCode 누락은 400 COMMON_INVALID_INPUT, 없는 코드는 400 COMMON_INVALID_JSON`() {
        post(WebTestTokens.USER, """{"itemSeq":22}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            jsonPath("$.errors[0].field") { value("actionCode") }
        }
        post(WebTestTokens.USER, """{"actionCode":"DANCE"}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_JSON") }
        }
        then(careActionUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `서비스의 도메인 오류를 그대로 응답한다(보호소는 404로 감춰짐)`() {
        given(careActionUseCase.act(77, 3001L, CareActionCommand(ActionCode.WATER, null)))
            .willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))
        given(careActionUseCase.act(17, 3001L, CareActionCommand(ActionCode.WATER, null)))
            .willThrow(BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING))

        post(WebTestTokens.SHELTER, """{"actionCode":"WATER"}""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
        post(WebTestTokens.USER, """{"actionCode":"WATER"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SIMULATION_EQUIPMENT_MISSING") }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
