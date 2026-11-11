package com.ssafy.animal.core.found.application.port.`in`

import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.swagger.v3.oas.annotations.media.Schema

interface BreedPredictionUseCase {
    /**
     * 8.9 발견 신고 사진으로 종·품종 추천(Q519). 사진 1~3장, 본인 업로드만.
     * AI 서버 호출 실패는 503 FOUND_BREED_PREDICTION_UNAVAILABLE
     */
    fun predict(userSeq: Long, imageSeqs: List<Long>): BreedPrediction
}

data class BreedPrediction(
    @field:Schema(description = "추천 종 번호. 사진에서 개·고양이를 찾지 못하면 null", nullable = true, example = "1")
    val speciesSeq: Int?,
    @field:Schema(description = "추천 종 코드(DOG/CAT). 찾지 못하면 null", nullable = true, example = "DOG")
    val speciesCode: SpeciesCode?,
    @field:Schema(description = "자동 입력할 품종 번호. 확신이 낮거나 우리 품종표에 없는 품종이면 null(종만 채운다)", nullable = true, example = "21")
    val breedSeq: Int?,
    @field:Schema(description = "자동 입력할 품종 이름. breedSeq가 null이면 null", nullable = true, example = "말티즈")
    val breedName: String?,
    @field:Schema(description = "품종 후보(점수 높은 순, 최대 3개). breedSeq가 null이어도 참고용으로 내려간다")
    val candidates: List<BreedCandidate>,
) {
    companion object {
        val NONE = BreedPrediction(null, null, null, null, emptyList())
    }
}

data class BreedCandidate(
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "모델 점수(0~1). 사진별 점수의 평균이며 정답 확률로 보정된 값이 아니다", example = "0.69")
    val score: Double,
)
