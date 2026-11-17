package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.InventoryItemView
import com.ssafy.animal.simulation.application.port.`in`.InventoryQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.InventoryView
import com.ssafy.animal.simulation.application.port.out.InventoryEntry
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stats
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(InventoryController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class InventoryControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var inventoryQueryUseCase: InventoryQueryUseCase

    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val session = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
        initialStats = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60),
    )

    private fun entry(item: SimulationShopItem, quantity: Int) =
        InventoryEntry(SimulationInventoryItem(3001L, item.itemSeq).apply { if (quantity > 0) add(quantity) }, item)

    @Test
    fun `18_10은 잔액·기구·보유 물품을 명세 필드대로 돌려준다`() {
        val bowl = SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL)
        val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, imageKey = "simulation/items/food.png")
        given(inventoryQueryUseCase.getInventory(17, 3001L)).willReturn(
            InventoryView(
                session = session,
                equipment = Equipment(foodBowl = true, waterBowl = false, litterBox = false, toy = false),
                items = listOf(
                    InventoryItemView(entry(bowl, 1), null),
                    InventoryItemView(entry(food, 0), "https://cdn/simulation/items/food.png"),
                ),
            ),
        )

        mockMvc.get("/api/simulation/v1/simulations/3001/inventory") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }.andExpect {
            status { isOk() }
            jsonPath("$.data.balance") { value(150000) }
            jsonPath("$.data.expectedCost") { value(150000) }
            jsonPath("$.data.equipment.foodBowl") { value(true) }
            jsonPath("$.data.equipment.toy") { value(false) }
            jsonPath("$.data.items.length()") { value(2) }
            jsonPath("$.data.items[0].itemSeq") { value(1) }
            jsonPath("$.data.items[0].category") { value("FOOD_BOWL") }
            jsonPath("$.data.items[0].isConsumable") { value(false) }
            jsonPath("$.data.items[0].quantity") { value(1) }
            jsonPath("$.data.items[0].imageUrl") { value(null) }
            jsonPath("$.data.items[1].itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data.items[1].isConsumable") { value(true) }
            jsonPath("$.data.items[1].quantity") { value(0) }
            jsonPath("$.data.items[1].imageUrl") { value("https://cdn/simulation/items/food.png") }
        }
    }

    @Test
    fun `18_10은 보호소도 역할로 막지 않고 서비스의 404로 존재를 감춘다`() {
        given(inventoryQueryUseCase.getInventory(77, 3001L)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001/inventory") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
