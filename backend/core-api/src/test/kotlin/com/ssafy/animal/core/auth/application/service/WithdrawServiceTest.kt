package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.auth.application.port.out.RefreshSession
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.auth.domain.UserDeleted
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AccountStatus
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import java.time.Duration
import java.util.function.Supplier
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 탈퇴 트랜잭션과 커밋 후 처리 */
class WithdrawServiceTest {

    private val user = User(Role.USER, "반려가족", "김가람", LocalDate.of(1997, 5, 12), "01012345678")
        .also { ReflectionTestUtils.setField(it, "userSeq", 1) }
    private val account = UserAccount(user, AuthProvider(1, AuthProvider.KAKAO), "kakao-1", null)

    private val springEvents = mutableListOf<Any>()
    private val published = mutableListOf<Any>()
    private val deletedAll = mutableListOf<Long>()
    private var redisDown = false
    private var mqDown = false

    private fun service(applicationEventPublisher: ApplicationEventPublisher) = WithdrawService(
        userRepository = object : UserRepository {
            override fun findById(userSeq: Int) = error("쓰지 않는다")
            override fun findActiveAccount(providerCode: String, loginIdentifier: String) = error("쓰지 않는다")
            override fun findActiveAccountByUserSeq(userSeq: Int) = account.takeIf { userSeq == 1 && !it.isDeleted }
            override fun existsActiveNickname(nickname: String, exceptUserSeq: Int?) = error("쓰지 않는다")
            override fun existsActivePhone(phone: String, exceptUserSeq: Int?) = error("쓰지 않는다")
            override fun saveAndFlush(user: User) = error("쓰지 않는다")
            override fun findShelterDisplay(userSeq: Int) = error("쓰지 않는다")
            override fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String) = error("쓰지 않는다")
        },
        refreshTokenStore = object : RefreshTokenStore {
            override fun save(userSeq: Long, jti: String, session: RefreshSession, ttl: Duration) = Unit
            override fun take(userSeq: Long, jti: String) = null
            override fun delete(userSeq: Long, jti: String) = Unit
            override fun deleteAll(userSeq: Long) {
                check(!redisDown) { "Redis 연결 실패" }
                deletedAll += userSeq
            }
        },
        eventPublisher = object : EventPublisher {
            override fun publish(event: Any) {
                check(!mqDown) { "MQ 연결 실패" }
                published += event
            }
        },
        applicationEventPublisher = applicationEventPublisher,
    )

    private val service = service { springEvents += it }

    /** 실제 트랜잭션 프록시로 호출해 커밋 후 리스너까지 돌린다. 예외가 나면 컨트롤러가 204를 못 낸다 */
    private fun withdrawInRealTransaction() = AnnotationConfigApplicationContext().use { context ->
        context.register(TxConfig::class.java)
        context.registerBean(WithdrawService::class.java, Supplier { service(context) }) // 람다만 주면 생성자 인자로 해석된다
        context.refresh()
        context.getBean(WithdrawService::class.java).withdraw(1)
    }

    @Test
    fun `Redis가 실패해도 예외 없이 끝나 204가 유지되고 UserDeleted는 발행된다`() {
        redisDown = true
        withdrawInRealTransaction()
        assertTrue(account.isDeleted)
        assertEquals(listOf<Any>(UserDeleted(1)), published)
        assertTrue(deletedAll.isEmpty())
    }

    @Test
    fun `MQ 발행이 실패해도 refresh는 삭제한다`() {
        mqDown = true
        withdrawInRealTransaction()
        assertTrue(account.isDeleted)
        assertEquals(listOf(1L), deletedAll)
    }

    /** DB 없이 @Transactional·@TransactionalEventListener만 실제로 돌리는 구성 */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true) // Boot 기본과 같은 CGLIB
    class TxConfig {
        @Bean
        fun transactionManager() = object : AbstractPlatformTransactionManager() {
            override fun doGetTransaction() = Any()
            override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
            override fun doCommit(status: DefaultTransactionStatus) = Unit
            override fun doRollback(status: DefaultTransactionStatus) = Unit
        }
    }

    @Test
    fun `users와 user_accounts를 소프트 삭제하고 커밋 후 처리는 트랜잭션 이벤트로 미룬다`() {
        service.withdraw(1)

        assertTrue(user.isDeleted)
        assertNotNull(user.deletedDttm)
        assertTrue(account.isDeleted)
        assertEquals(listOf<Any>(UserDeleted(1)), springEvents)
        assertTrue(published.isEmpty() && deletedAll.isEmpty())

        service.afterCommit(UserDeleted(1))
        assertEquals(listOf<Any>(UserDeleted(1)), published)
        assertEquals(listOf(1L), deletedAll)
    }

    @Test
    fun `이미 탈퇴했으면 401 AUTH_TOKEN_INVALID`() {
        service.withdraw(1)
        val e = assertFailsWith<BusinessException> { service.withdraw(1) }
        assertEquals(CommonErrorCode.AUTH_TOKEN_INVALID, e.errorCode)
    }

    @Test
    fun `정지 계정은 403 AUTH_ACCOUNT_UNAVAILABLE이고 삭제하지 않는다`() {
        ReflectionTestUtils.setField(user, "accountStatus", AccountStatus.SUSPENDED)
        val e = assertFailsWith<BusinessException> { service.withdraw(1) }
        assertEquals(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE, e.errorCode)
        assertTrue(!account.isDeleted && springEvents.isEmpty())
    }
}
