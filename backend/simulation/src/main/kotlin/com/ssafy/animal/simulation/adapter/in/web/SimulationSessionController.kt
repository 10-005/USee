package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation/v1/simulations")
class SimulationSessionController(
    private val startSimulationUseCase: StartSimulationUseCase,
) {

    /** 18.1 세션 시작. 품종·가상 이름·예상 비용으로 시작하고 실제 보호동물과는 연결하지 않는다(Q482) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    fun start(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: StartSimulationRequest) =
        SimulationSessionResponse.from(startSimulationUseCase.start(user.userSeq, request.toCommand()))
}
