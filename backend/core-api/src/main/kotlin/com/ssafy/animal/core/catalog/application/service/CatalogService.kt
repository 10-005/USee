package com.ssafy.animal.core.catalog.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.catalog.application.port.out.CatalogRepository
import com.ssafy.animal.core.catalog.application.port.out.GeocodingClient
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CatalogService(
    private val catalogRepository: CatalogRepository,
) : CatalogUseCase {

    override fun requireClassification(combinationSeq: Int): AnimalClassification =
        catalogRepository.findClassifications(listOf(combinationSeq)).firstOrNull()
            ?: throw BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND)

    override fun findClassifications(combinationSeqs: Collection<Int>): Map<Int, AnimalClassification> =
        if (combinationSeqs.isEmpty()) emptyMap()
        else catalogRepository.findClassifications(combinationSeqs.toSet()).associateBy { it.combinationSeq }

    override fun requireDistrict(districtCode: String) {
        if (!catalogRepository.existsDistrict(districtCode)) throw BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE)
    }
}

/** 트랜잭션을 걸지 않는다: 외부 호출을 DB 커넥션을 쥔 채 기다리지 않도록(명세 21.1 2단계) */
@Service
class GeocodeService(
    private val geocodingClient: GeocodingClient,
    private val catalogRepository: CatalogRepository,
) : GeocodeUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun search(query: String): List<GeocodeCandidate> = try {
        geocodingClient.search(query)
    } catch (e: RuntimeException) {
        log.warn("주소 검색 실패", e)
        throw BusinessException(CatalogErrorCode.CATALOG_GEOCODE_UNAVAILABLE)
    }

    override fun resolveDistrictCode(latitude: Double, longitude: Double): String? {
        val legalCode = try {
            geocodingClient.reverseLegalCode(latitude, longitude)
        } catch (e: RuntimeException) {
            log.warn("역지오코딩 실패, districtCode=null로 진행", e)
            return null
        }
        // 법정동 코드 앞 5자리가 시·군·구. 마스터에 없으면 FK 위반이 나므로 null(Q406-2)
        return legalCode?.takeIf { it.length >= DISTRICT_CODE_LENGTH }
            ?.substring(0, DISTRICT_CODE_LENGTH)
            ?.takeIf(catalogRepository::existsDistrict)
    }

    private companion object {
        const val DISTRICT_CODE_LENGTH = 5
    }
}
