package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseLine
import com.ssafy.animal.simulation.application.port.`in`.PurchaseResult
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
import com.ssafy.animal.simulation.application.port.`in`.PurchasedLine
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesScope
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

@WebMvcTest(PurchaseController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class PurchaseControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var purchaseUseCase: PurchaseUseCase

    private val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL)
    private val bowl = SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL)

    private fun post(token: String, body: String) = mockMvc.post("/api/simulation/v1/simulations/3001/purchases") {
        header(HttpHeaders.AUTHORIZATION, token)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun cart(vararg lines: Pair<Long, Int>) = PurchaseCommand(lines.map { (itemSeq, quantity) -> PurchaseLine(itemSeq, quantity) })

    private fun transaction(seq: Long, item: SimulationShopItem, quantity: Int, amount: Int, balanceAfter: Int) =
        SimulationTransaction.purchase(3001L, item, quantity, amount, balanceAfter).also { ReflectionTestUtils.setField(it, "transactionSeq", seq) }

    @Test
    fun `18_9는 201과 물품별 지출·보유 결과, 총액, 최종 잔액을 명세 필드대로 돌려준다`() {
        given(purchaseUseCase.purchase(17, 3001L, cart(22L to 2, 1L to 1))).willReturn(
            PurchaseResult(
                lines = listOf(
                    PurchasedLine(transaction(70001L, food, 2, 21_200, 128_800), food, inventoryQuantity = 24),
                    PurchasedLine(transaction(70002L, bowl, 1, 10_000, 118_800), bowl, inventoryQuantity = 1),
                ),
                totalAmount = 31_200,
                balanceAfter = 118_800,
            ),
        )

        post(WebTestTokens.USER, """{"items":[{"itemSeq":22,"quantity":2},{"itemSeq":1,"quantity":1}]}""").andExpect {
            status { isCreated() }
            jsonPath("$.data.purchases.length()") { value(2) }
            jsonPath("$.data.purchases[0].transactionSeq") { value(70001) }
            jsonPath("$.data.purchases[0].item.itemSeq") { value(22) }
            jsonPath("$.data.purchases[0].item.itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data.purchases[0].item.category") { value("FOOD") }
            jsonPath("$.data.purchases[0].quantity") { value(2) }
            jsonPath("$.data.purchases[0].amount") { value(21200) }
            jsonPath("$.data.purchases[0].balanceAfter") { value(128800) }
            jsonPath("$.data.purchases[0].inventoryQuantity") { value(24) }
            jsonPath("$.data.purchases[1].item.category") { value("FOOD_BOWL") }
            jsonPath("$.data.purchases[1].balanceAfter") { value(118800) }
            jsonPath("$.data.totalAmount") { value(31200) }
            jsonPath("$.data.balanceAfter") { value(118800) }
        }
    }

    @Test
    fun `장바구니 누락·빈 배열·50줄 초과, 줄의 수량 누락·범위 밖, 물품 누락, 빈 줄은 400 COMMON_INVALID_INPUT`() {
        val tooMany = (1..51).joinToString(",", "[", "]") { """{"itemSeq":$it,"quantity":1}""" }
        listOf(
            """{}""" to "items",
            """{"items":[]}""" to "items",
            """{"items":$tooMany}""" to "items",
            """{"items":[{"itemSeq":22}]}""" to "items[0].quantity",
            """{"items":[{"itemSeq":22,"quantity":1},{"itemSeq":23,"quantity":0}]}""" to "items[1].quantity",
            """{"items":[{"itemSeq":22,"quantity":100}]}""" to "items[0].quantity",
            """{"items":[{"quantity":1}]}""" to "items[0].itemSeq",
            """{"items":[{"itemSeq":22,"quantity":1},null]}""" to "items[1]",
        ).forEach { (body, field) ->
            post(WebTestTokens.USER, body).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value(field) }
            }
        }
        then(purchaseUseCase).shouldHaveNoInteractions()
    }

    @Test
    fun `서비스의 도메인 오류를 그대로 응답한다(보호소는 404로 감춰짐)`() {
        given(purchaseUseCase.purchase(77, 3001L, cart(22L to 1))).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))
        given(purchaseUseCase.purchase(17, 3001L, cart(22L to 1, 1L to 1)))
            .willThrow(BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED))

        post(WebTestTokens.SHELTER, """{"items":[{"itemSeq":22,"quantity":1}]}""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
        post(WebTestTokens.USER, """{"items":[{"itemSeq":22,"quantity":1},{"itemSeq":1,"quantity":1}]}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SIMULATION_EQUIPMENT_ALREADY_OWNED") }
        }
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
