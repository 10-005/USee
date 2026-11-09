package com.ssafy.animal.core.catalog.adapter.`in`.web

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogQueryUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeCandidate
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** API 명세 5장 기준정보. 로그인만 요구하고 역할 제한은 없다 */
@RestController
@RequestMapping("/api/core/v1")
class CatalogController(
    private val catalogQueryUseCase: CatalogQueryUseCase,
    private val geocodeUseCase: GeocodeUseCase,
) {

    /** 5.1 */
    @GetMapping("/animal-species")
    fun species() = catalogQueryUseCase.findSpecies()

    /** 5.2 */
    @GetMapping("/animal-species/{speciesSeq}/breeds")
    fun breeds(@PathVariable speciesSeq: Int) = catalogQueryUseCase.findBreeds(speciesSeq)

    /** 5.3 */
    @GetMapping("/animal-colors")
    fun colors() = catalogQueryUseCase.findColors()

    /** 5.4 */
    @GetMapping("/animal-combinations")
    fun combinations(@RequestParam(required = false) breedSeq: Int?, @RequestParam(required = false) colorSeq: Int?) =
        catalogQueryUseCase.findCombinations(breedSeq, colorSeq)

    /** 5.5 */
    @GetMapping("/districts")
    fun districts(@RequestParam(required = false) sidoName: String?) =
        catalogQueryUseCase.findDistricts(sidoName?.trim()?.ifEmpty { null })

    /** 5.6 query는 공백 제거 후 1~100자 */
    @GetMapping("/geocode")
    fun geocode(@RequestParam(required = false) query: String?): List<GeocodeCandidate> {
        val trimmed = query?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.length > MAX_QUERY_LENGTH) {
            throw BusinessException(
                CommonErrorCode.COMMON_INVALID_INPUT,
                errors = listOf(ErrorDetail("query", "1자 이상 ${MAX_QUERY_LENGTH}자 이하로 입력하세요.")),
            )
        }
        return geocodeUseCase.search(trimmed)
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 100
    }
}
