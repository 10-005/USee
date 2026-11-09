package com.ssafy.animal.simulation.domain

/** 돌봄 행동의 효과를 어디서 가져오는지 */
enum class EffectSource {
    /** 이번에 쓴 소모품의 effects_jsonb(사료·배변용품 등) */
    CONSUMED_ITEM,

    /** 보유한 필요 기구의 effects_jsonb(목욕 = 위생 관리 키트) */
    REQUIRED_EQUIPMENT,

    /** 코드 설정값(물은 물품이 없어 simulation.care.water-effects) */
    CONFIG,
}

/**
 * 18.6 돌봄 행동 하나의 규칙(M9).
 * requiredEquipment는 수량 1 이상 보유해야 하는 기구, consumable은 itemSeq로 골라 1개 쓰는 소모품 분류.
 */
data class CareRule(
    val actionCode: ActionCode,
    val requiredEquipment: ItemCategory?,
    val consumable: ItemCategory?,
    val effectSource: EffectSource,
) {
    init {
        require(actionCode.type == ActionType.CARE) { "돌봄 행동 코드가 아닙니다: $actionCode" }
        require(requiredEquipment == null || requiredEquipment.isEquipment) { "필요 기구는 기구 분류여야 합니다: $requiredEquipment" }
        require(consumable == null || !consumable.isEquipment) { "소모품 분류여야 합니다: $consumable" }
        require(effectSource != EffectSource.CONSUMED_ITEM || consumable != null) { "소모품 효과인데 소모품이 없습니다: $actionCode" }
        require(effectSource != EffectSource.REQUIRED_EQUIPMENT || requiredEquipment != null) { "기구 효과인데 필요 기구가 없습니다: $actionCode" }
    }

    /** itemSeq를 받아야 하는 행동인지(소모품을 고름) */
    val needsItem: Boolean get() = consumable != null
}

/**
 * 돌봄 행동 규칙표(S15P31D106-238·246). VET_VISIT은 254에서 추가한다.
 * 목욕은 위생 관리 키트만 있으면 무제한(사용자 결정 2026-11-09, 명세 18.6의 SHAMPOO 소모를 대체).
 */
object CareRules {
    private val RULES = listOf(
        CareRule(ActionCode.FEED, requiredEquipment = ItemCategory.FOOD_BOWL, consumable = ItemCategory.FOOD, effectSource = EffectSource.CONSUMED_ITEM),
        CareRule(ActionCode.WATER, requiredEquipment = ItemCategory.WATER_BOWL, consumable = null, effectSource = EffectSource.CONFIG),
        CareRule(
            ActionCode.CLEAN_LITTER,
            requiredEquipment = ItemCategory.LITTER_BOX,
            consumable = ItemCategory.LITTER,
            effectSource = EffectSource.CONSUMED_ITEM,
        ),
        CareRule(
            ActionCode.BATHE,
            requiredEquipment = ItemCategory.GROOMING_KIT,
            consumable = null,
            effectSource = EffectSource.REQUIRED_EQUIPMENT,
        ),
        // 246: 기구 없이 소모품만 쓰는 행동
        CareRule(ActionCode.SNACK, requiredEquipment = null, consumable = ItemCategory.SNACK, effectSource = EffectSource.CONSUMED_ITEM),
        CareRule(ActionCode.MEDICINE, requiredEquipment = null, consumable = ItemCategory.MEDICINE, effectSource = EffectSource.CONSUMED_ITEM),
    ).associateBy { it.actionCode }

    /** 지원하는 돌봄 행동이 아니면 null(산책·시스템 코드, 아직 만들지 않은 행동) */
    fun of(actionCode: ActionCode): CareRule? = RULES[actionCode]
}
