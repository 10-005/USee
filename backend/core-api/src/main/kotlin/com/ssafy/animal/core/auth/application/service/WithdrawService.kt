package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.auth.application.port.`in`.WithdrawUseCase
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.common.event.UserDeleted
import com.ssafy.animal.core.user.application.port.out.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import java.time.OffsetDateTime

/** 입양 신청 CANCELED(명세 2.6)는 입양 도메인 구현 후 이 트랜잭션에 추가한다. */
@Service
class WithdrawService(
    private val userRepository: UserRepository,
    private val refreshTokenStore: RefreshTokenStore,
    private val eventPublisher: EventPublisher,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : WithdrawUseCase {

    @Transactional
    override fun withdraw(userSeq: Long) {
        // 이미 탈퇴한 계정의 남은 access 토큰은 쓰기 재확인(Q433)과 같은 401
        val account = userRepository.findActiveAccountByUserSeq(userSeq.toInt())
            ?: throw BusinessException(CommonErrorCode.AUTH_TOKEN_INVALID)
        if (!account.user.isActive) throw BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
        account.withdraw(OffsetDateTime.now())
        applicationEventPublisher.publishEvent(UserDeleted(userSeq))
    }

    /**
     * 커밋된 뒤에만 실행된다(롤백이면 호출되지 않음). 탈퇴는 이미 확정이므로 여기서 실패해도 예외를 올리지 않고(204 유지) 로그만 남긴다.
     * 하나가 실패해도 다른 하나는 실행한다. refresh가 남아도 2.3이 탈퇴 회원을 거절한다.
     */
    @TransactionalEventListener
    fun afterCommit(event: UserDeleted) {
        runCatching { eventPublisher.publish(event) }
            .onFailure { log.error("UserDeleted 발행 실패 userSeq={}", event.userSeq, it) }
        runCatching { refreshTokenStore.deleteAll(event.userSeq) }
            .onFailure { log.error("탈퇴 회원 refresh 삭제 실패 userSeq={}", event.userSeq, it) }
    }

    private val log = LoggerFactory.getLogger(javaClass)
}
