package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.auth.application.port.`in`.WithdrawUseCase
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 명세 2.6. 경로는 /users/me지만 인증·회원 장(2장)에 속해 auth에 둔다. */
@RestController
class WithdrawController(
    private val withdrawUseCase: WithdrawUseCase,
) {

    @DeleteMapping("/api/core/v1/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    fun withdraw(@AuthenticationPrincipal user: AuthUser) {
        withdrawUseCase.withdraw(user.userSeq)
    }
}
