package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface TransactionQueryUseCase {
    /**
     * 18.11 지출 내역(본인 전용, Q435·Q483). 없거나 남의 세션이면 404 SIMULATION_NOT_FOUND(보호소 포함).
     * type이 null이면 PURCHASE·EVENT 전체. 종료된 세션도 조회할 수 있다.
     */
    fun getTransactions(userSeq: Long, sessionSeq: Long, type: TransactionType?, pageable: Pageable): Page<TransactionEntry>
}
