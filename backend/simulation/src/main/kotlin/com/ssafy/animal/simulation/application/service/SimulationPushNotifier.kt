package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.SimulationPushRequested
import com.ssafy.animal.common.event.SimulationPushType
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.PushMessage
import com.ssafy.animal.simulation.domain.SimulationPushMessages
import com.ssafy.animal.simulation.domain.SimulationReportSummarized
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.TriggerContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 모의 입양 푸시(S15P31D106-260, Q481·Q487·Q500). notification에 SimulationPushRequested만 보내고 저장하지 않는다.
 * 공통 EventPublisher는 트랜잭션 안이면 커밋 뒤에만 보내므로 롤백된 이벤트·종료는 푸시되지 않는다.
 */
@Component
class SimulationPushNotifier(
    private val eventPublisher: EventPublisher,
) {

    /** 집 돌발 이벤트가 새로 생김(스케줄러). 산책 중 이벤트는 하트비트 응답으로 전하고 푸시하지 않는다(252) */
    fun eventOccurred(session: SimulationSession, scenario: EventScenario) {
        if (scenario.triggerContext != TriggerContext.HOME) return
        publish(session.userSeq, SimulationPushType.EVENT, SimulationPushMessages.event(session.animalName, scenario.title))
    }

    /**
     * 리포트 요약이 저장됨 → SESSION_END(Q487). 요약은 한 번만 채워지므로 푸시도 한 번이다.
     * 포기는 보내지 않는다(Q500). 종료 시각이 밤이어도 그대로 보낸다(사용자 결정 2026-11-09).
     */
    @EventListener
    fun onReportSummarized(event: SimulationReportSummarized) {
        val message = SimulationPushMessages.sessionEnd(event.status, event.animalName) ?: return
        publish(event.userSeq, SimulationPushType.SESSION_END, message)
    }

    private fun publish(userSeq: Int, type: SimulationPushType, message: PushMessage) {
        eventPublisher.publish(SimulationPushRequested(userSeq.toLong(), type, message.title, message.body))
    }
}
