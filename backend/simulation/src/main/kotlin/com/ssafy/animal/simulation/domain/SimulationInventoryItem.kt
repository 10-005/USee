package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * DB 명세 37. simulation_inventory_items. 세션별 보유 물품(UNIQUE(session_seq, item_seq)).
 * 소모품은 써서 0이 되어도 행을 남긴다(앱이 "다 썼음" 표시). 같은 스키마 FK는 숫자 컬럼으로 둔다.
 */
@Entity
@Table(name = "simulation_inventory_items")
class SimulationInventoryItem(
    sessionSeq: Long,
    itemSeq: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var inventoryItemSeq: Long? = null
        protected set

    @Column(nullable = false)
    var sessionSeq: Long = sessionSeq
        protected set

    @Column(nullable = false)
    var itemSeq: Long = itemSeq
        protected set

    @Column(nullable = false)
    var quantity: Int = 0
        protected set

    val isEmpty: Boolean get() = quantity == 0

    fun add(amount: Int) {
        require(amount >= 1) { "증가량은 1 이상이어야 합니다: $amount" }
        quantity = Math.addExact(quantity, amount)
    }

    fun consumeOne() {
        check(quantity > 0) { "남은 수량이 없습니다: inventoryItemSeq=$inventoryItemSeq" }
        quantity -= 1
    }
}
