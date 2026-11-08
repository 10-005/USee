package com.ssafy.animal.core.catalog.adapter.out.geocoding

import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.out.GeocodingClient
import org.springframework.stereotype.Component

/**
 * 네이버 어댑터가 들어오기 전 자리표시. 검색은 503, 역지오코딩은 null(신고 등록은 계속 된다, Q406-2).
 * ponytail: 네이버 Maps 어댑터(103)를 adapter/out/naver에 추가할 때 이 파일을 지운다.
 */
@Component
class UnconfiguredGeocodingClient : GeocodingClient {

    override fun search(query: String): List<GeocodeCandidate> = throw IllegalStateException("네이버 Geocoding 어댑터가 아직 없습니다")

    override fun reverseLegalCode(latitude: Double, longitude: Double): String? = null
}
