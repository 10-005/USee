package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.service.FakeSimulationStore.Companion.MALTESE
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimulationSessionDetailAssemblerTest {

    private val store = FakeSimulationStore()
    private val assembler = store.assembler()
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    private val session = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
        initialStats = StatCalculator(StatCalculatorTest.RULES, Duration.ofHours(1)).initialStats(),
    ).also { ReflectionTestUtils.setField(it, "sessionSeq", 3001L) }

    @Test
    fun `품종·보유 기구·진행 중 산책을 붙인다`() {
        store.breeds[21] = MALTESE
        store.stockShop()
        listOf(1L to 1, 11L to 1, 22L to 12).forEach { (itemSeq, quantity) ->
            store.save(SimulationInventoryItem(3001L, itemSeq).apply { add(quantity) })
        }
        store.activeWalks[3001L] = 8001L

        val detail = assembler.assemble(session)

        assertEquals(MALTESE, detail.breed)
        assertEquals(Equipment(foodBowl = true, waterBowl = false, litterBox = false, toy = true), detail.equipment)
        assertEquals(8001L, detail.activeWalkSeq)
    }

    @Test
    fun `품종 기준정보가 없으면 데이터 오류로 실패한다`() {
        assertFailsWith<IllegalStateException> { assembler.assemble(session) }
    }

    @Test
    fun `기구 판정은 기구 4종만 본다`() {
        assertEquals(Equipment.NONE, Equipment.of(setOf(ItemCategory.FOOD, ItemCategory.SNACK, ItemCategory.LITTER)))
    }
}
