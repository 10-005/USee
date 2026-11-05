package com.ssafy.animal.chat.config

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler
import java.security.KeyPairGenerator
import java.util.Base64

/** STOMP 핸드셰이크도 HTTP CORS와 같은 허용 출처(common.yml cors.allowed-origins)를 쓴다. */
@WebMvcTest
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
@Import(WebSocketConfig::class)
@ActiveProfiles("dev")
class WebSocketConfigTest {

    @Autowired
    @Qualifier("stompWebSocketHandlerMapping")
    lateinit var stompHandlerMapping: AbstractUrlHandlerMapping

    @Test
    fun `WebSocket 엔드포인트에 허용 출처가 반영된다`() {
        val handler = stompHandlerMapping.handlerMap["/api/chat/v1/ws"] as WebSocketHttpRequestHandler
        val originInterceptor = handler.handshakeInterceptors.filterIsInstance<OriginHandshakeInterceptor>().single()

        assertThat(originInterceptor.allowedOrigins)
            .containsExactlyInAnyOrder("http://localhost:5173", "http://127.0.0.1:5173")
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            val publicKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(publicKey.encoded) }
        }
    }
}
