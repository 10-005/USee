package com.ssafy.animal.core.found.application.port.out

/** AI 추천 서버(ai/breed-server). 실패는 예외로 알린다 */
interface BreedPredictionClient {
    fun predict(imageUrls: List<String>): AiBreedPrediction
}

/** species는 dog/cat/null, breed·label은 AI 팀 라벨(maltese 등). breed가 null이면 확신 없음 */
data class AiBreedPrediction(val species: String?, val breed: String?, val candidates: List<AiBreedCandidate>)

data class AiBreedCandidate(val label: String, val score: Double)
