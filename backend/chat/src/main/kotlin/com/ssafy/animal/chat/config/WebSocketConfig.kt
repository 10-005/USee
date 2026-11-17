package com.ssafy.animal.chat.config

import com.ssafy.animal.chat.adapter.`in`.stomp.ChatStompErrorHandler
import com.ssafy.animal.chat.adapter.`in`.stomp.ChatStompInterceptor
import com.ssafy.animal.common.auth.StompAuthChannelInterceptor
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import tools.jackson.databind.json.JsonMapper

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val stompAuthChannelInterceptor: StompAuthChannelInterceptor,
    private val chatStompInterceptor: ChatStompInterceptor,
    private val jsonMapper: JsonMapper,
    // HTTP CORS와 같은 허용 출처(common.yml). 비어 있으면 같은 출처만 허용, Origin 없는 네이티브 앱은 영향 없음
    @Value("\${cors.allowed-origins:}") private val allowedOrigins: Array<String>,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/api/chat/v1/ws")
            .setAllowedOrigins(*allowedOrigins.map(String::trim).filter(String::isNotEmpty).toTypedArray())
        registry.setErrorHandler(ChatStompErrorHandler(jsonMapper))
    }

    /**
     * SEND는 /app(@MessageMapping), 구독은 /topic/rooms/{id}와 /user/queue/{errors,room-updates}.
     * ponytail: 인메모리 브로커라 인스턴스 1개 전제. 여러 개 띄우면 명세의 RabbitMQ STOMP relay
     * (enableStompBrokerRelay + setUserDestinationBroadcast·setUserRegistryBroadcast)로 바꾼다(reactor-netty 의존성 필요, 코디네이터 요청)
     */
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
        registry.enableSimpleBroker("/topic", "/queue")
            .setHeartbeatValue(longArrayOf(HEARTBEAT_MS, HEARTBEAT_MS))
            .setTaskScheduler(heartbeatScheduler)
    }

    // 순서 중요: common이 CONNECT를 인증한 뒤 chat 인터셉터가 주체 교체·구독 인가를 한다
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(stompAuthChannelInterceptor, chatStompInterceptor)
    }

    // 빈으로 내지 않는다: TaskExecutor 빈이 생기면 Boot 기본 applicationTaskExecutor가 빠지고
    // STOMP 인바운드·아웃바운드 채널까지 이 1스레드로 처리된다(실측)
    private val heartbeatScheduler = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("stomp-heartbeat-")
        initialize()
    }

    @PreDestroy
    fun shutdownHeartbeatScheduler() = heartbeatScheduler.shutdown()

    companion object {
        private const val HEARTBEAT_MS = 10_000L
    }
}
