package com.ssafy.animal.core.found.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.BreedLookupUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.BreedRef
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.found.application.port.`in`.BreedCandidate
import com.ssafy.animal.core.found.application.port.`in`.BreedColor
import com.ssafy.animal.core.found.application.port.`in`.BreedPrediction
import com.ssafy.animal.core.found.application.port.`in`.BreedPredictionUseCase
import com.ssafy.animal.core.found.application.port.out.BreedPredictionClient
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.found.domain.FoundImageErrorCode
import com.ssafy.animal.core.image.application.port.`in`.UploadedImageUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 8.9 발견 신고 사진 종·품종 추천(Q519). 트랜잭션을 걸지 않는다: AI 서버 호출을 DB 커넥션을 쥔 채 기다리지 않도록(21.1 2단계).
 * AI 서버는 서비스 DB를 모르므로 AI 품종 이름(한글 표시 이름) → 품종 변환은 여기서 한다. 품종표 이름을 AI 표시 이름에 맞췄으므로(Q520) 이름이 1:1로 같다.
 */
@Service
class BreedPredictionService(
    private val uploadedImageUseCase: UploadedImageUseCase,
    private val breedLookupUseCase: BreedLookupUseCase,
    private val breedPredictionClient: BreedPredictionClient,
) : BreedPredictionUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun predict(userSeq: Long, imageKeys: List<String>): BreedPrediction {
        if (imageKeys.size !in 1..MAX_IMAGES) throw BusinessException(FoundImageErrorCode.IMAGE_INVALID_COUNT)
        if (imageKeys.toSet().size != imageKeys.size) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("imageKeys", "같은 사진이 중복되었습니다.")))
        }
        val imageUrls = imageKeys.map { uploadedImageUseCase.requireUploadedUrl(userSeq, it) }

        val ai = try {
            breedPredictionClient.predict(imageUrls)
        } catch (e: RuntimeException) {
            log.warn("AI 품종 추천 실패", e)
            throw BusinessException(FoundErrorCode.FOUND_BREED_PREDICTION_UNAVAILABLE)
        }

        val speciesCode = SPECIES[ai.species] ?: return BreedPrediction.NONE
        val breeds = breedLookupUseCase.findBreedsByName(speciesCode) ?: return BreedPrediction.NONE
        // 품종표에 없는 이름은 품종표 개명 등으로 연결이 끊긴 것(afterMigrate 검사가 막는다). 로그만 남기고 그 품종은 비운다
        fun breed(name: String?): BreedRef? {
            if (name == null) return null
            return breeds.byName[name] ?: run { log.warn("AI 품종 이름이 품종표에 없습니다: {} {}", speciesCode, name); null }
        }

        val top = breed(ai.breed)
        return BreedPrediction(
            speciesSeq = breeds.speciesSeq,
            speciesCode = speciesCode,
            breedSeq = top?.breedSeq,
            breedName = top?.breedName,
            breedConfidence = top?.let { ai.confidence },
            candidates = ai.candidates.mapNotNull { c -> breed(c.breed)?.let { BreedCandidate(it.breedSeq, it.breedName, c.score) } },
            colors = top?.let { breedLookupUseCase.findBreedColors(it.breedSeq) }.orEmpty().map { BreedColor(it.combinationSeq, it.colorSeq, it.colorName) },
        )
    }

    private companion object {
        const val MAX_IMAGES = 3
        val SPECIES = mapOf("dog" to SpeciesCode.DOG, "cat" to SpeciesCode.CAT)
    }
}
