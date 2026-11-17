package com.ssafy.animal.chat.config

import com.ssafy.animal.chat.TestTokens
import com.ssafy.animal.chat.adapter.`in`.stomp.ChatStompInterceptor
import com.ssafy.animal.chat.application.port.`in`.ChatUseCase
import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.messaging.support.AbstractSubscribableChannel
import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler

/** STOMP 핸드셰이크도 HTTP CORS와 같은 허용 출처(common.yml cors.allowed-origins)를 쓴다. */
@WebMvcTest
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
@Import(WebSocketConfig::class, ChatStompInterceptor::class)
@ActiveProfiles("dev")
class WebSocketConfigTest {

    @Autowired
    @Qualifier("stompWebSocketHandlerMapping")
    lateinit var stompHandlerMapping: AbstractUrlHandlerMapping

    @Autowired
    @Qualifier("clientInboundChannel")
    lateinit var clientInboundChannel: AbstractSubscribableChannel

    // 슬라이스가 집는 ChatController·ChatStompController와 ChatStompInterceptor가 쓴다
    @MockitoBean
    lateinit var chatUseCase: ChatUseCase

    @Test
    fun `WebSocket 엔드포인트에 허용 출처가 반영된다`() {
        val handler = stompHandlerMapping.handlerMap["/api/chat/v1/ws"] as WebSocketHttpRequestHandler
        val originInterceptor = handler.handshakeInterceptors.filterIsInstance<OriginHandshakeInterceptor>().single()

        assertThat(originInterceptor.allowedOrigins)
            .containsExactlyInAnyOrder("http://localhost:5173", "http://127.0.0.1:5173")
    }

    @Test
    fun `CONNECT 인증(common) 다음에 chat 인터셉터가 돈다`() {
        assertThat(clientInboundChannel.interceptors.map<Any, Class<*>> { it.javaClass })
            .containsSubsequence(StompAuthChannelInterceptor::class.java, ChatStompInterceptor::class.java)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { TestTokens.publicKeyBase64 }
        }
    }
}
