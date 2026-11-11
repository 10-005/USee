package com.ssafy.animal.core.catalog.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.BreedLookupUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.BreedRef
import com.ssafy.animal.core.catalog.application.port.`in`.BreedView
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogQueryUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.ColorView
import com.ssafy.animal.core.catalog.application.port.`in`.DistrictView
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.SpeciesBreeds
import com.ssafy.animal.core.catalog.application.port.`in`.SpeciesView
import com.ssafy.animal.core.catalog.application.port.out.CatalogRepository
import com.ssafy.animal.core.catalog.application.port.out.GeocodingClient
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class CatalogService(
    private val catalogRepository: CatalogRepository,
    private val imageUseCase: ImageUseCase,
) : CatalogUseCase, CatalogQueryUseCase, BreedLookupUseCase {

    override fun requireClassification(combinationSeq: Int): AnimalClassification =
        catalogRepository.findClassifications(listOf(combinationSeq)).firstOrNull()
            ?: throw BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND)

    override fun findClassifications(combinationSeqs: Collection<Int>): Map<Int, AnimalClassification> =
        if (combinationSeqs.isEmpty()) emptyMap()
        else catalogRepository.findClassifications(combinationSeqs.toSet()).associateBy { it.combinationSeq }

    override fun requireDistrict(districtCode: String) {
        if (!catalogRepository.existsDistrict(districtCode)) throw BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE)
    }

    override fun findSpecies() = catalogRepository.findAllSpecies().map { SpeciesView(it.speciesSeq, it.speciesCode, it.speciesName) }

    // ponytail: 대표 이미지가 있는 품종마다 이미지 1건 조회. 시드 이미지가 늘어 느려지면 일괄 조회 포트를 추가
    override fun findBreeds(speciesSeq: Int) = catalogRepository.findBreeds(speciesSeq).map {
        BreedView(
            breedSeq = it.breedSeq,
            speciesSeq = it.speciesSeq,
            breedName = it.breedName,
            personality = it.personality,
            representativeImageUrl = it.representativeImageSeq?.let(imageUseCase::findSummary)?.imageUrl,
        )
    }

    override fun findColors() = catalogRepository.findAllColors().map { ColorView(it.colorSeq, it.colorName) }

    override fun findCombinations(breedSeq: Int?, colorSeq: Int?) = catalogRepository.findCombinations(breedSeq, colorSeq)

    override fun findDistricts(sidoName: String?) =
        catalogRepository.findDistricts(sidoName).map { DistrictView(it.districtCode, it.sidoName, it.sigunguName) }

    override fun findBreedColors(breedSeq: Int) = catalogRepository.findCombinations(breedSeq, null)

    override fun findBreedsByName(speciesCode: SpeciesCode): SpeciesBreeds? {
        val species = catalogRepository.findAllSpecies().firstOrNull { it.speciesCode == speciesCode } ?: return null
        val breeds = catalogRepository.findBreeds(species.speciesSeq)
        return SpeciesBreeds(species.speciesSeq, breeds.associate { it.breedName to BreedRef(it.breedSeq, it.breedName) })
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
