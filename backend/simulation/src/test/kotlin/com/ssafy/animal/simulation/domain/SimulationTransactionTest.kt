package com.ssafy.animal.simulation.domain

import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SimulationTransactionTest {

    private val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private fun session(expectedCost: Int) = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = expectedCost, startedAt = start, endsAt = start.plusDays(7),
        initialStats = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60),
    )

    @Test
    fun `구매 기록은 PURCHASE 유형에 물품·수량·금액·차감 후 잔액과 표시 문구를 남긴다`() {
        val transaction = SimulationTransaction.purchase(3001L, food, quantity = 2, amount = 21_200, balanceAfter = 128_800)

        assertEquals(TransactionType.PURCHASE, transaction.transactionType)
        assertEquals(22L, transaction.itemSeq)
        assertNull(transaction.occurrenceSeq)
        assertEquals(2, transaction.quantity)
        assertEquals(21_200, transaction.amount)
        assertEquals(128_800, transaction.balanceAfter)
        assertEquals("균형 잡힌 사료 × 2", transaction.description)
    }

    @Test
    fun `진료비 기록은 EVENT 유형에 이벤트 번호·금액·잔액을 남기고 물품은 없다`() {
        val transaction = SimulationTransaction.event(3001L, occurrenceSeq = 90001L, amount = 30_000, balanceAfter = 80_000, scenarioTitle = "축 처져 있어요")

        assertEquals(TransactionType.EVENT, transaction.transactionType)
        assertEquals(90001L, transaction.occurrenceSeq)
        assertNull(transaction.itemSeq)
        assertEquals(1, transaction.quantity)
        assertEquals(30_000, transaction.amount)
        assertEquals(80_000, transaction.balanceAfter)
        assertEquals("동물병원 진료비 · 축 처져 있어요", transaction.description)
    }

    @Test
    fun `진료비 표시 문구는 200자로 자른다`() {
        val transaction = SimulationTransaction.event(3001L, 90001L, 30_000, 80_000, "가".repeat(300))

        assertEquals(200, transaction.description!!.length)
    }

    @Test
    fun `잔액은 음수까지 내려갈 수 있다`() {
        val session = session(expectedCost = 10_000)

        assertEquals(-600, session.spend(10_600))
        assertEquals(-600, session.balance)
        assertFailsWith<IllegalArgumentException> { session.spend(-1) }
    }

    @Test
    fun `종료된 세션은 돈을 쓰지 않는다`() {
        val session = session(expectedCost = 10_000)
        ReflectionTestUtils.setField(session, "status", SessionStatus.ABANDONED)

        assertFailsWith<IllegalStateException> { session.spend(100) }
    }
}
