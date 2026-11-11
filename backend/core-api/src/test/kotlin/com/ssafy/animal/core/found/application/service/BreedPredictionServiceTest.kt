package com.ssafy.animal.core.found.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.BreedLookupUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.BreedRef
import com.ssafy.animal.core.catalog.application.port.`in`.SpeciesBreeds
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.found.application.port.`in`.BreedCandidate
import com.ssafy.animal.core.found.application.port.`in`.BreedPrediction
import com.ssafy.animal.core.found.application.port.out.AiBreedCandidate
import com.ssafy.animal.core.found.application.port.out.AiBreedPrediction
import com.ssafy.animal.core.found.application.port.out.BreedPredictionClient
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.found.domain.FoundImageErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.domain.ImageErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BreedPredictionServiceTest {

    /** 1000번대는 회원 1, 2000번대는 회원 2가 올린 사진 */
    private val imageUseCase = object : ImageUseCase {
        override fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary {
            val owner = when (imageSeq / 1000) { 1L -> 1L; 2L -> 2L; else -> throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND) }
            if (owner != userSeq) throw BusinessException(ImageErrorCode.IMAGE_NOT_OWNED)
            return ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        }
        override fun findSummary(imageSeq: Long): ImageSummary? = error("not used")
        override fun deleteIfUnreferenced(imageSeq: Long) = error("not used")
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
    }

    private val breeds = object : BreedLookupUseCase {
        override fun findBreedsByCode(speciesCode: SpeciesCode) = when (speciesCode) {
            SpeciesCode.DOG -> SpeciesBreeds(1, mapOf("000072" to BreedRef(21, "말티즈"), "000114" to BreedRef(63, "믹스견"), "000018" to BreedRef(5, "비숑 프리제")))
            SpeciesCode.CAT -> SpeciesBreeds(2, mapOf("000188" to BreedRef(230, "스코티시폴드")))
            else -> null
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

    private val service = BreedPredictionService(imageUseCase, breeds, client)

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException>(block = block).errorCode)

    @Test
    fun `AI 라벨을 공식 품종 코드로 바꿔 종·품종·후보를 채운다`() {
        aiResult = {
            AiBreedPrediction("dog", "maltese", listOf(AiBreedCandidate("maltese", 0.69), AiBreedCandidate("maltipoo", 0.29), AiBreedCandidate("bichon_frise", 0.005)))
        }

        val result = service.predict(1, listOf(1201, 1202))

        assertEquals(listOf("https://cdn/1201.jpg", "https://cdn/1202.jpg"), requestedUrls)
        assertEquals(
            BreedPrediction(
                1, SpeciesCode.DOG, 21, "말티즈",
                listOf(BreedCandidate(21, "말티즈", 0.69), BreedCandidate(63, "믹스견", 0.29), BreedCandidate(5, "비숑 프리제", 0.005)),
            ),
            result,
        )
    }

    @Test
    fun `품종 확신이 없거나 품종표에 없는 라벨이면 종만 채운다`() {
        aiResult = { AiBreedPrediction("dog", null, listOf(AiBreedCandidate("maltese", 0.4))) }
        assertEquals(BreedPrediction(1, SpeciesCode.DOG, null, null, listOf(BreedCandidate(21, "말티즈", 0.4))), service.predict(1, listOf(1201)))

        // scottish_straight는 공식 품종표에 없어 후보에서도 뺀다
        aiResult = {
            AiBreedPrediction("cat", "scottish_straight", listOf(AiBreedCandidate("scottish_straight", 0.9), AiBreedCandidate("scottish_fold", 0.05)))
        }
        assertEquals(BreedPrediction(2, SpeciesCode.CAT, null, null, listOf(BreedCandidate(230, "스코티시폴드", 0.05))), service.predict(1, listOf(1201)))
    }

    @Test
    fun `개·고양이를 찾지 못하면 전부 비운다`() {
        aiResult = { AiBreedPrediction(null, null, emptyList()) }
        assertEquals(BreedPrediction.NONE, service.predict(1, listOf(1201)))
    }

    @Test
    fun `사진 개수·중복·소유를 AI 호출 전에 검사한다`() {
        assertCode(FoundImageErrorCode.IMAGE_INVALID_COUNT) { service.predict(1, emptyList()) }
        assertCode(FoundImageErrorCode.IMAGE_INVALID_COUNT) { service.predict(1, listOf(1201, 1202, 1203, 1204)) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.predict(1, listOf(1201, 1201)) }
        assertCode(ImageErrorCode.IMAGE_NOT_OWNED) { service.predict(1, listOf(2201)) }
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.predict(1, listOf(9999)) }
        assertEquals(null, requestedUrls)
    }

    @Test
    fun `AI 서버 실패는 503`() {
        aiResult = { throw IllegalStateException("연결 실패") }
        assertCode(FoundErrorCode.FOUND_BREED_PREDICTION_UNAVAILABLE) { service.predict(1, listOf(1201)) }
    }
}
