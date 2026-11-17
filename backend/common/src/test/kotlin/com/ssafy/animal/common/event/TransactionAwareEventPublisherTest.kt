package com.ssafy.animal.common.event

import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionAwareEventPublisherTest {

    private val sent = mutableListOf<Any>()
    private var transportDown = false
    private val publisher = TransactionAwareEventPublisher {
        check(!transportDown) { "MQ 연결 실패" }
        sent += it
    }

    /** DB 없이 동기화 콜백만 실제로 도는 트랜잭션 */
    private val tx = TransactionTemplate(object : AbstractPlatformTransactionManager() {
        override fun doGetTransaction() = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    })

    @Test
    fun `트랜잭션 밖에서는 바로 보낸다`() {
        publisher.publish(UserDeleted(1))
        assertEquals(listOf<Any>(UserDeleted(1)), sent)
    }

    @Test
    fun `트랜잭션 안에서는 커밋된 뒤에만 보내고 롤백이면 버린다`() {
        tx.executeWithoutResult {
            publisher.publish(UserDeleted(1))
            assertTrue(sent.isEmpty())
        }
        assertEquals(listOf<Any>(UserDeleted(1)), sent)

        tx.executeWithoutResult {
            publisher.publish(UserDeleted(2))
            it.setRollbackOnly()
        }
        assertEquals(listOf<Any>(UserDeleted(1)), sent)
    }

    @Test
    fun `다른 커밋 후 콜백 안에서 발행해도 사라지지 않는다`() {
        tx.executeWithoutResult {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = publisher.publish(UserDeleted(3))
            })
        }
        assertEquals(listOf<Any>(UserDeleted(3)), sent)
    }

    @Test
    fun `전송이 실패해도 예외를 올리지 않는다`() {
        transportDown = true
        tx.executeWithoutResult { publisher.publish(UserDeleted(1)) }
        publisher.publish(UserDeleted(2))
        assertTrue(sent.isEmpty())
    }
}
