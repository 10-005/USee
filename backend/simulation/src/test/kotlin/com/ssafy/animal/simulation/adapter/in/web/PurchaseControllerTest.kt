package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseResult
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
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

    private fun post(token: String, body: String) = mockMvc.post("/api/simulation/v1/simulations/3001/purchases") {
        header(HttpHeaders.AUTHORIZATION, token)
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `18_9는 201과 지출·보유 결과를 명세 필드대로 돌려준다`() {
        val transaction = SimulationTransaction.purchase(3001L, food, quantity = 2, amount = 21_200, balanceAfter = 128_800)
            .also { ReflectionTestUtils.setField(it, "transactionSeq", 70001L) }
        given(purchaseUseCase.purchase(17, 3001L, PurchaseCommand(22, 2))).willReturn(PurchaseResult(transaction, food, inventoryQuantity = 24))

        post(WebTestTokens.USER, """{"itemSeq":22,"quantity":2}""").andExpect {
            status { isCreated() }
            jsonPath("$.data.transactionSeq") { value(70001) }
            jsonPath("$.data.item.itemSeq") { value(22) }
            jsonPath("$.data.item.itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data.item.category") { value("FOOD") }
            jsonPath("$.data.quantity") { value(2) }
            jsonPath("$.data.amount") { value(21200) }
            jsonPath("$.data.balanceAfter") { value(128800) }
            jsonPath("$.data.inventoryQuantity") { value(24) }
        }
    }

    @Test
    fun `수량 누락·1 미만·상한 초과, 물품 누락은 400 COMMON_INVALID_INPUT`() {
        listOf(
            """{"itemSeq":22}""" to "quantity",
            """{"itemSeq":22,"quantity":0}""" to "quantity",
            """{"itemSeq":22,"quantity":100}""" to "quantity",
            """{"quantity":1}""" to "itemSeq",
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
        given(purchaseUseCase.purchase(77, 3001L, PurchaseCommand(22, 1))).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))
        given(purchaseUseCase.purchase(17, 3001L, PurchaseCommand(1, 1)))
            .willThrow(BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_ALREADY_OWNED))

        post(WebTestTokens.SHELTER, """{"itemSeq":22,"quantity":1}""").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
        post(WebTestTokens.USER, """{"itemSeq":1,"quantity":1}""").andExpect {
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
