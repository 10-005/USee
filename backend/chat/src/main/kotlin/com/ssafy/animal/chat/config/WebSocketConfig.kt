package com.ssafy.animal.chat.config

import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    // HTTP CORS와 같은 허용 출처(common.yml). 비어 있으면 같은 출처만 허용, Origin 없는 네이티브 앱은 영향 없음
    @Value("\${cors.allowed-origins:}") private val allowedOrigins: Array<String>,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/api/chat/v1/ws")
            .setAllowedOrigins(*allowedOrigins.map(String::trim).filter(String::isNotEmpty).toTypedArray())
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthChannelInterceptor)
    }
}
