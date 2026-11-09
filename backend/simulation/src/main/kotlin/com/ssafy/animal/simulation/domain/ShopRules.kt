package com.ssafy.animal.simulation.domain

/**
 * 펫샵 코드 설정값(S15P31D106-240). application.yml의 simulation.shop으로 받는다.
 * 묶음 물품은 1개를 사면 여러 번 쓴다(예 사료 1개 = 12끼). 물품의 속성이지만 스키마를 바꾸지 않기로 해
 * 시드의 고정 item_seq를 키로 설정에 둔다. 시드와 짝을 맞춰 고쳐야 한다.
 */
data class ShopRules(
    /** item_seq → 1개 구매 시 인벤토리 증가량. 없는 물품은 1 */
    val usesPerPurchase: Map<Long, Int> = emptyMap(),
) {
    init {
        require(usesPerPurchase.values.all { it >= 1 }) { "usesPerPurchase는 1 이상이어야 합니다: $usesPerPurchase" }
    }

    fun usesPerPurchase(itemSeq: Long): Int = usesPerPurchase[itemSeq] ?: 1
}
