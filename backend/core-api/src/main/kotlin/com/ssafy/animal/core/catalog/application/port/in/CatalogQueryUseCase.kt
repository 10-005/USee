package com.ssafy.animal.core.catalog.application.port.`in`

import com.ssafy.animal.core.catalog.domain.Personality
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.swagger.v3.oas.annotations.media.Schema

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

data class SpeciesView(
    @field:Schema(description = "종 번호", example = "1")
    val speciesSeq: Int,
    @field:Schema(description = "종 코드. DOG=개, CAT=고양이, ETC=기타. 모의 입양은 DOG·CAT만", example = "DOG")
    val speciesCode: SpeciesCode,
    @field:Schema(description = "종 이름", example = "개")
    val speciesName: String,
)

data class BreedView(
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "종 번호", example = "1")
    val speciesSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(
        description = "모의 입양 성격. ACTIVE=활발, TIMID=소심, AGGRESSIVE=공격적, FRIENDLY=친화적. 모든 품종에 배정돼 null 없음",
        example = "FRIENDLY",
    )
    val personality: Personality,
    @field:Schema(description = "품종 대표 이미지 URL. 없으면 null", nullable = true, example = "https://cdn.example.com/breeds/21.jpg")
    val representativeImageUrl: String?,
)

data class ColorView(
    @field:Schema(description = "색상 번호", example = "1")
    val colorSeq: Int,
    @field:Schema(description = "색상 이름", example = "흰색")
    val colorName: String,
)

data class DistrictView(
    @field:Schema(description = "행정구역 코드(법정동 코드 앞 5자리, 시·군·구). 신고·보호소 목록의 districtCode 필터 값", example = "27110")
    val districtCode: String,
    @field:Schema(description = "시·도 이름", example = "대구광역시")
    val sidoName: String,
    @field:Schema(description = "시·군·구 이름", example = "중구")
    val sigunguName: String,
)
