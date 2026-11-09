package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.simulation.application.port.`in`.ShopItemView
import com.ssafy.animal.simulation.application.port.`in`.ShopQueryUseCase
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ShopController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ShopControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var shopQueryUseCase: ShopQueryUseCase

    private val bowl = ShopItemView(
        SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL, description = "사료를 담는 그릇입니다.", displayOrder = 1),
        imageUrl = null,
        usesPerPurchase = 1,
    )
    private val food = ShopItemView(
        SimulationShopItem(
            22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL,
            effects = mapOf("satiety" to 30, "bond" to 1), imageKey = "simulation/items/food.png", displayOrder = 22,
        ),
        imageUrl = "https://cdn/simulation/items/food.png",
        usesPerPurchase = 12,
    )

    @Test
    fun `18_8은 물품 목록을 페이지 없이 배열로 돌려준다`() {
        given(shopQueryUseCase.getItems(SpeciesType.DOG)).willReturn(listOf(bowl, food))

        mockMvc.get("/api/simulation/v1/simulations/shop/items?speciesType=DOG") {
            header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(2) }
            jsonPath("$.data[0].itemSeq") { value(1) }
            jsonPath("$.data[0].category") { value("FOOD_BOWL") }
            jsonPath("$.data[0].isConsumable") { value(false) }
            jsonPath("$.data[0].effects") { value(null) }
            jsonPath("$.data[0].imageUrl") { value(null) }
            jsonPath("$.data[0].usesPerPurchase") { value(1) }
            jsonPath("$.data[1].itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data[1].isConsumable") { value(true) }
            jsonPath("$.data[1].price") { value(10600) }
            jsonPath("$.data[1].speciesScope") { value("ALL") }
            jsonPath("$.data[1].effects.satiety") { value(30) }
            jsonPath("$.data[1].effects.bond") { value(1) }
            jsonPath("$.data[1].imageUrl") { value("https://cdn/simulation/items/food.png") }
            jsonPath("$.data[1].usesPerPurchase") { value(12) }
        }
    }

    @Test
    fun `18_8은 종을 생략하면 전체를 요청하고 보호소도 조회할 수 있다`() {
        given(shopQueryUseCase.getItems(null)).willReturn(listOf(bowl))

        mockMvc.get("/api/simulation/v1/simulations/shop/items") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].itemSeq") { value(1) }
        }
    }

    @Test
    fun `18_8은 모의 입양 종이 아니면 400, 토큰이 없으면 401`() {
        mockMvc.get("/api/simulation/v1/simulations/shop/items?speciesType=ETC") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER) }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            }
        mockMvc.get("/api/simulation/v1/simulations/shop/items").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
        then(shopQueryUseCase).shouldHaveNoInteractions()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
