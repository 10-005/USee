package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.application.port.`in`.TransactionQueryUseCase
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class TransactionController(
    private val transactionQueryUseCase: TransactionQueryUseCase,
) {

    /** 18.11 지출 내역. 본인만 보며 그 외(보호소 포함)는 404로 감추므로 역할 제한을 두지 않는다(Q483). 정렬 regDttm만 */
    @GetMapping("/transactions")
    fun getTransactions(
        @AuthenticationPrincipal user: AuthUser,
        @PathVariable sessionSeq: Long,
        @RequestParam(required = false) transactionType: TransactionType?,
        @PageableDefault(size = 50) pageable: Pageable,
    ) = PageResponse.of(
        transactionQueryUseCase.getTransactions(user.userSeq, sessionSeq, transactionType, pageable).map(TransactionResponse::from),
    )
}

data class TransactionResponse(
    val transactionSeq: Long,
    val transactionType: TransactionType,
    /** PURCHASE면 구매 물품, EVENT면 null */
    val item: TransactionItem?,
    /** EVENT면 비용이 발생한 이벤트, PURCHASE면 null */
    val occurrenceSeq: Long?,
    val quantity: Int,
    val amount: Int,
    val balanceAfter: Int,
    val description: String?,
    val regDttm: OffsetDateTime?,
) {
    data class TransactionItem(
        val itemSeq: Long,
        val itemName: String,
        val category: ItemCategory,
    )

    companion object {
        fun from(entry: TransactionEntry) = with(entry.transaction) {
            TransactionResponse(
                transactionSeq = checkNotNull(transactionSeq),
                transactionType = transactionType,
                item = entry.item?.let { TransactionItem(it.itemSeq, it.itemName, it.category) },
                occurrenceSeq = occurrenceSeq,
                quantity = quantity,
                amount = amount,
                balanceAfter = balanceAfter,
                description = description,
                regDttm = regDttm,
            )
        }
    }
}
