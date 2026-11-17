package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.CareActionResult
import com.ssafy.animal.simulation.application.port.`in`.ConsumedItem
import com.ssafy.animal.simulation.application.port.`in`.EventView
import com.ssafy.animal.simulation.application.port.`in`.WalkActionResult
import com.ssafy.animal.simulation.application.port.`in`.WalkEndResult
import com.ssafy.animal.simulation.application.port.`in`.WalkHeartbeatResult
import com.ssafy.animal.simulation.application.port.`in`.WalkStartResult
import com.ssafy.animal.simulation.application.port.`in`.WalkUseCase
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.WalkRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.EffectResult
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatEffect
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * 18.14~18.17 산책(S15P31D106-252). 세션 잠금 → 지금 시각까지 진행(catchUp) → 산책 처리.
 * 도메인 오류는 롤백하지 않는다: 오류는 모두 쓰기 전에 나고, 그때까지의 진행·종료·자동 종료는 남아야 한다(행동 18.6과 같은 원칙).
 */
@Service
class WalkService(
    private val sessionRepository: SimulationSessionRepository,
    private val walkRepository: WalkRepository,
    private val inventoryRepository: InventoryRepository,
    private val shopItemRepository: ShopItemRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val inventory: SimulationInventory,
    private val statRefresher: SessionStatRefresher,
    private val progress: SessionProgress,
    private val events: SessionEvents,
    private val lifecycle: WalkLifecycle,
    private val properties: SimulationProperties,
) : WalkUseCase {

    @Transactional(noRollbackFor = [BusinessException::class])
    override fun start(userSeq: Long, sessionSeq: Long): WalkStartResult {
        val session = lockInProgress(userSeq, sessionSeq)
        if (session.speciesType != SpeciesType.DOG) throw BusinessException(SimulationErrorCode.SIMULATION_WALK_NOT_FOR_SPECIES)
        if (walkRepository.findActive(sessionSeq) != null) throw BusinessException(SimulationErrorCode.SIMULATION_WALK_ALREADY_IN_PROGRESS)
        if (inventoryRepository.findOwnedByCategory(sessionSeq, ItemCategory.LEASH).isEmpty()) {
            throw BusinessException(SimulationErrorCode.SIMULATION_EQUIPMENT_MISSING)
        }
        val now = statRefresher.now()
        val walk = walkRepository.save(SimulationWalk.start(session, now))
        session.recordAction(now)
        actionLogRepository.save(SimulationActionLog.walk(session, ActionCode.WALK_START, checkNotNull(walk.walkSeq)))
        // 시연 회원은 하트비트를 짧게 받아 배변 이벤트가 시작 30초 무렵에 뜨게 한다
        val heartbeatInterval = if (properties.demo.isDemo(session)) properties.demo.heartbeatInterval else properties.walk.heartbeatInterval
        return WalkStartResult(walk, session, heartbeatInterval)
    }

    @Transactional(noRollbackFor = [BusinessException::class])
    override fun heartbeat(userSeq: Long, sessionSeq: Long, walkSeq: Long): WalkHeartbeatResult {
        val session = lockInProgress(userSeq, sessionSeq)
        val now = statRefresher.now()
        val walk = activeWalk(session, walkSeq, now)
        walk.heartbeat(now)
        val created = events.triggerWalk(session, walk, now)
        val pending = if (created == null) events.pending(session) else null
        return WalkHeartbeatResult(
            walk = walk,
            session = session,
            elapsedMinutes = walk.durationMinutes(now),
            newEvent = created?.let { EventView(it.occurrence, it.scenario) },
            pendingEvent = pending?.let { EventView(it.occurrence, it.scenario) },
        )
    }

    @Transactional(noRollbackFor = [BusinessException::class])
    override fun act(userSeq: Long, sessionSeq: Long, walkSeq: Long, command: CareActionCommand): WalkActionResult {
        val session = lockInProgress(userSeq, sessionSeq)
        val now = statRefresher.now()
        val walk = activeWalk(session, walkSeq, now)
        val (consumed, effect) = when (command.actionCode) {
            ActionCode.GIVE_WATER -> null to properties.walk.waterEffect
            ActionCode.GIVE_SNACK -> consume(session, ItemCategory.SNACK, command.itemSeq ?: throw invalid("itemSeq", "간식 주기에는 간식(SNACK)을 골라야 합니다."))
                .let { it to it.item.effect() }
            ActionCode.CLEAN_UP_POOP -> {
                // 치울 배변(이 산책의 배변 이벤트)이 있을 때만 배변봉투를 쓴다(사용자 결정)
                events.pending(session)?.takeIf { it.isAnsweredOnWalk(ActionCode.CLEAN_UP_POOP, walkSeq) }
                    ?: throw BusinessException(SimulationErrorCode.SIMULATION_POOP_NOT_FOUND)
                consume(session, ItemCategory.POOP_BAG, command.itemSeq ?: firstOwned(session, ItemCategory.POOP_BAG)) to StatEffect.NONE
            }
            else -> throw invalid("actionCode", "산책 중에는 GIVE_WATER·GIVE_SNACK·CLEAN_UP_POOP만 할 수 있습니다: ${command.actionCode}")
        }
        return WalkActionResult(walkSeq, record(session, walk, command.actionCode, consumed, effect, now))
    }

    @Transactional(noRollbackFor = [BusinessException::class])
    override fun end(userSeq: Long, sessionSeq: Long, walkSeq: Long): WalkEndResult {
        val session = lockInProgress(userSeq, sessionSeq)
        val now = statRefresher.now()
        val walk = ownWalk(session, walkSeq)
        // 하트비트가 끊긴 뒤 누른 종료는 409 대신 마지막 하트비트 시각으로 끝내고 결과를 준다
        val endedAt = if (walk.isStaleAt(now, properties.walk.autoEndAfter)) walk.lastActiveAt else now
        return lifecycle.end(session, walk, endedAt)
    }

    /** 18.16 결과 기록: 행동 효과 → 이 산책의 대기 이벤트 정답이면 해결 효과(합산) → WALK 로그 → 해결 처리 */
    private fun record(
        session: SimulationSession,
        walk: SimulationWalk,
        actionCode: ActionCode,
        consumed: ConsumedItem?,
        effect: StatEffect,
        now: OffsetDateTime,
    ): CareActionResult {
        val walkSeq = checkNotNull(walk.walkSeq)
        val itemResult = session.applyEffect(effect)
        val answered = events.pending(session)?.takeIf { it.isAnsweredOnWalk(actionCode, walkSeq) }
        val eventResult = answered?.let { session.applyEffect(it.scenario.resolveEffect()) }
        val result = eventResult?.let { EffectResult(itemResult.before, it.after) } ?: itemResult
        session.recordAction(now)
        val log = actionLogRepository.save(
            SimulationActionLog.walk(session, actionCode, walkSeq, result, consumed?.item?.itemSeq, answered?.occurrence?.occurrenceSeq),
        )
        val resolved = answered?.let { events.resolve(session, it, log, checkNotNull(eventResult), now) }
        return CareActionResult(log, session, consumed, result.delta, resolved)
    }

    /** 본인 세션을 잠그고 진행시킨다. 없거나 남의 세션 404, 진행 중이 아니면(이번에 끝난 경우 포함) 409 */
    private fun lockInProgress(userSeq: Long, sessionSeq: Long): SimulationSession {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        progress.catchUp(session)
        if (!session.isInProgress) throw BusinessException(SimulationErrorCode.SIMULATION_NOT_IN_PROGRESS)
        return session
    }

    /** 이 세션의 진행 중 산책. 없거나 다른 세션 404, 끝났으면 409 */
    private fun ownWalk(session: SimulationSession, walkSeq: Long): SimulationWalk {
        val walk = walkRepository.findByWalkSeq(walkSeq)?.takeIf { it.sessionSeq == session.sessionSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_WALK_NOT_FOUND)
        if (!walk.isInProgress) throw BusinessException(SimulationErrorCode.SIMULATION_WALK_NOT_IN_PROGRESS)
        return walk
    }

    /** 진행 중 산책(하트비트·행동). 하트비트가 끊겼으면 마지막 하트비트 시각으로 자동 종료하고 409 */
    private fun activeWalk(session: SimulationSession, walkSeq: Long, now: OffsetDateTime): SimulationWalk {
        val walk = ownWalk(session, walkSeq)
        if (lifecycle.endIfStale(session, walk, now)) {
            throw BusinessException(SimulationErrorCode.SIMULATION_WALK_NOT_IN_PROGRESS)
        }
        return walk
    }

    /** 고른 소모품이 분류·세션 종에 맞는지 보고 1개 쓴다(18.6과 같은 규칙) */
    private fun consume(session: SimulationSession, category: ItemCategory, itemSeq: Long): ConsumedItem {
        val item = shopItemRepository.findByItemSeq(itemSeq) ?: throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOUND)
        if (item.category != category) throw invalid("itemSeq", "이 행동에는 $category 물품을 써야 합니다.")
        requireForSpecies(session, item)
        val remaining = inventory.consumeOne(checkNotNull(session.sessionSeq), item).quantity
        return ConsumedItem(item, remaining)
    }

    /** itemSeq를 생략하면 그 분류에서 남은 수량이 있는 첫 물품. 없으면 409 */
    private fun firstOwned(session: SimulationSession, category: ItemCategory): Long =
        inventoryRepository.findOwnedByCategory(checkNotNull(session.sessionSeq), category).firstOrNull()?.shopItem?.itemSeq
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_ITEM_NOT_IN_INVENTORY)

    private fun requireForSpecies(session: SimulationSession, item: SimulationShopItem) {
        if (!item.speciesScope.includes(session.speciesType)) throw BusinessException(SimulationErrorCode.SIMULATION_SHOP_ITEM_NOT_FOR_SPECIES)
    }

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))
}
