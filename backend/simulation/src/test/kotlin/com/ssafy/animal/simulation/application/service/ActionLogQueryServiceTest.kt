package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.StatEffect
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ActionLogQueryServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val service = ActionLogQueryService(store, store)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private val session = store.save(
        SimulationSession.start(
            userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
            initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
        ),
    )
    private val sessionSeq = session.sessionSeq!!

    init {
        store.save(SimulationActionLog.system(session, ActionCode.SESSION_START))
        store.save(SimulationActionLog.care(session, ActionCode.FEED, 22L, session.applyEffect(StatEffect.fromJson(mapOf("satiety" to 30)))))
        store.save(SimulationActionLog.care(session, ActionCode.WATER, null, session.applyEffect(StatEffect.fromJson(mapOf("hydration" to 50)))))
    }

    @Test
    fun `내 세션의 행동 이력을 최근 순으로 쓴 물품과 함께 돌려준다`() {
        val page = service.getActionLogs(17, sessionSeq, null, PageRequest.of(0, 50))

        assertEquals(listOf(ActionCode.WATER, ActionCode.FEED, ActionCode.SESSION_START), page.content.map { it.log.actionCode })
        assertNull(page.content[0].item)
        assertEquals(22L, page.content[1].item!!.itemSeq)
        assertEquals(mapOf("satiety" to 30), page.content[1].log.statDeltaJsonb)
        assertNull(page.content[2].log.statDeltaJsonb)
    }

    @Test
    fun `분류로 거른다`() {
        val page = service.getActionLogs(17, sessionSeq, ActionType.SYSTEM, PageRequest.of(0, 50))

        assertEquals(listOf(ActionCode.SESSION_START), page.content.map { it.log.actionCode })
    }

    @Test
    fun `남의 세션이나 없는 세션이면 404 SIMULATION_NOT_FOUND`() {
        listOf(18L to sessionSeq, 17L to 9999L).forEach { (userSeq, seq) ->
            val error = assertFailsWith<BusinessException> { service.getActionLogs(userSeq, seq, null, PageRequest.of(0, 50)) }
            assertEquals(SimulationErrorCode.SIMULATION_NOT_FOUND, error.errorCode)
        }
    }
}
