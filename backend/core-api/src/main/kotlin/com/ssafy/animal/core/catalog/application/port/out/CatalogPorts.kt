package com.ssafy.animal.core.catalog.application.port.out

import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.domain.AnimalBreed
import com.ssafy.animal.core.catalog.domain.AnimalColor
import com.ssafy.animal.core.catalog.domain.AnimalSpecies
import com.ssafy.animal.core.catalog.domain.District

interface CatalogRepository {
    fun findClassifications(combinationSeqs: Collection<Int>): List<AnimalClassification>
    fun existsDistrict(districtCode: String): Boolean

    fun findAllSpecies(): List<AnimalSpecies>
    fun findBreeds(speciesSeq: Int): List<AnimalBreed>
    fun findAllColors(): List<AnimalColor>
    fun findCombinations(breedSeq: Int?, colorSeq: Int?): List<AnimalClassification>
    fun findDistricts(sidoName: String?): List<District>
}

/** 네이버 클라우드 Maps(Geocoding·Reverse Geocoding). 실패는 예외로 알린다 */
interface GeocodingClient {
    fun search(query: String): List<GeocodeCandidate>

    /** 좌표의 법정동 코드(10자리). 결과가 없으면 null */
    fun reverseLegalCode(latitude: Double, longitude: Double): String?
}
