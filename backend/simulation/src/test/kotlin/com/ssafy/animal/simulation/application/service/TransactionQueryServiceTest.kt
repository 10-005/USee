package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.PageRequest
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TransactionQueryServiceTest {

    private val store = FakeSimulationStore().apply { stockShop() }
    private val service = TransactionQueryService(store, store)
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
        store.save(SimulationTransaction.purchase(sessionSeq, store.shopItems.getValue(22), 1, 10_600, 139_400))
        store.save(SimulationTransaction.event(sessionSeq, 90001L, 30_000, 109_400, "축 처져 있어요"))
        store.save(SimulationTransaction.purchase(sessionSeq, store.shopItems.getValue(1), 1, 10_000, 99_400))
        // 다른 세션의 지출은 섞이지 않는다
        store.save(SimulationTransaction.purchase(9999L, store.shopItems.getValue(22), 1, 10_600, 0))
    }

    @Test
    fun `내 세션의 지출을 최근 순으로 물품과 함께 돌려준다(진료비는 물품 없음)`() {
        val page = service.getTransactions(17, sessionSeq, null, PageRequest.of(0, 50))

        assertEquals(3, page.totalElements)
        assertEquals(listOf(1L, null, 22L), page.content.map { it.item?.itemSeq })
        assertEquals(listOf(TransactionType.PURCHASE, TransactionType.EVENT, TransactionType.PURCHASE), page.content.map { it.transaction.transactionType })
        assertNull(page.content[1].item)
        assertEquals(90001L, page.content[1].transaction.occurrenceSeq)
    }

    @Test
    fun `유형으로 거른다`() {
        val page = service.getTransactions(17, sessionSeq, TransactionType.EVENT, PageRequest.of(0, 50))

        assertEquals(listOf(30_000), page.content.map { it.transaction.amount })
    }

    @Test
    fun `남의 세션이나 없는 세션이면 404 SIMULATION_NOT_FOUND`() {
        listOf(18L to sessionSeq, 17L to 9999L).forEach { (userSeq, seq) ->
            val error = assertFailsWith<BusinessException> { service.getTransactions(userSeq, seq, null, PageRequest.of(0, 50)) }
            assertEquals(SimulationErrorCode.SIMULATION_NOT_FOUND, error.errorCode)
        }
    }
}
