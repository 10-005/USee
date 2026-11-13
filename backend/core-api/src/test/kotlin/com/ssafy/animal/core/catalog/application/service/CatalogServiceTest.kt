package com.ssafy.animal.core.catalog.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.out.CatalogRepository
import com.ssafy.animal.core.catalog.application.port.out.GeocodingClient
import com.ssafy.animal.core.catalog.domain.AnimalBreed
import com.ssafy.animal.core.catalog.domain.AnimalColor
import com.ssafy.animal.core.catalog.domain.AnimalSpecies
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.District
import com.ssafy.animal.core.catalog.domain.Personality
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CatalogServiceTest {

    private val maltese = AnimalClassification(88, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색")
    private val districts = setOf("47190")

    private val repository = object : CatalogRepository {
        override fun findClassifications(combinationSeqs: Collection<Int>) = listOf(maltese).filter { it.combinationSeq in combinationSeqs }
        override fun existsDistrict(districtCode: String) = districtCode in districts
        override fun findAllSpecies() = emptyList<AnimalSpecies>()
        override fun findBreeds(speciesSeq: Int) = breeds.filter { it.speciesSeq == speciesSeq }
        override fun findAllColors() = emptyList<AnimalColor>()
        override fun findCombinations(breedSeq: Int?, colorSeq: Int?) = emptyList<AnimalClassification>()
        override fun findDistricts(sidoName: String?) = emptyList<District>()
    }

    private val breeds = listOf(
        AnimalBreed(21, 1, "말티즈", Personality.FRIENDLY).also { ReflectionTestUtils.setField(it, "representativeImageSeq", 7L) },
        AnimalBreed(22, 1, "진돗개", Personality.ACTIVE),
    )
    private val imageUseCase = mock(ImageUseCase::class.java)

    private var legalCode: () -> String? = { null }
    private val client = object : GeocodingClient {
        override fun search(query: String): List<GeocodeCandidate> = error("네이버 장애")
        override fun reverseLegalCode(latitude: Double, longitude: Double) = legalCode()
    }

    private val catalog = CatalogService(repository, imageUseCase)
    private val geocode = GeocodeService(client, repository)

    @Test
    fun `없는 조합은 404, 있는 조합은 분류를 돌려준다`() {
        assertEquals("말티즈", catalog.requireClassification(88).breedName)
        assertCode(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND) { catalog.requireClassification(99) }
        assertEquals(setOf(88), catalog.findClassifications(listOf(88, 99)).keys)
    }

    @Test
    fun `5_2 품종 대표 이미지는 이미지 URL로, 없으면 null`() {
        given(imageUseCase.findSummaries(listOf(7L))).willReturn(mapOf(7L to ImageSummary(7, "https://cdn/images/seed/breeds/000072.jpg")))

        val result = catalog.findBreeds(1)

        assertEquals(listOf("https://cdn/images/seed/breeds/000072.jpg", null), result.map { it.representativeImageUrl })
        assertEquals(Personality.FRIENDLY, result[0].personality)
        assertEquals(emptyList(), catalog.findBreeds(99))
    }

    @Test
    fun `마스터에 없는 행정구역 필터는 400`() {
        catalog.requireDistrict("47190")
        assertCode(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE) { catalog.requireDistrict("99999") }
    }

    @Test
    fun `역지오코딩은 법정동 코드 앞 5자리를 마스터에 있을 때만 돌려준다`() {
        legalCode = { "4719025000" }
        assertEquals("47190", geocode.resolveDistrictCode(36.1, 128.3))
        legalCode = { "1111010100" }
        assertNull(geocode.resolveDistrictCode(37.5, 127.0))
        legalCode = { error("timeout") }
        assertNull(geocode.resolveDistrictCode(36.1, 128.3))
    }

    @Test
    fun `주소 검색 외부 실패는 503`() {
        assertCode(CatalogErrorCode.CATALOG_GEOCODE_UNAVAILABLE) { geocode.search("구미시") }
    }

    private fun assertCode(code: CatalogErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)
}
