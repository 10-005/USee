package com.ssafy.animal.core.catalog.application.port.`in`

import com.ssafy.animal.core.catalog.domain.Personality
import com.ssafy.animal.core.catalog.domain.SpeciesCode

/**
 * 5.1~5.5 기준정보 목록. 시드 전량을 페이지 없이 돌려준다(5장 조회 규약).
 * 다른 도메인이 쓰는 CatalogUseCase와 나눠 둔다: 그쪽 구현체·가짜를 깨지 않으려고
 */
interface CatalogQueryUseCase {
    fun findSpecies(): List<SpeciesView>

    /** 없는 종이면 빈 목록 */
    fun findBreeds(speciesSeq: Int): List<BreedView>
    fun findColors(): List<ColorView>

    /** 필터는 각각 선택 */
    fun findCombinations(breedSeq: Int?, colorSeq: Int?): List<AnimalClassification>

    /** sidoName이 null이면 전국 */
    fun findDistricts(sidoName: String?): List<DistrictView>
}

data class SpeciesView(val speciesSeq: Int, val speciesCode: SpeciesCode, val speciesName: String)

data class BreedView(
    val breedSeq: Int,
    val speciesSeq: Int,
    val breedName: String,
    val personality: Personality,
    val representativeImageUrl: String?,
)

data class ColorView(val colorSeq: Int, val colorName: String)

data class DistrictView(val districtCode: String, val sidoName: String, val sigunguName: String)
