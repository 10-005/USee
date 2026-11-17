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
import com.ssafy.animal.core.found.application.port.`in`.BreedConfidence
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
        override fun findBreedsByName(speciesCode: SpeciesCode) = when (speciesCode) {
            SpeciesCode.DOG -> SpeciesBreeds(1, mapOf("말티즈" to BreedRef(21, "말티즈"), "말티푸" to BreedRef(255, "말티푸"), "비숑 프리제" to BreedRef(5, "비숑 프리제")))
            SpeciesCode.CAT -> SpeciesBreeds(2, mapOf("스코티시 폴드" to BreedRef(230, "스코티시 폴드")))
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
    fun `AI 품종 이름으로 품종표를 찾아 종·품종·확신·후보·색상 선택지를 채운다`() {
        aiResult = {
            AiBreedPrediction(
                "dog", "말티즈", BreedConfidence.LOW,
                listOf(AiBreedCandidate("말티즈", 0.69), AiBreedCandidate("말티푸", 0.29), AiBreedCandidate("비숑 프리제", 0.005)),
            )
        }

        val result = service.predict(1, listOf("u1/a.jpg", "u1/b.jpg"))

        assertEquals(listOf("https://cdn/u1/a.jpg", "https://cdn/u1/b.jpg"), requestedUrls)
        assertEquals(
            BreedPrediction(
                1, SpeciesCode.DOG, 21, "말티즈", BreedConfidence.LOW,
                listOf(BreedCandidate(21, "말티즈", 0.69), BreedCandidate(255, "말티푸", 0.29), BreedCandidate(5, "비숑 프리제", 0.005)),
                listOf(BreedColor(88, 3, "흰색"), BreedColor(89, 12, "크림색")),
            ),
            result,
        )
    }

    @Test
    fun `품종 확신이 없거나 품종표에 없는 이름이면 종만 채우고 확신·색상은 비운다`() {
        aiResult = { AiBreedPrediction("dog", null, null, listOf(AiBreedCandidate("말티즈", 0.4))) }
        assertEquals(
            BreedPrediction(1, SpeciesCode.DOG, null, null, null, listOf(BreedCandidate(21, "말티즈", 0.4)), emptyList()),
            service.predict(1, listOf("u1/a.jpg")),
        )

        // 품종표 개명 등으로 이름이 안 맞으면 그 품종은 후보에서도 뺀다(afterMigrate 검사가 막아야 하는 상황)
        aiResult = {
            AiBreedPrediction("cat", "없는 품종", BreedConfidence.HIGH, listOf(AiBreedCandidate("없는 품종", 0.9), AiBreedCandidate("스코티시 폴드", 0.05)))
        }
        assertEquals(
            BreedPrediction(2, SpeciesCode.CAT, null, null, null, listOf(BreedCandidate(230, "스코티시 폴드", 0.05)), emptyList()),
            service.predict(1, listOf("u1/a.jpg")),
        )
    }

    @Test
    fun `개·고양이를 찾지 못하면 전부 비운다`() {
        aiResult = { AiBreedPrediction(null, null, null, emptyList()) }
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
