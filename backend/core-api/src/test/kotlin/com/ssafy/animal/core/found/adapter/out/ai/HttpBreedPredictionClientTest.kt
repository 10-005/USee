package com.ssafy.animal.core.found.adapter.out.ai

import com.ssafy.animal.core.found.application.port.`in`.BreedConfidence
import com.ssafy.animal.core.found.application.port.out.AiBreedCandidate
import com.ssafy.animal.core.found.application.port.out.AiBreedPrediction
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFails

class HttpBreedPredictionClientTest {

    private val builder = RestClient.builder()
    private val server = MockRestServiceServer.bindTo(builder).build()
    private val client = HttpBreedPredictionClient(BreedServerProperties("http://breed-server:8000"), builder)

    @Test
    fun `사진 주소를 보내고 AI 서버 응답(실제 서버 출력 형태)을 옮긴다`() {
        server.expect(requestTo("http://breed-server:8000/v1/breed-predictions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"imageUrls":["https://cdn/1.jpg","https://cdn/2.jpg"]}"""))
            .andRespond(
                withSuccess(
                    """{"species":"dog","breed":"말티즈","label":"maltese","confidence":"LOW","candidates":[{"breed":"말티즈","label":"maltese","score":0.6925380229949951},{"breed":"말티푸","label":"maltipoo","score":0.2879141569137573}],"images":[{"species":"dog","speciesScore":0.999,"error":null}],"modelVersion":"dog-convnext_mix,cat-convnext_weighted,clip-vit-base-patch32"}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            AiBreedPrediction(
                "dog", "말티즈", BreedConfidence.LOW,
                listOf(AiBreedCandidate("말티즈", 0.6925380229949951), AiBreedCandidate("말티푸", 0.2879141569137573)),
            ),
            client.predict(listOf("https://cdn/1.jpg", "https://cdn/2.jpg")),
        )
    }

    @Test
    fun `개·고양이를 못 찾은 응답은 null과 빈 후보`() {
        server.expect(requestTo("http://breed-server:8000/v1/breed-predictions"))
            .andRespond(
                withSuccess("""{"species":null,"breed":null,"label":null,"confidence":null,"candidates":[],"images":[],"modelVersion":"v"}""", MediaType.APPLICATION_JSON),
            )

        assertEquals(AiBreedPrediction(null, null, null, emptyList()), client.predict(listOf("https://cdn/1.jpg")))
    }

    @Test
    fun `서버 오류와 주소 미설정은 예외`() {
        server.expect(requestTo("http://breed-server:8000/v1/breed-predictions")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))
        assertFails { client.predict(listOf("https://cdn/1.jpg")) }
        assertFails { HttpBreedPredictionClient(BreedServerProperties(""), RestClient.builder()).predict(listOf("https://cdn/1.jpg")) }
    }
}
