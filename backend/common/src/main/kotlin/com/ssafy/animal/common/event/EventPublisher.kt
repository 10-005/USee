package com.ssafy.animal.common.event

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** 이벤트 발행 포트(port/out). 이벤트 클래스는 같은 패키지의 Events.kt */
interface EventPublisher {
    fun publish(event: Any)
}

/**
 * 트랜잭션 안에서 부르면 커밋된 뒤에만 보내고(롤백이면 버림), 밖에서 부르면 바로 보낸다.
 * 그래서 서비스는 @Transactional 메서드 안에서 그냥 publish하면 된다(별도 @TransactionalEventListener 불필요).
 * afterCompletion에서 보내므로 다른 커밋 후 콜백·리스너 안에서 불러도 사라지지 않는다.
 * ponytail: 전송 실패는 로그만 남긴다(최대 1회 전달). 유실이 문제가 되면 아웃박스 테이블로 바꾼다.
 */
class TransactionAwareEventPublisher(
    private val transport: (Any) -> Unit,
) : EventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publish(event: Any) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return send(event)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) send(event)
            }
        })
    }

    private fun send(event: Any) {
        runCatching { transport(event) }
            .onFailure { log.error("[event] 전송 실패 {} {}", event.javaClass.simpleName, event, it) }
    }
}
