package com.ssafy.animal.common.config

import com.ssafy.animal.common.event.EventPublisher
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommonEventAutoConfigurationTest {

    private val runner = ApplicationContextRunner().withConfiguration(
        AutoConfigurations.of(JacksonAutoConfiguration::class.java, RabbitAutoConfiguration::class.java, CommonEventAutoConfiguration::class.java),
    )

    @Test
    fun `기본은 로그 퍼블리셔 하나만 등록한다`() {
        runner.run { context ->
            assertEquals(listOf("logEventPublisher"), context.getBeanNamesForType(EventPublisher::class.java).toList())
        }
    }

    @Test
    fun `rabbit이면 RabbitMQ 퍼블리셔와 JSON 변환기를 쓴다`() {
        runner.withPropertyValues("events.transport=rabbit").run { context ->
            assertEquals(listOf("rabbitEventPublisher"), context.getBeanNamesForType(EventPublisher::class.java).toList())
            assertIs<JacksonJsonMessageConverter>(context.getBean(RabbitTemplate::class.java).messageConverter)
        }
    }
}
