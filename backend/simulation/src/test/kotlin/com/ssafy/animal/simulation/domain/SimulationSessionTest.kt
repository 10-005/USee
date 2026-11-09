package com.ssafy.animal.simulation.domain

import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SimulationSessionTest {

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val stats = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60)

    private fun start(animalName: String = "초코", expectedCost: Int = 150_000) = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = animalName, speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = expectedCost, startedAt = start, endsAt = start.plusDays(7), initialStats = stats,
    )

    @Test
    fun `시작하면 진행 중이고 잔액은 예상 비용, 스탯 계산 시각은 시작 시각이다`() {
        val session = start()

        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(150_000, session.balance)
        assertEquals(start, session.startedDttm)
        assertEquals(start.plusDays(7), session.endsDttm)
        assertEquals(start, session.statsCalculatedDttm)
        assertEquals(stats, session.stats())
        assertNull(session.endedDttm)
        assertNull(session.lastActionDttm)
    }

    @Test
    fun `이름은 1~6자, 예상 비용은 0 이상이어야 한다`() {
        start(animalName = "여섯글자이름")
        start(expectedCost = 0)
        assertFailsWith<IllegalArgumentException> { start(animalName = "") }
        assertFailsWith<IllegalArgumentException> { start(animalName = "일곱글자이름임") }
        assertFailsWith<IllegalArgumentException> { start(expectedCost = -1) }
    }

    @Test
    fun `효과를 적용하면 스탯이 바뀌고 지연 계산 기준 시각은 그대로다`() {
        val session = start()

        val result = session.applyEffect(StatEffect.fromJson(mapOf("satiety" to 50, "calmness" to -5)))

        assertEquals(100, session.stats().satiety)
        assertEquals(45, session.stats().calmness)
        assertEquals(mapOf(Stat.SATIETY to 30, Stat.CALMNESS to -5), result.delta)
        assertEquals(start, session.statsCalculatedDttm)
    }

    @Test
    fun `종료된 세션에는 효과를 적용하지 않는다`() {
        val session = start()
        ReflectionTestUtils.setField(session, "status", SessionStatus.FAILED)

        assertFailsWith<IllegalStateException> { session.applyEffect(StatEffect.fromJson(mapOf("health" to 20))) }
    }

    @Test
    fun `SYSTEM 로그는 분류를 코드에서 정하고 그 시점 스탯 7종을 남긴다`() {
        val session = start().also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }

        val log = SimulationActionLog.system(session, ActionCode.SESSION_START)

        assertEquals(3001L, log.sessionSeq)
        assertEquals(ActionType.SYSTEM, log.actionType)
        assertEquals(ActionCode.SESSION_START, log.actionCode)
        assertEquals(
            mapOf("satiety" to 70, "hydration" to 70, "cleanliness" to 80, "bond" to 30, "calmness" to 50, "health" to 90, "energy" to 60),
            log.statsAfterJsonb,
        )
        assertNull(log.statDeltaJsonb)
        assertFailsWith<IllegalArgumentException> { SimulationActionLog.system(session, ActionCode.FEED) }
    }
}
