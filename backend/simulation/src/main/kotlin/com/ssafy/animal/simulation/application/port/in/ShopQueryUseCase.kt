package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesType

interface ShopQueryUseCase {
    /** 18.8 펫샵 물품 목록(페이지 없이 전량). speciesType이 있으면 그 종과 ALL 물품만 */
    fun getItems(speciesType: SpeciesType?): List<ShopItemView>
}

/** 물품 + 사진 URL + 묶음 수(1개 구매 시 인벤토리 증가량) */
data class ShopItemView(
    val item: SimulationShopItem,
    val imageUrl: String?,
    val usesPerPurchase: Int,
)
