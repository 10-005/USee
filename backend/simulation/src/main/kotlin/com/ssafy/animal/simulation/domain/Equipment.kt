package com.ssafy.animal.simulation.domain

/**
 * 펫샵 물품 분류 14종(DB 36 category, M9 + S15P31D106-240). 기구는 한 번 사면 세션 내 영구, 나머지는 소모품.
 * 240에서 FURNITURE·LEASH·GROOMING_KIT(기구)와 POOP_BAG·CATNIP(소모품)을 추가했다.
 */
enum class ItemCategory(val isEquipment: Boolean) {
    FOOD_BOWL(true),
    WATER_BOWL(true),
    LITTER_BOX(true),
    TOY(true),
    /** 이동장·쉼터·숨숨 하우스·캣타워. 지금은 보유 여부만 남는다 */
    FURNITURE(true),
    /** 하네스와 리드줄. 산책을 시작하는 기본 조건(252에서 검증) */
    LEASH(true),
    /** 위생 관리 키트 */
    GROOMING_KIT(true),
    FOOD(false),
    SNACK(false),
    LITTER(false),
    SHAMPOO(false),
    MEDICINE(false),
    /** 산책용 배변봉투(252) */
    POOP_BAG(false),
    CATNIP(false),
}

/** 보유 기구(메인 화면 오브젝트 활성 여부). 명세 18.0 SimulationEquipment */
data class Equipment(
    val foodBowl: Boolean,
    val waterBowl: Boolean,
    val litterBox: Boolean,
    val toy: Boolean,
) {
    companion object {
        /** 인벤토리가 빈 채로 시작한다(18.1, M5·M9) */
        val NONE = Equipment(foodBowl = false, waterBowl = false, litterBox = false, toy = false)

        fun of(owned: Set<ItemCategory>) = Equipment(
            foodBowl = ItemCategory.FOOD_BOWL in owned,
            waterBowl = ItemCategory.WATER_BOWL in owned,
            litterBox = ItemCategory.LITTER_BOX in owned,
            toy = ItemCategory.TOY in owned,
        )
    }
}
