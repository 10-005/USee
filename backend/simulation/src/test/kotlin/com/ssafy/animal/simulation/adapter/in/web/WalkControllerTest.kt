package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.CareActionResult
import com.ssafy.animal.simulation.application.port.`in`.WalkActionResult
import com.ssafy.animal.simulation.application.port.`in`.WalkEndResult
import com.ssafy.animal.simulation.application.port.`in`.WalkHeartbeatResult
import com.ssafy.animal.simulation.application.port.`in`.WalkStartResult
import com.ssafy.animal.simulation.application.port.`in`.WalkUseCase
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stat
import com.ssafy.animal.simulation.domain.StatEffect
import com.ssafy.animal.simulation.domain.Stats
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(WalkController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class WalkControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var walkUseCase: WalkUseCase

    private val start = OffsetDateTime.of(2026, 11, 1, 17, 0, 0, 0, ZoneOffset.ofHours(9))
    private val base = "/api/simulation/v1/simulations/3001/walks"

    private val session = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
        initialStats = Stats(satiety = 64, hydration = 58, cleanliness = 77, bond = 31, calmness = 46, health = 90, energy = 55),
    ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }

    private fun walk() = SimulationWalk(3001L, start).also { ReflectionTestUtils.setField(it, "walkSeq", 8001L) }

    @Test
    fun `18_14는 201과 산책 번호·하트비트 간격을 돌려준다`() {
        given(walkUseCase.start(17, 3001L)).willReturn(WalkStartResult(walk(), session, Duration.ofSeconds(60)))

        mockMvc.post(base) { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isCreated() }
            jsonPath("$.data.walkSeq") { value(8001) }
            jsonPath("$.data.status") { value("IN_PROGRESS") }
            jsonPath("$.data.heartbeatIntervalSeconds") { value(60) }
            jsonPath("$.data.stats.energy") { value(55) }
        }
    }

    @Test
    fun `18_15는 새 이벤트가 없으면 newEvent·pendingEvent가 null`() {
        given(walkUseCase.heartbeat(17, 3001L, 8001L)).willReturn(WalkHeartbeatResult(walk(), session, 3, null, null))

        mockMvc.post("$base/8001/heartbeat") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.elapsedMinutes") { value(3) }
            jsonPath("$.data.newEvent") { value(null) }
            jsonPath("$.data.pendingEvent") { value(null) }
        }
    }

    @Test
    fun `18_16은 행동 결과에 산책 번호를 붙여 돌려준다`() {
        val effect = session.applyEffect(StatEffect(mapOf(Stat.HYDRATION to 20)))
        val log = SimulationActionLog.walk(session, ActionCode.GIVE_WATER, 8001L, effect).also { ReflectionTestUtils.setField(it, "actionLogSeq", 50010L) }
        given(walkUseCase.act(17, 3001L, 8001L, CareActionCommand(ActionCode.GIVE_WATER, null)))
            .willReturn(WalkActionResult(8001L, CareActionResult(log, session, null, effect.delta)))

        mockMvc.post("$base/8001/actions") {
            header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER)
            contentType = MediaType.APPLICATION_JSON
            content = """{"actionCode":"GIVE_WATER"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.walkSeq") { value(8001) }
            jsonPath("$.data.actionType") { value("WALK") }
            jsonPath("$.data.statDelta.hydration") { value(20) }
            jsonPath("$.data.resolvedEvent") { value(null) }
        }
    }

    @Test
    fun `18_17은 산책 길이와 효과를 돌려준다`() {
        val walk = walk().apply { end(start.plusMinutes(35)) }
        given(walkUseCase.end(17, 3001L, 8001L))
            .willReturn(WalkEndResult(walk, session, 35, mapOf(Stat.ENERGY to 21, Stat.HYDRATION to -9), 2))

        mockMvc.post("$base/8001/end") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ENDED") }
            jsonPath("$.data.endedDttm") { value("2026-11-01T17:35:00+09:00") }
            jsonPath("$.data.durationMinutes") { value(35) }
            jsonPath("$.data.statDelta.energy") { value(21) }
            jsonPath("$.data.eventCount") { value(2) }
        }
    }

    @Test
    fun `서비스 오류를 그대로 응답하고 토큰이 없으면 401`() {
        given(walkUseCase.start(17, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_WALK_NOT_FOR_SPECIES))

        mockMvc.post(base) { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SIMULATION_WALK_NOT_FOR_SPECIES") }
        }
        mockMvc.post(base).andExpect { status { isUnauthorized() } }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
