package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.simulation.application.port.`in`.ShopItemView
import com.ssafy.animal.simulation.application.port.`in`.ShopQueryUseCase
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation/v1/simulations/shop")
class ShopController(
    private val shopQueryUseCase: ShopQueryUseCase,
) {

    /** 18.8 펫샵 물품 목록(로그인만 필요, 역할 무관). 페이지 없이 전량, display_order 순 */
    @GetMapping("/items")
    fun getItems(@RequestParam(required = false) speciesType: SpeciesType?) =
        shopQueryUseCase.getItems(speciesType).map(ShopItemResponse::from)
}

data class ShopItemResponse(
    val itemSeq: Long,
    val itemName: String,
    val category: ItemCategory,
    val isConsumable: Boolean,
    val price: Int,
    val description: String?,
    val imageUrl: String?,
    val speciesScope: SpeciesScope,
    /** 사용 시 스탯 변화(가안). 기구는 null */
    val effects: Map<String, Int>?,
    /** 1개 구매 시 인벤토리 증가량(묶음 물품, S15P31D106-240). 기구·단품은 1 */
    val usesPerPurchase: Int,
) {
    companion object {
        fun from(view: ShopItemView) = with(view.item) {
            ShopItemResponse(
                itemSeq = itemSeq,
                itemName = itemName,
                category = category,
                isConsumable = isConsumable,
                price = price,
                description = description,
                imageUrl = view.imageUrl,
                speciesScope = speciesScope,
                effects = effectsJsonb,
                usesPerPurchase = view.usesPerPurchase,
            )
        }
    }
}
