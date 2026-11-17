package com.ssafy.animal.core.catalog.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.BreedView
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogQueryUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.ColorView
import com.ssafy.animal.core.catalog.application.port.`in`.DistrictView
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.SpeciesView
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.Personality
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date

@WebMvcTest(CatalogController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class CatalogControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var catalogQueryUseCase: CatalogQueryUseCase

    @MockitoBean
    lateinit var geocodeUseCase: GeocodeUseCase

    private fun get(path: String, auth: Boolean = true) = mockMvc.get(path) { if (auth) header(HttpHeaders.AUTHORIZATION, BEARER) }

    @Test
    fun `5_1 종 목록`() {
        given(catalogQueryUseCase.findSpecies()).willReturn(listOf(SpeciesView(1, SpeciesCode.DOG, "개")))

        get("/api/core/v1/animal-species").andExpect {
            status { isOk() }
            jsonPath("$.data[0].speciesSeq") { value(1) }
            jsonPath("$.data[0].speciesCode") { value("DOG") }
            jsonPath("$.data[0].speciesName") { value("개") }
        }
    }

    @Test
    fun `5_1 토큰 없으면 401`() {
        get("/api/core/v1/animal-species", auth = false).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `5_2 종의 품종 목록, 대표 이미지 없으면 null`() {
        given(catalogQueryUseCase.findBreeds(1)).willReturn(listOf(BreedView(21, 1, "말티즈", Personality.FRIENDLY, null)))

        get("/api/core/v1/animal-species/1/breeds").andExpect {
            status { isOk() }
            jsonPath("$.data[0].breedSeq") { value(21) }
            jsonPath("$.data[0].personality") { value("FRIENDLY") }
            jsonPath("$.data[0].representativeImageUrl") { value(null) }
        }
    }

    @Test
    fun `5_2 speciesSeq가 숫자가 아니면 400`() {
        get("/api/core/v1/animal-species/dog/breeds").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `5_3 색상 목록`() {
        given(catalogQueryUseCase.findColors()).willReturn(listOf(ColorView(3, "흰색")))

        get("/api/core/v1/animal-colors").andExpect {
            status { isOk() }
            jsonPath("$.data[0].colorSeq") { value(3) }
            jsonPath("$.data[0].colorName") { value("흰색") }
        }
    }

    @Test
    fun `5_4 조합 목록은 필터를 넘기고, 필터 없이도 된다`() {
        given(catalogQueryUseCase.findCombinations(21, 3))
            .willReturn(listOf(AnimalClassification(88, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색")))
        given(catalogQueryUseCase.findCombinations(null, null)).willReturn(emptyList())

        get("/api/core/v1/animal-combinations?breedSeq=21&colorSeq=3").andExpect {
            status { isOk() }
            jsonPath("$.data[0].combinationSeq") { value(88) }
            jsonPath("$.data[0].speciesSeq") { value(1) }
            jsonPath("$.data[0].breedSeq") { value(21) }
            jsonPath("$.data[0].colorSeq") { value(3) }
        }
        get("/api/core/v1/animal-combinations").andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }

    @Test
    fun `5_4 필터가 숫자가 아니면 400`() {
        get("/api/core/v1/animal-combinations?breedSeq=abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `5_5 시도 필터로 시군구 목록`() {
        given(catalogQueryUseCase.findDistricts("대구광역시")).willReturn(listOf(DistrictView("27110", "대구광역시", "중구")))

        get("/api/core/v1/districts?sidoName=대구광역시").andExpect {
            status { isOk() }
            jsonPath("$.data[0].districtCode") { value("27110") }
            jsonPath("$.data[0].sigunguName") { value("중구") }
        }
    }

    @Test
    fun `5_6 주소 검색은 앞뒤 공백을 지우고 후보를 돌려준다`() {
        given(geocodeUseCase.search("대구광역시 중구 동성로 1"))
            .willReturn(listOf(GeocodeCandidate("대구광역시 중구 동성로 1", "대구광역시 중구 동성로2가 1", 35.8714, 128.6014)))

        get("/api/core/v1/geocode?query= 대구광역시 중구 동성로 1 ").andExpect {
            status { isOk() }
            jsonPath("$.data[0].roadAddress") { value("대구광역시 중구 동성로 1") }
            jsonPath("$.data[0].latitude") { value(35.8714) }
            jsonPath("$.data[0].longitude") { value(128.6014) }
        }
    }

    @Test
    fun `5_6 query 누락·공백·100자 초과는 400`() {
        listOf(null, "  ", "가".repeat(101)).forEach { query ->
            mockMvc.get("/api/core/v1/geocode") {
                header(HttpHeaders.AUTHORIZATION, BEARER)
                query?.let { param("query", it) }
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            }
        }
        then(geocodeUseCase).should(never()).search(anyString())
    }

    @Test
    fun `5_6 네이버 장애는 503 CATALOG_GEOCODE_UNAVAILABLE`() {
        given(geocodeUseCase.search("구미시")).willThrow(BusinessException(CatalogErrorCode.CATALOG_GEOCODE_UNAVAILABLE))

        get("/api/core/v1/geocode?query=구미시").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("CATALOG_GEOCODE_UNAVAILABLE") }
        }
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val BEARER = "Bearer " + Jwts.builder().claim("userSeq", 42).claim("role", "USER").claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
