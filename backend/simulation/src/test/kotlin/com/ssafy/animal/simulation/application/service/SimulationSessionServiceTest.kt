package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.simulation.application.port.`in`.Equipment
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimulationSessionServiceTest {

    private val sessions = mutableListOf<SimulationSession>()
    private val logs = mutableListOf<SimulationActionLog>()
    private val breeds = mutableMapOf<Int, BreedInfo>()
    private val breedLookups = mutableListOf<Int>()

    private val sessionRepository = object : SimulationSessionRepository {
        override fun findActiveForUpdate(userSeq: Int) =
            sessions.firstOrNull { it.userSeq == userSeq && it.status == SessionStatus.IN_PROGRESS }

        override fun save(session: SimulationSession) = session.also {
            ReflectionTestUtils.setField(it, "sessionSeq", 3000L + sessions.size + 1)
            sessions += it
        }
    }

    private val actionLogRepository = object : SimulationActionLogRepository {
        override fun save(log: SimulationActionLog) = log.also { logs += it }
    }

    private val breedReader = object : BreedReader {
        override fun findBreed(breedSeq: Int): BreedInfo? {
            breedLookups += breedSeq
            return breeds[breedSeq]
        }
    }

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 123_456_789, ZoneOffset.ofHours(9))
    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)

    private val service = SimulationSessionService(
        sessionRepository,
        actionLogRepository,
        breedReader,
        StatCalculator(properties.stats, properties.tick),
        properties,
        Clock.fixed(now.toInstant(), zone),
    )

    private val maltese = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")

    @Test
    fun `품종의 종·성격을 스냅샷해 세션을 만들고 SESSION_START 로그를 남긴다`() {
        breeds[21] = maltese

        val detail = service.start(17, StartSimulationCommand(breedSeq = 21, animalName = "초코", expectedCost = 150_000))

        val session = sessions.single()
        val startedAt = now.withNano(123_456_000) // 마이크로초까지
        assertEquals(17, session.userSeq)
        assertEquals(21, session.breedSeq)
        assertEquals("초코", session.animalName)
        assertEquals(SpeciesType.DOG, session.speciesType)
        assertEquals(Personality.FRIENDLY, session.personality)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(150_000, session.expectedCost)
        assertEquals(150_000, session.balance)
        assertEquals(startedAt, session.startedDttm)
        assertEquals(startedAt.plusDays(7), session.endsDttm)
        assertEquals(startedAt, session.statsCalculatedDttm)
        assertEquals(StatCalculator(properties.stats, properties.tick).initialStats(), session.stats())

        val log = logs.single()
        assertEquals(session.sessionSeq, log.sessionSeq)
        assertEquals(ActionCode.SESSION_START, log.actionCode)

        assertEquals(session, detail.session)
        assertEquals(maltese, detail.breed)
        assertEquals(Equipment.NONE, detail.equipment)
        assertNull(detail.activeWalkSeq)
    }

    @Test
    fun `진행 중 세션이 있으면 409이고 품종 조회·저장을 하지 않는다`() {
        breeds[21] = maltese
        service.start(17, StartSimulationCommand(21, "초코", 150_000))
        breedLookups.clear()

        assertError(SimulationErrorCode.SIMULATION_ACTIVE_ALREADY_EXISTS) {
            service.start(17, StartSimulationCommand(21, "보리", 100_000))
        }
        assertTrue(breedLookups.isEmpty())
        assertEquals(1, sessions.size)
        assertEquals(1, logs.size)
    }

    @Test
    fun `다른 사용자의 진행 중 세션은 막지 않는다`() {
        breeds[21] = maltese
        service.start(17, StartSimulationCommand(21, "초코", 150_000))

        service.start(18, StartSimulationCommand(21, "보리", 0))

        assertEquals(2, sessions.size)
    }

    @Test
    fun `없는 품종이면 404 SIMULATION_BREED_NOT_FOUND`() {
        assertError(SimulationErrorCode.SIMULATION_BREED_NOT_FOUND) {
            service.start(17, StartSimulationCommand(999, "초코", 150_000))
        }
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun `품종의 종이 ETC면 409 SIMULATION_SPECIES_NOT_SUPPORTED`() {
        breeds[90] = BreedInfo(90, "기타", CatalogSpeciesCode.ETC, Personality.TIMID, null)

        assertError(SimulationErrorCode.SIMULATION_SPECIES_NOT_SUPPORTED) {
            service.start(17, StartSimulationCommand(90, "초코", 150_000))
        }
        assertTrue(sessions.isEmpty())
        assertTrue(logs.isEmpty())
    }

    private fun assertError(expected: ErrorCode, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BusinessException> { block() }.errorCode)
    }
}
