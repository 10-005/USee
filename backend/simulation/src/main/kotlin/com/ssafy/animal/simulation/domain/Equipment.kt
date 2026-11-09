package com.ssafy.animal.simulation.domain

/** 펫샵 물품 분류 9종(DB 36 category, M9). 기구 4종은 한 번 사면 세션 내 영구, 나머지는 소모품 */
enum class ItemCategory(val isEquipment: Boolean) {
    FOOD_BOWL(true),
    WATER_BOWL(true),
    LITTER_BOX(true),
    TOY(true),
    FOOD(false),
    SNACK(false),
    LITTER(false),
    SHAMPOO(false),
    MEDICINE(false),
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
