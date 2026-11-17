package com.ssafy.animal.core.auth.application.port.`in`

interface WithdrawUseCase {
    /** 2.6 회원 탈퇴 */
    fun withdraw(userSeq: Long)
}
