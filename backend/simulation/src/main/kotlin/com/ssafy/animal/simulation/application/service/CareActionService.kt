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
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.CareRule
import com.ssafy.animal.simulation.domain.CareRules
import com.ssafy.animal.simulation.domain.EffectSource
import com.ssafy.animal.simulation.domain.ItemCategory
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
            val owned = inventoryRepository.findOwnedByCategory(sessionSeq, category)
            if (owned.isEmpty()) throw BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING)
            val selected = command.itemSeq.takeIf { rule.selectsEquipment }
            if (selected == null) owned.first().shopItem else selectOwned(owned.map { it.shopItem }, category, selected)
        }
        val consumed = if (rule.needsItem) consume(session, rule, command.itemSeq) else null

        val effect = when (rule.effectSource) {
            EffectSource.CONSUMED_ITEM -> consumedItemEffect(sessionSeq, rule, checkNotNull(consumed).item)
            EffectSource.REQUIRED_EQUIPMENT, EffectSource.SELECTED_EQUIPMENT -> checkNotNull(equipment).effect()
            EffectSource.CONFIG -> properties.care.waterEffect
        }
        // 로그의 item_seq는 쓴 물품: 소모품, 또는 골라 쓴 기구(장난감)
        val usedItemSeq = consumed?.item?.itemSeq ?: equipment?.takeIf { rule.selectsEquipment }?.itemSeq
        return record(session, rule, consumed, usedItemSeq, effect)
    }

    /**
     * 소모품 효과. 캣닢은 직전 캣닢 주기 뒤 설정 시간(가안 6시간) 안이면 남용으로 보고 캣닢 효과 대신
     * 남용 효과(가안 안정감 -20)만 넣는다. 크게 떨어진 안정감은 조건 이벤트(259)로 이어진다(사용자 결정 B안).
     * 직전 시각은 행동 이력에서 찾으므로 이번 행동 로그를 저장하기 전에 부른다.
     */
    private fun consumedItemEffect(sessionSeq: Long, rule: CareRule, item: SimulationShopItem): StatEffect {
        if (rule.actionCode != ActionCode.GIVE_CATNIP) return item.effect()
        val last = actionLogRepository.findLastActionDttm(sessionSeq, ActionCode.GIVE_CATNIP) ?: return item.effect()
        val overused = last.isAfter(statRefresher.now().minus(properties.care.catnipOveruseWindow))
        return if (overused) properties.care.catnipOveruseEffect else item.effect()
    }

    /** itemSeq로 고른 기구가 보유 목록에 있는지 본다. 분류가 다르면 400, 없는 물품 404, 보유하지 않았으면 409 */
    private fun selectOwned(owned: List<SimulationShopItem>, category: ItemCategory, itemSeq: Long): SimulationShopItem {
        owned.firstOrNull { it.itemSeq == itemSeq }?.let { return it }
        val item = shopItemRepository.findByItemSeq(itemSeq) ?: throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND)
        if (item.category != category) throw invalid("itemSeq", "이 행동에는 $category 물품을 골라야 합니다.")
        throw BusinessException(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY)
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

    private fun record(
        session: SimulationSession,
        rule: CareRule,
        consumed: ConsumedItem?,
        usedItemSeq: Long?,
        effect: StatEffect,
    ): CareActionResult {
        val result = session.applyEffect(effect)
        session.recordAction(statRefresher.now())
        val log = actionLogRepository.save(SimulationActionLog.care(session, rule.actionCode, usedItemSeq, result))
        return CareActionResult(log, session, consumed, result.delta)
    }

    private fun requireForSpecies(session: SimulationSession, item: SimulationShopItem) {
        if (!item.speciesScope.includes(session.speciesType)) throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES)
    }

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))
}
