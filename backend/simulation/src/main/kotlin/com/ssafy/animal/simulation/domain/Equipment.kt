package com.ssafy.animal.simulation.domain

import io.swagger.v3.oas.annotations.media.Schema

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

/** 보유 기구(메인 화면 오브젝트 활성 여부). 명세 18.0 SimulationEquipment. 응답에 그대로 나가 문서 설명을 단다 */
data class Equipment(
    @field:Schema(description = "밥그릇 보유. 있어야 밥 주기(FEED) 가능", example = "true")
    val foodBowl: Boolean,
    @field:Schema(description = "물그릇 보유. 있어야 물 주기(WATER) 가능", example = "true")
    val waterBowl: Boolean,
    @field:Schema(description = "화장실(고양이 화장실·강아지 배변판) 보유. 있어야 배변 치우기(CLEAN_LITTER) 가능", example = "false")
    val litterBox: Boolean,
    @field:Schema(description = "장난감 1개 이상 보유", example = "false")
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
