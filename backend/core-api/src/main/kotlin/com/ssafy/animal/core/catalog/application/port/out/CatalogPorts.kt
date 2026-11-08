package com.ssafy.animal.core.catalog.application.port.out

import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate

interface CatalogRepository {
    fun findClassifications(combinationSeqs: Collection<Int>): List<AnimalClassification>
    fun existsDistrict(districtCode: String): Boolean
}

/** 네이버 클라우드 Maps(Geocoding·Reverse Geocoding). 실패는 예외로 알린다 */
interface GeocodingClient {
    fun search(query: String): List<GeocodeCandidate>

    /** 좌표의 법정동 코드(10자리). 결과가 없으면 null */
    fun reverseLegalCode(latitude: Double, longitude: Double): String?
}
