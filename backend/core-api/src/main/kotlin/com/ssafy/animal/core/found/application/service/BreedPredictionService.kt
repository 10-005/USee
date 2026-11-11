package com.ssafy.animal.core.found.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.BreedLookupUseCase
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
 * AI 서버는 서비스 DB를 모르므로 AI 라벨 → 공식 품종 코드 → 품종 변환은 여기서 한다.
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
        val breeds = breedLookupUseCase.findBreedsByCode(speciesCode) ?: return BreedPrediction.NONE
        fun breed(label: String?) = BREED_CODES[label]?.let(breeds.byCode::get)

        val top = breed(ai.breed)
        return BreedPrediction(
            speciesSeq = breeds.speciesSeq,
            speciesCode = speciesCode,
            breedSeq = top?.breedSeq,
            breedName = top?.breedName,
            candidates = ai.candidates.mapNotNull { c -> breed(c.label)?.let { BreedCandidate(it.breedSeq, it.breedName, c.score) } },
            colors = top?.let { breedLookupUseCase.findBreedColors(it.breedSeq) }.orEmpty().map { BreedColor(it.combinationSeq, it.colorSeq, it.colorName) },
        )
    }

    private companion object {
        const val MAX_IMAGES = 3
        val SPECIES = mapOf("dog" to SpeciesCode.DOG, "cat" to SpeciesCode.CAT)

        /**
         * AI 팀 품종 라벨(개 15·고양이 15) → 국가동물보호정보시스템 공식 품종 코드(animal_breeds.breed_code, Q507).
         * [가안] 공식 코드표에 여러 개로 나뉜 품종은 흔한 쪽으로(닥스훈트→미니어쳐, 웰시코기→펨브로크), 없는 말티푸는 믹스견.
         * scottish_straight는 맞는 품종이 없어 뺐다(스코티시폴드로 넣으면 귀 모양이 다른 틀린 값) → 종만 채운다.
         */
        val BREED_CODES = mapOf(
            "bichon_frise" to "000018", "chihuahua" to "000032", "dachshund" to "000076", "french_bulldog" to "000050",
            "golden_retriever" to "000054", "japanese_spitz" to "000062", "jindo" to "000067", "maltese" to "000072",
            "maltipoo" to "000114", "pomeranian" to "000089", "shiba_inu" to "000100", "shih_tzu" to "000101",
            "siberian_husky" to "000103", "welsh_corgi" to "000108", "yorkshire_terrier" to "000113",
            "abyssinian" to "000193", "american_shorthair" to "000192", "bengal" to "000179", "british_shorthair" to "000181",
            "korean_shorthair" to "000200", "maine_coon" to "000176", "munchkin" to "000175", "norwegian_forest" to "000170",
            "persian" to "000197", "ragdoll" to "000173", "russian_blue" to "000172", "scottish_fold" to "000188",
            "siamese" to "000184", "turkish_angora" to "000195",
        )
    }
}
