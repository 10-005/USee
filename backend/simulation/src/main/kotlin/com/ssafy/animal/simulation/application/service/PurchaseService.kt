package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseResult
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
import com.ssafy.animal.simulation.application.port.`in`.PurchasedLine
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesType
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
     * 명세 21.5의 4: 세션 잠금 → 진행(지연 계산·종료 판정) → 장바구니 전체 검증 → 물품마다 인벤토리 증가·잔액 차감·지출 기록.
     * 예정 종료가 지난 세션에서 사지 못하도록 먼저 진행시킨다. 도메인 오류는 모두 첫 쓰기 전(전체 검증)에서 나므로
     * 롤백하지 않고 그때까지의 지연 계산·종료 처리를 남긴다. 한 물품이라도 실패하면 아무것도 사지 않는다(Q503).
     */
    @Transactional(noRollbackFor = [BusinessException::class])
    override fun purchase(userSeq: Long, sessionSeq: Long, command: PurchaseCommand): PurchaseResult {
        require(command.lines.isNotEmpty()) { "장바구니가 비어 있습니다." }
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        progress.catchUp(session)
        if (!session.isInProgress) throw BusinessException(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS)
        val seq = checkNotNull(session.sessionSeq)

        val items = validate(seq, session.speciesType, command)

        val purchased = command.lines.zip(items) { line, item ->
            val inventoryItem = inventory.receive(seq, item, line.quantity)
            val amount = Math.multiplyExact(item.price, line.quantity)
            val balanceAfter = session.spend(amount)
            val transaction = transactionRepository.save(SimulationTransaction.purchase(seq, item, line.quantity, amount, balanceAfter))
            PurchasedLine(transaction, item, inventoryItem.quantity)
        }
        val totalAmount = purchased.fold(0) { sum, line -> Math.addExact(sum, line.transaction.amount) }
        return PurchaseResult(purchased, totalAmount, session.balance)
    }

    /** 쓰기 전에 장바구니를 앞에서부터 검증해 첫 오류를 낸다. 400의 field는 `items[i].필드` */
    private fun validate(
        sessionSeq: Long,
        speciesType: SpeciesType,
        command: PurchaseCommand,
    ): List<SimulationShopItem> {
        val seen = HashSet<Long>()
        return command.lines.mapIndexed { index, line ->
            if (!seen.add(line.itemSeq)) {
                throw invalid("items[$index].itemSeq", "같은 물품을 두 번 담을 수 없습니다.")
            }
            val item = shopItemRepository.findByItemSeq(line.itemSeq)?.takeIf { it.isActive }
                ?: throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND)
            if (!item.speciesScope.includes(speciesType)) {
                throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES)
            }
            if (item.category.isEquipment && line.quantity != 1) {
                throw invalid("items[$index].quantity", "기구는 1개만 살 수 있습니다.")
            }
            // 기구 재구매(409)를 돈을 쓰기 전에 걸러낸다
            inventory.checkReceivable(sessionSeq, item)
            item
        }
    }

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))
}
