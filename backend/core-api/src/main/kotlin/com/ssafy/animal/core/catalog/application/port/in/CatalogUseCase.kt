package com.ssafy.animal.core.catalog.application.port.`in`

import com.ssafy.animal.core.catalog.domain.SpeciesCode

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
    val combinationSeq: Int,
    val speciesSeq: Int,
    val speciesCode: SpeciesCode,
    val speciesName: String,
    val breedSeq: Int,
    val breedName: String,
    val colorSeq: Int,
    val colorName: String,
)
