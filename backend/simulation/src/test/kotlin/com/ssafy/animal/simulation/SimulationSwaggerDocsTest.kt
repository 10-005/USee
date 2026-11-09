package com.ssafy.animal.simulation

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.simulation.adapter.`in`.web.WebTestTokens
import com.ssafy.animal.simulation.application.port.`in`.AbandonSimulationUseCase
import com.ssafy.animal.simulation.application.port.`in`.ActionLogQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.CareActionUseCase
import com.ssafy.animal.simulation.application.port.`in`.InventoryQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
import com.ssafy.animal.simulation.application.port.`in`.ShopQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.SimulationReportQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import com.ssafy.animal.simulation.application.port.`in`.TransactionQueryUseCase
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.junit.jupiter.api.Test
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.configuration.SpringDocPageableConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * simulation 전체 api-docs를 만들어 문서 누락과 예시 오류를 잡는다.
 * - 모든 API에 summary가 있고 401 응답이 문서화됐는지
 * - 모든 응답 예시가 JSON이고, SIMULATION_ 오류 예시의 status·message가 SimulationErrorCode와 같은지
 */
@WebMvcTest
@ActiveProfiles("local")
@ImportAutoConfiguration(
    CommonWebAutoConfiguration::class,
    SpringDocConfiguration::class,
    SpringDocPageableConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
)
class SimulationSwaggerDocsTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    // 문서에 실제 컨트롤러가 잡히도록 컨트롤러는 띄우고 유스케이스만 목으로 둔다
    @MockitoBean lateinit var startSimulationUseCase: StartSimulationUseCase
    @MockitoBean lateinit var sessionQueryUseCase: SimulationSessionQueryUseCase
    @MockitoBean lateinit var abandonUseCase: AbandonSimulationUseCase
    @MockitoBean lateinit var shopQueryUseCase: ShopQueryUseCase
    @MockitoBean lateinit var purchaseUseCase: PurchaseUseCase
    @MockitoBean lateinit var inventoryQueryUseCase: InventoryQueryUseCase
    @MockitoBean lateinit var transactionQueryUseCase: TransactionQueryUseCase
    @MockitoBean lateinit var careActionUseCase: CareActionUseCase
    @MockitoBean lateinit var actionLogQueryUseCase: ActionLogQueryUseCase
    @MockitoBean lateinit var reportQueryUseCase: SimulationReportQueryUseCase

    private val mapper = ObjectMapper()

    private fun apiDocs(): JsonNode {
        val body = mockMvc.get("/api/simulation/v3/api-docs").andExpect { status { isOk() } }.andReturn().response.contentAsString
        return mapper.readTree(body)
    }

    /** (경로 메서드, 오퍼레이션) 목록 */
    private fun operations(docs: JsonNode): List<Pair<String, JsonNode>> =
        docs["paths"].properties().flatMap { (path, methods) -> methods.properties().map { (method, op) -> "$method $path" to op } }

    @Test
    fun `모든 API에 요약과 태그와 401 응답 문서가 있다`() {
        val operations = operations(apiDocs())

        // 18.1~18.11, 18.18
        assertTrue(operations.size >= 12, "simulation API 문서 수: ${operations.size}")
        operations.forEach { (name, op) ->
            assertTrue(op["summary"]?.asText().orEmpty().isNotBlank(), "$name: summary 없음")
            assertTrue(op["tags"]?.size() ?: 0 > 0, "$name: 태그 없음")
            assertTrue(op["responses"].has("401"), "$name: 401 응답 문서 없음")
        }
    }

    @Test
    fun `응답 예시는 모두 JSON이고 SIMULATION_ 오류 예시는 실제 오류 코드와 같다`() {
        val codes = SimulationErrorCode.entries.associateBy { it.name }
        var checked = 0
        operations(apiDocs()).forEach { (name, op) ->
            op["responses"].properties().forEach { (status, response) ->
                response["content"]?.get("application/json")?.get("examples")?.properties()?.forEach { (exampleName, example) ->
                    val value = example["value"]
                    val json = if (value.isTextual) runCatching { mapper.readTree(value.asText()) }.getOrElse { fail("$name $status $exampleName: JSON 아님") } else value
                    assertEquals(status.toInt(), json["status"].asInt(), "$name $exampleName: status가 응답 코드와 다름")
                    val code = json["code"].asText()
                    codes[code]?.let { errorCode ->
                        assertEquals(errorCode.status.value(), json["status"].asInt(), "$name $exampleName: $code 상태 코드")
                        assertEquals(errorCode.message, json["message"].asText(), "$name $exampleName: $code 메시지")
                    }
                    checked++
                }
            }
        }
        assertTrue(checked > 0)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
