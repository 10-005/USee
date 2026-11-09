package com.ssafy.animal.core.notification.adapter.`in`.messaging

import com.ssafy.animal.common.event.AiMatchFound
import com.ssafy.animal.core.notification.application.port.`in`.AiMatchUseCase
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/** AI 워커 → core-api(Q432). 처리는 AiMatchUseCase가 한다 */
@Component
@ConditionalOnProperty(name = ["events.transport"], havingValue = "rabbit")
class AiMatchListener(
    private val aiMatchUseCase: AiMatchUseCase,
) {

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue("core-api.AiMatchFound", durable = "true"),
                exchange = Exchange("animal.events", type = ExchangeTypes.TOPIC),
                key = ["AiMatchFound"],
            ),
        ],
    )
    fun onAiMatchFound(event: AiMatchFound) {
        aiMatchUseCase.handle(event)
    }
}
