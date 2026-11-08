package com.ssafy.animal.core.catalog.application.port.`in`

interface GeocodeUseCase {

    /** 5.6 주소 검색. 0건이면 빈 목록, 외부 호출 실패는 503 CATALOG_GEOCODE_UNAVAILABLE */
    fun search(query: String): List<GeocodeCandidate>

    /**
     * 신고 등록·위치 수정 때 좌표 → districtCode(Q406). districts에 있는 코드만 돌려주고
     * 외부 호출 실패·미등록 코드는 null(등록을 막지 않는다, Q406-2). 외부 호출이므로 트랜잭션 밖에서 부른다.
     */
    fun resolveDistrictCode(latitude: Double, longitude: Double): String?
}

/** 네이버 응답의 roadAddress·jibunAddress·y·x를 그대로 옮긴다(5.6) */
data class GeocodeCandidate(
    val roadAddress: String?,
    val jibunAddress: String?,
    val latitude: Double,
    val longitude: Double,
)
