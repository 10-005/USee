package com.ssafy.animal.core.found.application.port.out

import com.ssafy.animal.core.found.application.port.`in`.BreedConfidence

/** AI 추천 서버(ai/breed-server). 실패는 예외로 알린다 */
interface BreedPredictionClient {
    fun predict(imageUrls: List<String>): AiBreedPrediction
}

/** species는 dog/cat/null, breed는 AI 팀 한글 표시 이름(말티즈 등). breed가 null이면 확신 없음(confidence도 null) */
data class AiBreedPrediction(val species: String?, val breed: String?, val confidence: BreedConfidence?, val candidates: List<AiBreedCandidate>)

data class AiBreedCandidate(val breed: String, val score: Double)
