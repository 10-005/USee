package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.simulation.adapter.out.persistence.TransactionPersistenceAdapter
import com.ssafy.animal.simulation.application.port.`in`.TransactionQueryUseCase
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.QSimulationTransaction.Companion.simulationTransaction
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.TransactionType
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@WebMvcTest(TransactionController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class TransactionControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var transactionQueryUseCase: TransactionQueryUseCase

    private val food = SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL)
    private val at = OffsetDateTime.of(2026, 11, 1, 15, 10, 0, 0, ZoneOffset.ofHours(9))

    private fun saved(transaction: SimulationTransaction, seq: Long) = transaction.also {
        ReflectionTestUtils.setField(it, "transactionSeq", seq)
        ReflectionTestUtils.setField(it, "regDttm", at)
    }

    @Test
    fun `18_11은 지출 내역을 유형·페이지·정렬과 함께 넘기고 명세 필드대로 돌려준다`() {
        val pageable = PageRequest.of(0, 50, Sort.by(Sort.Order.desc("regDttm")))
        val purchase = saved(SimulationTransaction.purchase(3001L, food, 2, 21_200, 128_800), 70001L)
        val vet = saved(SimulationTransaction.event(3001L, 90001L, 30_000, 98_800, "축 처져 있어요"), 70002L)
        given(transactionQueryUseCase.getTransactions(17, 3001L, null, pageable))
            .willReturn(PageImpl(listOf(TransactionEntry(vet, null), TransactionEntry(purchase, food)), pageable, 2))

        mockMvc.get("/api/simulation/v1/simulations/3001/transactions?page=0&size=50&sort=regDttm,desc") {
            header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(2) }
            jsonPath("$.data.content[0].transactionSeq") { value(70002) }
            jsonPath("$.data.content[0].transactionType") { value("EVENT") }
            jsonPath("$.data.content[0].item") { value(null) }
            jsonPath("$.data.content[0].occurrenceSeq") { value(90001) }
            jsonPath("$.data.content[0].description") { value("동물병원 진료비 · 축 처져 있어요") }
            jsonPath("$.data.content[1].transactionType") { value("PURCHASE") }
            jsonPath("$.data.content[1].item.itemSeq") { value(22) }
            jsonPath("$.data.content[1].item.itemName") { value("균형 잡힌 사료") }
            jsonPath("$.data.content[1].item.category") { value("FOOD") }
            jsonPath("$.data.content[1].occurrenceSeq") { value(null) }
            jsonPath("$.data.content[1].quantity") { value(2) }
            jsonPath("$.data.content[1].amount") { value(21200) }
            jsonPath("$.data.content[1].balanceAfter") { value(128800) }
            jsonPath("$.data.content[1].regDttm") { value("2026-11-01T15:10:00+09:00") }
        }
    }

    @Test
    fun `18_11은 유형 필터를 넘기고, 없는 유형 값이면 400`() {
        val pageable = PageRequest.of(0, 50)
        given(transactionQueryUseCase.getTransactions(17, 3001L, TransactionType.PURCHASE, pageable)).willReturn(PageImpl(emptyList(), pageable, 0))

        mockMvc.get("/api/simulation/v1/simulations/3001/transactions?transactionType=PURCHASE") {
            header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER)
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(0) }
        }
        mockMvc.get("/api/simulation/v1/simulations/3001/transactions?transactionType=VET") {
            header(HttpHeaders.AUTHORIZATION, WebTestTokens.USER)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `18_11은 보호소도 역할로 막지 않고 서비스의 404로 존재를 감춘다`() {
        val pageable = PageRequest.of(0, 50)
        given(transactionQueryUseCase.getTransactions(77, 3001L, null, pageable)).willThrow(BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND))

        mockMvc.get("/api/simulation/v1/simulations/3001/transactions") { header(HttpHeaders.AUTHORIZATION, WebTestTokens.SHELTER) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SIMULATION_NOT_FOUND") }
        }
    }

    @Test
    fun `18_11 정렬은 regDttm만 허용하고 기본은 regDttm desc, 동률은 PK desc`() {
        val policy = TransactionPersistenceAdapter.SORT_POLICY

        assertEquals(
            listOf(simulationTransaction.regDttm.desc(), simulationTransaction.transactionSeq.desc()),
            policy.toOrderSpecifiers(Sort.unsorted()).toList(),
        )
        val error = assertFailsWith<BusinessException> { policy.toOrderSpecifiers(Sort.by("amount")) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
        then(transactionQueryUseCase).shouldHaveNoInteractions()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) = WebTestTokens.register(registry)
    }
}
