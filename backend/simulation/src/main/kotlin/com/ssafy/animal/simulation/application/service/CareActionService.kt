package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.CareActionResult
import com.ssafy.animal.simulation.application.port.`in`.CareActionUseCase
import com.ssafy.animal.simulation.application.port.`in`.ConsumedItem
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.CareRule
import com.ssafy.animal.simulation.domain.CareRules
import com.ssafy.animal.simulation.domain.EffectSource
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.StatEffect
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CareActionService(
    private val sessionRepository: SimulationSessionRepository,
    private val shopItemRepository: ShopItemRepository,
    private val inventoryRepository: InventoryRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val inventory: SimulationInventory,
    private val statRefresher: SessionStatRefresher,
    private val properties: SimulationProperties,
) : CareActionUseCase {

    /** 명세 21.5의 3: 세션 잠금 → 지연 계산 → 기구·소모품 검사 → 수량 -1 → 효과 반영 → CARE 로그 → last_action_dttm */
    @Transactional
    override fun act(userSeq: Long, sessionSeq: Long, command: CareActionCommand): CareActionResult {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        if (session.status != SessionStatus.IN_PROGRESS) throw BusinessException(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS)
        val rule = CareRules.of(command.actionCode) ?: throw invalid("actionCode", "지원하지 않는 돌봄 행동입니다: ${command.actionCode}")

        statRefresher.refresh(session)

        val equipment = rule.requiredEquipment?.let { category ->
            inventoryRepository.findOwnedByCategory(sessionSeq, category).firstOrNull()?.shopItem
                ?: throw BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING)
        }
        val consumed = if (rule.needsItem) consume(session, rule, command.itemSeq) else null

        val effect = when (rule.effectSource) {
            EffectSource.CONSUMED_ITEM -> checkNotNull(consumed).item.effect()
            EffectSource.REQUIRED_EQUIPMENT -> checkNotNull(equipment).effect()
            EffectSource.CONFIG -> properties.care.waterEffect
        }
        return record(session, rule, consumed, effect)
    }

    /** itemSeq로 고른 소모품이 행동 분류·세션 종에 맞는지 보고 1개 쓴다 */
    private fun consume(session: SimulationSession, rule: CareRule, itemSeq: Long?): ConsumedItem {
        val seq = itemSeq ?: throw invalid("itemSeq", "이 행동에는 사용할 물품이 필요합니다.")
        val item = shopItemRepository.findByItemSeq(seq) ?: throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND)
        if (item.category != rule.consumable) throw invalid("itemSeq", "${rule.actionCode}에는 ${rule.consumable} 물품을 써야 합니다.")
        requireForSpecies(session, item)
        // 판매가 중단된 물품도 이미 보유했다면 쓸 수 있다
        val remaining = inventory.consumeOne(checkNotNull(session.sessionSeq), item).quantity
        return ConsumedItem(item, remaining)
    }

    private fun record(session: SimulationSession, rule: CareRule, consumed: ConsumedItem?, effect: StatEffect): CareActionResult {
        val result = session.applyEffect(effect)
        session.recordAction(statRefresher.now())
        val log = actionLogRepository.save(SimulationActionLog.care(session, rule.actionCode, consumed?.item?.itemSeq, result))
        return CareActionResult(log, session, consumed, result.delta)
    }

    private fun requireForSpecies(session: SimulationSession, item: SimulationShopItem) {
        if (!item.speciesScope.includes(session.speciesType)) throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES)
    }

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))
}
