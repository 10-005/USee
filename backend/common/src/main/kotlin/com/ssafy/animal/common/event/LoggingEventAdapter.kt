package com.ssafy.animal.common.event

import org.slf4j.LoggerFactory

// ponytail: MQ 미정(S15P31D106-293)이라 로그만 남김. MQ 어댑터 도입 시 커밋 이후 발행(아웃박스 등)도 같이 결정
class LoggingEventAdapter : EventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: Any) {
        log.info("[event] {} {}", event.javaClass.simpleName, event)
    }
}
