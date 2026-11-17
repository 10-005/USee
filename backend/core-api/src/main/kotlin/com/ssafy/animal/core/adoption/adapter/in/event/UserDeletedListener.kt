package com.ssafy.animal.core.adoption.adapter.`in`.event

import com.ssafy.animal.common.event.UserDeleted
import com.ssafy.animal.core.adoption.application.port.`in`.AdoptionUseCase
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 2.6 탈퇴 시 본인 DRAFT/SUBMITTED/APPROVED → CANCELED.
 * WithdrawService가 탈퇴 트랜잭션 안에서 UserDeleted를 Spring 이벤트로 발행하므로, 동기 @EventListener는 같은 트랜잭션에서 돈다
 * (취소가 실패하면 탈퇴도 롤백). RabbitMQ로 나가는 UserDeleted는 커밋 뒤 별도 발행이라 여기와 무관하다
 */
@Component
class UserDeletedListener(
    private val adoptionUseCase: AdoptionUseCase,
) {
    @EventListener
    fun onUserDeleted(event: UserDeleted) = adoptionUseCase.cancelAllOfWithdrawnUser(event.userSeq)
}
