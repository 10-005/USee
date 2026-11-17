package com.ssafy.animal.core.found.application.port.`in`

import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.swagger.v3.oas.annotations.media.Schema

interface BreedPredictionUseCase {
    /**
     * 8.9 발견 신고 사진으로 종·품종 추천(Q519). 4.2 확정 전의 imageKey 1~3개, 본인이 4.1로 발급받은 키만(Q456 순서 유지).
     * AI 서버 호출 실패는 503 FOUND_BREED_PREDICTION_UNAVAILABLE
     */
    fun predict(userSeq: Long, imageKeys: List<String>): BreedPrediction
}

data class BreedPrediction(
    @field:Schema(description = "추천 종 번호. 사진에서 개·고양이를 찾지 못하면 null", nullable = true, example = "1")
    val speciesSeq: Int?,
    @field:Schema(description = "추천 종 코드(DOG/CAT). 찾지 못하면 null", nullable = true, example = "DOG")
    val speciesCode: SpeciesCode?,
    @field:Schema(description = "자동 입력할 품종 번호. 품종 점수가 0.5 미만이면 null(종만 채운다)", nullable = true, example = "21")
    val breedSeq: Int?,
    @field:Schema(description = "자동 입력할 품종 이름. breedSeq가 null이면 null", nullable = true, example = "말티즈")
    val breedName: String?,
    @field:Schema(
        description = "품종 확신 정도. HIGH(점수 0.8 이상)·LOW(0.5 이상 0.8 미만, \"~인 것 같아요\"로 표시). breedSeq가 null이면 null",
        nullable = true,
        example = "HIGH",
    )
    val breedConfidence: BreedConfidence?,
    @field:Schema(description = "품종 후보(점수 높은 순, 최대 3개). breedSeq가 null이어도 참고용으로 내려간다")
    val candidates: List<BreedCandidate>,
    @field:Schema(description = "추천 품종(breedSeq)의 색상 선택지. 사용자가 고른 항목의 combinationSeq를 8.4에 보낸다. breedSeq가 null이면 빈 배열")
    val colors: List<BreedColor>,
) {
    companion object {
        val NONE = BreedPrediction(null, null, null, null, null, emptyList(), emptyList())
    }
}

/** AI 서버가 정한 품종 확신 정도(기준값은 AI 서버 설정 BREED_HIGH_SCORE·BREED_MIN_SCORE) */
enum class BreedConfidence { HIGH, LOW }

data class BreedColor(
    @field:Schema(description = "종·품종·색상 조합 번호. 발견 신고 등록(8.4)의 combinationSeq", example = "88")
    val combinationSeq: Int,
    @field:Schema(description = "색상 번호", example = "3")
    val colorSeq: Int,
    @field:Schema(description = "색상 이름", example = "흰색")
    val colorName: String,
)

data class BreedCandidate(
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "모델 점수(0~1). 사진별 점수의 평균이며 정답 확률로 보정된 값이 아니다", example = "0.69")
    val score: Double,
)
