package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseResult
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationTransaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PurchaseService(
    private val sessionRepository: SimulationSessionRepository,
    private val shopItemRepository: ShopItemRepository,
    private val transactionRepository: TransactionRepository,
    private val inventory: SimulationInventory,
    private val progress: SessionProgress,
) : PurchaseUseCase {

    /**
     * 명세 21.5의 4: 세션 잠금 → 진행(지연 계산·종료 판정) → 검증 → 인벤토리 증가 → 잔액 차감 → 지출 기록.
     * 예정 종료가 지난 세션에서 사지 못하도록 먼저 진행시킨다. 도메인 오류는 모두 쓰기 전에 나므로 롤백하지 않고
     * 그때까지의 지연 계산·종료 처리를 남긴다.
     */
    @Transactional(noRollbackFor = [BusinessException::class])
    override fun purchase(userSeq: Long, sessionSeq: Long, command: PurchaseCommand): PurchaseResult {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        progress.catchUp(session)
        if (!session.isInProgress) throw BusinessException(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS)

        val item = shopItemRepository.findByItemSeq(command.itemSeq)?.takeIf { it.isActive }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND)
        if (!item.speciesScope.includes(session.speciesType)) {
            throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES)
        }
        if (item.category.isEquipment && command.quantity != 1) {
            throw BusinessException(
                CommonErrorCode.COMMON_INVALID_INPUT,
                errors = listOf(ErrorDetail("quantity", "기구는 1개만 살 수 있습니다.")),
            )
        }

        // 기구 재구매(409)를 돈을 쓰기 전에 걸러낸다. 이후 실패해도 같은 트랜잭션이라 함께 롤백된다
        val inventoryItem = inventory.receive(checkNotNull(session.sessionSeq), item, command.quantity)
        val amount = Math.multiplyExact(item.price, command.quantity)
        val balanceAfter = session.spend(amount)
        val transaction = transactionRepository.save(
            SimulationTransaction.purchase(session.sessionSeq!!, item, command.quantity, amount, balanceAfter),
        )
        return PurchaseResult(transaction, item, inventoryItem.quantity)
    }
}
