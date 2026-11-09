package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.simulation.application.port.`in`.InventoryItemView
import com.ssafy.animal.simulation.application.port.`in`.InventoryQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.InventoryView
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class InventoryController(
    private val inventoryQueryUseCase: InventoryQueryUseCase,
) {

    /** 18.10 보유 물품 + 잔액. 본인만 보며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @GetMapping("/inventory")
    fun getInventory(@AuthenticationPrincipal user: AuthUser, @PathVariable sessionSeq: Long) =
        InventoryResponse.from(inventoryQueryUseCase.getInventory(user.userSeq, sessionSeq))
}

data class InventoryResponse(
    val balance: Int,
    val expectedCost: Int,
    val equipment: Equipment,
    val items: List<InventoryItemResponse>,
) {
    companion object {
        fun from(view: InventoryView) = InventoryResponse(
            balance = view.session.balance,
            expectedCost = view.session.expectedCost,
            equipment = view.equipment,
            items = view.items.map(InventoryItemResponse::from),
        )
    }
}

data class InventoryItemResponse(
    val itemSeq: Long,
    val itemName: String,
    val category: ItemCategory,
    val isConsumable: Boolean,
    /** 남은 수량(묶음 물품은 남은 사용 횟수). 다 쓴 소모품은 0 */
    val quantity: Int,
    val imageUrl: String?,
) {
    companion object {
        fun from(view: InventoryItemView) = with(view.entry) {
            InventoryItemResponse(
                itemSeq = shopItem.itemSeq,
                itemName = shopItem.itemName,
                category = shopItem.category,
                isConsumable = shopItem.isConsumable,
                quantity = inventoryItem.quantity,
                imageUrl = view.imageUrl,
            )
        }
    }
}
