package com.ssafy.animal.simulation.adapter.`in`.messaging

import com.ssafy.animal.common.event.UserDeleted
import com.ssafy.animal.simulation.application.port.`in`.SessionMaintenanceUseCase
import org.springframework.amqp.core.ExchangeTypes
import org.springframework.amqp.rabbit.annotation.Exchange
import org.springframework.amqp.rabbit.annotation.Queue
import org.springframework.amqp.rabbit.annotation.QueueBinding
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 21.6 UserDeleted(core-api 회원 탈퇴) 소비. 큐 simulation.UserDeleted(durable)를 animal.events에 라우팅 키 UserDeleted로 묶는다.
 * 진행 중 세션을 ABANDONED로 끝내고 리포트를 만든다. 재전달돼도 진행 중 세션이 없으면 아무것도 하지 않는다.
 */
@Component
@ConditionalOnProperty(name = ["events.transport"], havingValue = "rabbit")
class UserDeletedListener(
    private val maintenance: SessionMaintenanceUseCase,
) {

    @RabbitListener(
        bindings = [
            QueueBinding(
                value = Queue("simulation.UserDeleted", durable = "true"),
                exchange = Exchange(EXCHANGE, type = ExchangeTypes.TOPIC),
                key = ["UserDeleted"],
            ),
        ],
    )
    fun onUserDeleted(event: UserDeleted) = maintenance.abandonForWithdrawal(event.userSeq)

    companion object {
        private const val EXCHANGE = "animal.events"
    }
}
