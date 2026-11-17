package com.ssafy.animal.notification.adapter.`in`.messaging

import com.ssafy.animal.common.event.ChatMessageSent
import com.ssafy.animal.common.event.CommentDeleted
import com.ssafy.animal.common.event.DeviceTokenDeactivateRequested
import com.ssafy.animal.common.event.NotificationCreated
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.SimulationPushRequested
import com.ssafy.animal.common.event.UserDeleted
import com.ssafy.animal.notification.application.port.`in`.NotificationEventUseCase
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 21.6 notification 소비 이벤트 7종. 큐 notification.{이벤트 이름}(durable)을 animal.events에 라우팅 키 = 클래스 이름으로 묶는다.
 * 처리는 NotificationEventUseCase가 한다. 메시지는 common의 JSON 변환기가 이벤트 클래스로 바꿔 준다
 */
@Component
@ConditionalOnProperty(name = ["events.transport"], havingValue = "rabbit")
class NotificationEventListener(
    private val useCase: NotificationEventUseCase,
) {

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.NotificationRequested", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["NotificationRequested"])])
    fun onNotificationRequested(event: NotificationRequested) = useCase.onNotificationRequested(event)

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.NotificationCreated", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["NotificationCreated"])])
    fun onNotificationCreated(event: NotificationCreated) = useCase.onNotificationCreated(event)

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.CommentDeleted", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["CommentDeleted"])])
    fun onCommentDeleted(event: CommentDeleted) = useCase.onCommentDeleted(event)

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.SimulationPushRequested", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["SimulationPushRequested"])])
    fun onSimulationPushRequested(event: SimulationPushRequested) = useCase.onSimulationPushRequested(event)

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.UserDeleted", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["UserDeleted"])])
    fun onUserDeleted(event: UserDeleted) = useCase.onUserDeleted(event)

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.DeviceTokenDeactivateRequested", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["DeviceTokenDeactivateRequested"])])
    fun onDeviceTokenDeactivateRequested(event: DeviceTokenDeactivateRequested) = useCase.onDeviceTokenDeactivateRequested(event)

    @RabbitListener(bindings = [QueueBinding(value = Queue("notification.ChatMessageSent", durable = "true"), exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC), key = ["ChatMessageSent"])])
    fun onChatMessageSent(event: ChatMessageSent) = useCase.onChatMessageSent(event)

    companion object {
        private const val EXCHANGE = "animal.events"
    }
}
