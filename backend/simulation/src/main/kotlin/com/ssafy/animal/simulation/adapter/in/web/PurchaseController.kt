package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseResult
import com.ssafy.animal.simulation.application.port.`in`.PurchaseUseCase
import com.ssafy.animal.simulation.domain.ItemCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class PurchaseController(
    private val purchaseUseCase: PurchaseUseCase,
) {

    /** 18.9 펫샵 구매. 본인만 사며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    fun purchase(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable sessionSeq: Long,
        @Valid @RequestBody request: PurchaseRequest,
    ) = PurchaseResponse.from(purchaseUseCase.purchase(user.userSeq, sessionSeq, request.toCommand()))
}

/** 누락도 400 COMMON_INVALID_INPUT이 되도록 nullable로 받아 검증한다 */
data class PurchaseRequest(
    @field:NotNull
    val itemSeq: Long?,
    @field:NotNull
    @field:Min(1)
    // 명세에 상한은 없지만 금액·잔액 정수 범위를 넘지 않도록 한 번에 살 수 있는 수를 막아 둔다
    @field:Max(MAX_QUANTITY)
    val quantity: Int?,
) {
    fun toCommand() = PurchaseCommand(itemSeq!!, quantity!!)

    companion object {
        const val MAX_QUANTITY = 99L
    }
}

data class PurchaseResponse(
    val transactionSeq: Long,
    val item: PurchasedItem,
    val quantity: Int,
    val amount: Int,
    val balanceAfter: Int,
    /** 구매 후 보유 수량(묶음 물품은 남은 사용 횟수) */
    val inventoryQuantity: Int,
) {
    data class PurchasedItem(
        val itemSeq: Long,
        val itemName: String,
        val category: ItemCategory,
    )

    companion object {
        fun from(result: PurchaseResult) = PurchaseResponse(
            transactionSeq = checkNotNull(result.transaction.transactionSeq),
            item = PurchasedItem(result.item.itemSeq, result.item.itemName, result.item.category),
            quantity = result.transaction.quantity,
            amount = result.transaction.amount,
            balanceAfter = result.transaction.balanceAfter,
            inventoryQuantity = result.inventoryQuantity,
        )
    }
}
