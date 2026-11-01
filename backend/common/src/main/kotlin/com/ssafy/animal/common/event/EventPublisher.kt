package com.ssafy.animal.common.event

/** 이벤트 발행 포트(port/out). 메시지 큐가 정해지면 그 어댑터를 빈으로 등록하면 LoggingEventAdapter를 대체한다. */
interface EventPublisher {
    fun publish(event: Any)
}
