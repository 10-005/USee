package com.ssafy.animal.common.config

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.TransactionAwareEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

/**
 * 서비스 간 이벤트(API 명세 21.6). events.transport=rabbit이면 RabbitMQ, 아니면(기본 log) 로그만 남긴다.
 * 소비 쪽 @RabbitListener도 아래 변환기를 써서 common.event 클래스로 바로 받는다.
 */
@AutoConfiguration(after = [RabbitAutoConfiguration::class])
class CommonEventAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["events.transport"], havingValue = "rabbit")
    class RabbitTransport {

        /** 생산자는 교환기만 선언한다. 큐와 바인딩은 소비 서비스가 선언한다 */
        @Bean
        fun eventsExchange() = TopicExchange(EXCHANGE)

        // RabbitTemplate·리스너 컨테이너가 이 변환기를 쓴다. __TypeId__ 헤더로 클래스를 찾으므로 이벤트 패키지만 믿는다
        @Bean
        fun eventMessageConverter(jsonMapper: JsonMapper) = JacksonJsonMessageConverter(jsonMapper, EVENT_PACKAGE)

        @Bean
        @ConditionalOnMissingBean
        fun rabbitEventPublisher(rabbitTemplate: RabbitTemplate): EventPublisher =
            TransactionAwareEventPublisher { rabbitTemplate.convertAndSend(EXCHANGE, it.javaClass.simpleName, it) }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = ["events.transport"], havingValue = "log", matchIfMissing = true)
    class LogTransport {

        @Bean
        @ConditionalOnMissingBean
        fun logEventPublisher(): EventPublisher {
            val log = LoggerFactory.getLogger(EventPublisher::class.java)
            return TransactionAwareEventPublisher { log.info("[event] {} {}", it.javaClass.simpleName, it) }
        }
    }

    companion object {
        const val EXCHANGE = "animal.events"
        private const val EVENT_PACKAGE = "com.ssafy.animal.common.event"
    }
}
