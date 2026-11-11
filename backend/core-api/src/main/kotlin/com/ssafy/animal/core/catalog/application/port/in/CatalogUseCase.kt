package com.ssafy.animal.core.catalog.application.port.`in`

import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 다른 도메인(반려동물·실종·발견·보호동물)이 쓰는 기준정보 조회. 기준정보 엔티티를 직접 import하지 말고 이 포트를 쓴다.
 * 5.1~5.5 목록 API는 catalog 컨트롤러가 따로 둔다.
 */
interface CatalogUseCase {

    /** 등록·수정 요청의 combinationSeq 검증 겸 표시용 분류. 없으면 404 CATALOG_COMBINATION_NOT_FOUND */
    fun requireClassification(combinationSeq: Int): AnimalClassification

    /** 목록 응답용 일괄 조회. 없는 seq는 결과에 없다 */
    fun findClassifications(combinationSeqs: Collection<Int>): Map<Int, AnimalClassification>

    /** 목록 필터 districtCode 검증(7.1·8.1·9.1·11.1). districts에 없으면 400 CATALOG_INVALID_DISTRICT_CODE */
    fun requireDistrict(districtCode: String)
}

/** API 명세 1.4 AnimalClassification. speciesCode는 AI 매칭 이벤트 페이로드에도 쓴다 */
data class AnimalClassification(
    @field:Schema(description = "종·품종·색상 조합 번호. 동물 등록·수정 요청에 이 값을 보낸다", example = "88")
    val combinationSeq: Int,
    @field:Schema(description = "종 번호", example = "1")
    val speciesSeq: Int,
    @field:Schema(description = "종 코드. DOG=개, CAT=고양이, ETC=기타", example = "DOG")
    val speciesCode: SpeciesCode,
    @field:Schema(description = "종 이름", example = "개")
    val speciesName: String,
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "색상 번호", example = "1")
    val colorSeq: Int,
    @field:Schema(description = "색상 이름", example = "흰색")
    val colorName: String,
)

/** AI 품종 추천(8.9)이 AI 라벨을 품종으로 바꿀 때 쓴다. CatalogUseCase와 따로 둔 것은 그쪽 가짜 구현(테스트)을 건드리지 않기 위해서다 */
interface BreedLookupUseCase {
    /** 종의 품종 전체를 공식 품종 코드(breed_code)로 찾을 수 있게 돌려준다. 종이 시드에 없으면 null */
    fun findBreedsByCode(speciesCode: SpeciesCode): SpeciesBreeds?
}

data class SpeciesBreeds(val speciesSeq: Int, val byCode: Map<String, BreedRef>)

data class BreedRef(val breedSeq: Int, val breedName: String)
