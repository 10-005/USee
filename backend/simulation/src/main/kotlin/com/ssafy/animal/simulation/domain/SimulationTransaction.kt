package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 지출 유형(DB 38). PURCHASE는 펫샵 구매, EVENT는 이벤트 해결 비용(VET_VISIT 진료비, M15·Q486) */
enum class TransactionType { PURCHASE, EVENT }

/**
 * DB 명세 38. simulation_transactions. 리포트 지출 집계의 근거라 한 번 쓰고 바꾸지 않는다.
 * PURCHASE는 item_seq, EVENT는 occurrence_seq가 필수(DB CHECK). 같은 스키마 FK는 숫자 컬럼으로 둔다.
 */
@Entity
@Table(name = "simulation_transactions")
class SimulationTransaction protected constructor(
    sessionSeq: Long,
    transactionType: TransactionType,
    itemSeq: Long?,
    occurrenceSeq: Long?,
    quantity: Int,
    amount: Int,
    balanceAfter: Int,
    description: String?,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var transactionSeq: Long? = null
        protected set

    @Column(nullable = false)
    var sessionSeq: Long = sessionSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var transactionType: TransactionType = transactionType
        protected set

    var itemSeq: Long? = itemSeq
        protected set

    var occurrenceSeq: Long? = occurrenceSeq
        protected set

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    @Column(nullable = false)
    var amount: Int = amount
        protected set

    /** 지출 후 잔액(음수 가능, M5) */
    @Column(nullable = false)
    var balanceAfter: Int = balanceAfter
        protected set

    @Column(length = 200)
    var description: String? = description
        protected set

    companion object {
        /** 18.9 구매 기록. 표시 문구는 "물품명 × 수량" */
        fun purchase(sessionSeq: Long, item: SimulationShopItem, quantity: Int, amount: Int, balanceAfter: Int): SimulationTransaction {
            require(quantity >= 1) { "수량은 1 이상이어야 합니다: $quantity" }
            require(amount >= 0) { "금액은 0 이상이어야 합니다: $amount" }
            return SimulationTransaction(
                sessionSeq = sessionSeq,
                transactionType = TransactionType.PURCHASE,
                itemSeq = item.itemSeq,
                occurrenceSeq = null,
                quantity = quantity,
                amount = amount,
                balanceAfter = balanceAfter,
                description = "${item.itemName} × $quantity",
            )
        }
    }
}
