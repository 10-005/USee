package com.ssafy.animal.core.found.adapter.out.ai

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ssafy.animal.core.found.application.port.`in`.BreedConfidence
import com.ssafy.animal.core.found.application.port.out.AiBreedCandidate
import com.ssafy.animal.core.found.application.port.out.AiBreedPrediction
import com.ssafy.animal.core.found.application.port.out.BreedPredictionClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** AI 추천 서버 주소(클러스터 내부). 비어 있으면 앱은 뜨고 추천은 503 */
@ConfigurationProperties("ai.breed-server")
data class BreedServerProperties(val baseUrl: String = "")

/** ai/breed-server의 POST /v1/breed-predictions 호출. 실패는 예외로 던지고 503 변환은 서비스가 한다 */
@Component
@EnableConfigurationProperties(BreedServerProperties::class)
class HttpBreedPredictionClient(
    private val properties: BreedServerProperties,
    restClientBuilder: RestClient.Builder,
) : BreedPredictionClient {

    // 사진 3장이면 CPU 추론만 2초 남짓(스레드 2개 실측)이라 읽기 대기는 넉넉히 둔다
    @Autowired
    constructor(properties: BreedServerProperties) : this(
        properties,
        RestClient.builder().requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build())
                .apply { setReadTimeout(Duration.ofSeconds(10)) },
        ),
    )

    private val restClient = restClientBuilder.baseUrl(properties.baseUrl).build()

    override fun predict(imageUrls: List<String>): AiBreedPrediction {
        check(properties.baseUrl.isNotBlank()) { "AI_BREED_SERVER_URL 미설정" }
        val response = restClient.post()
            .uri("/v1/breed-predictions")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("imageUrls" to imageUrls))
            .retrieve()
            .body(Response::class.java) ?: error("AI 추천 서버 응답이 비어 있습니다")
        return AiBreedPrediction(
            response.species,
            response.breed,
            BreedConfidence.entries.find { it.name == response.confidence },
            response.candidates.orEmpty().map { AiBreedCandidate(it.breed, it.score) },
        )
    }

    // label(영문 라벨)은 로그용이라 받지 않는다
    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Response(val species: String?, val breed: String?, val confidence: String?, val candidates: List<Candidate>?)

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Candidate(val breed: String, val score: Double)
}
