package com.ssafy.animal.core.found.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.BreedLookupUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.BreedRef
import com.ssafy.animal.core.catalog.application.port.`in`.SpeciesBreeds
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.found.application.port.`in`.BreedCandidate
import com.ssafy.animal.core.found.application.port.`in`.BreedColor
import com.ssafy.animal.core.found.application.port.`in`.BreedPrediction
import com.ssafy.animal.core.found.application.port.out.AiBreedCandidate
import com.ssafy.animal.core.found.application.port.out.AiBreedPrediction
import com.ssafy.animal.core.found.application.port.out.BreedPredictionClient
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.found.domain.FoundImageErrorCode
import com.ssafy.animal.core.image.application.port.`in`.UploadedImageUseCase
import com.ssafy.animal.core.image.domain.ImageErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BreedPredictionServiceTest {

    /** "u1/..." 키는 회원 1이 발급받은 것. 그 밖(남의 키·만료·이미 확정)은 4.2와 같이 404 */
    private val uploadedImages = object : UploadedImageUseCase {
        override fun requireUploadedUrl(userSeq: Long, imageKey: String): String {
            if (!imageKey.startsWith("u$userSeq/")) throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
            return "https://cdn/$imageKey"
        }
    }

    private val breeds = object : BreedLookupUseCase {
        override fun findBreedsByCode(speciesCode: SpeciesCode) = when (speciesCode) {
            SpeciesCode.DOG -> SpeciesBreeds(1, mapOf("000072" to BreedRef(21, "말티즈"), "000114" to BreedRef(63, "믹스견"), "000018" to BreedRef(5, "비숑 프리제")))
            SpeciesCode.CAT -> SpeciesBreeds(2, mapOf("000188" to BreedRef(230, "스코티시폴드")))
            else -> null
        }

        override fun findBreedColors(breedSeq: Int) = when (breedSeq) {
            21 -> listOf(
                AnimalClassification(88, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색"),
                AnimalClassification(89, 1, SpeciesCode.DOG, "개", 21, "말티즈", 12, "크림색"),
            )
            else -> error("품종이 정해지지 않았는데 색상을 조회했다: $breedSeq")
        }
    }

    private var requestedUrls: List<String>? = null
    private var aiResult: () -> AiBreedPrediction = { error("AI 응답 미설정") }
    private val client = object : BreedPredictionClient {
        override fun predict(imageUrls: List<String>): AiBreedPrediction {
            requestedUrls = imageUrls
            return aiResult()
        }
    }

    private val service = BreedPredictionService(uploadedImages, breeds, client)

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException>(block = block).errorCode)

    @Test
    fun `AI 라벨을 공식 품종 코드로 바꿔 종·품종·후보·색상 선택지를 채운다`() {
        aiResult = {
            AiBreedPrediction("dog", "maltese", listOf(AiBreedCandidate("maltese", 0.69), AiBreedCandidate("maltipoo", 0.29), AiBreedCandidate("bichon_frise", 0.005)))
        }

        val result = service.predict(1, listOf("u1/a.jpg", "u1/b.jpg"))

        assertEquals(listOf("https://cdn/u1/a.jpg", "https://cdn/u1/b.jpg"), requestedUrls)
        assertEquals(
            BreedPrediction(
                1, SpeciesCode.DOG, 21, "말티즈",
                listOf(BreedCandidate(21, "말티즈", 0.69), BreedCandidate(63, "믹스견", 0.29), BreedCandidate(5, "비숑 프리제", 0.005)),
                listOf(BreedColor(88, 3, "흰색"), BreedColor(89, 12, "크림색")),
            ),
            result,
        )
    }

    @Test
    fun `품종 확신이 없거나 품종표에 없는 라벨이면 종만 채우고 색상은 비운다`() {
        aiResult = { AiBreedPrediction("dog", null, listOf(AiBreedCandidate("maltese", 0.4))) }
        assertEquals(
            BreedPrediction(1, SpeciesCode.DOG, null, null, listOf(BreedCandidate(21, "말티즈", 0.4)), emptyList()),
            service.predict(1, listOf("u1/a.jpg")),
        )

        // scottish_straight는 공식 품종표에 없어 후보에서도 뺀다
        aiResult = {
            AiBreedPrediction("cat", "scottish_straight", listOf(AiBreedCandidate("scottish_straight", 0.9), AiBreedCandidate("scottish_fold", 0.05)))
        }
        assertEquals(
            BreedPrediction(2, SpeciesCode.CAT, null, null, listOf(BreedCandidate(230, "스코티시폴드", 0.05)), emptyList()),
            service.predict(1, listOf("u1/a.jpg")),
        )
    }

    @Test
    fun `개·고양이를 찾지 못하면 전부 비운다`() {
        aiResult = { AiBreedPrediction(null, null, emptyList()) }
        assertEquals(BreedPrediction.NONE, service.predict(1, listOf("u1/a.jpg")))
    }

    @Test
    fun `사진 개수·중복·발급자를 AI 호출 전에 검사한다`() {
        assertCode(FoundImageErrorCode.IMAGE_INVALID_COUNT) { service.predict(1, emptyList()) }
        assertCode(FoundImageErrorCode.IMAGE_INVALID_COUNT) { service.predict(1, listOf("u1/a", "u1/b", "u1/c", "u1/d")) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.predict(1, listOf("u1/a.jpg", "u1/a.jpg")) }
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.predict(1, listOf("u2/a.jpg")) } // 남이 발급받은 키
        assertEquals(null, requestedUrls)
    }

    @Test
    fun `AI 서버 실패는 503`() {
        aiResult = { throw IllegalStateException("연결 실패") }
        assertCode(FoundErrorCode.FOUND_BREED_PREDICTION_UNAVAILABLE) { service.predict(1, listOf("u1/a.jpg")) }
    }
}
