package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.Equipment
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stats
import io.jsonwebtoken.Jwts
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
import java.security.KeyPairGenerator
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date

@WebMvcTest(SimulationSessionController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class SimulationSessionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var startSimulationUseCase: StartSimulationUseCase

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

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        private val USER_TOKEN = bearer(mapOf("userSeq" to 17, "role" to "USER"))
        private val SHELTER_TOKEN = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
