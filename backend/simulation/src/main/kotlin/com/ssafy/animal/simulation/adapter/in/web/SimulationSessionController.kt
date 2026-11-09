package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import com.ssafy.animal.simulation.domain.SessionStatus
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simulation/v1/simulations")
class SimulationSessionController(
    private val startSimulationUseCase: StartSimulationUseCase,
    private val sessionQueryUseCase: SimulationSessionQueryUseCase,
) {

    /** 18.1 세션 시작. 품종·가상 이름·예상 비용으로 시작하고 실제 보호동물과는 연결하지 않는다(Q482) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    fun start(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: StartSimulationRequest) =
        SimulationSessionResponse.from(startSimulationUseCase.start(user.userSeq, request.toCommand()))

    /** 18.2 진행 중 세션(스탯 지연 계산 반영). 없으면 data:null */
    @GetMapping("/me/active")
    @PreAuthorize("hasRole('USER')")
    fun getActive(@AuthenticationPrincipal user: AuthUser): SimulationSessionResponse? =
        sessionQueryUseCase.getActive(user.userSeq)?.let(SimulationSessionResponse::from)

    /** 18.3 내 세션 목록(종료 포함). 필터 status, 정렬 startedDttm(기본 desc)·endedDttm */
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun getMySessions(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) status: SessionStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(sessionQueryUseCase.getMySessions(user.userSeq, status, pageable).map(SimulationSessionSummaryResponse::from))

    /** 18.4 세션 상세. 본인만 보며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @GetMapping("/{sessionSeq}")
    fun getDetail(@AuthenticationPrincipal user: AuthUser, @PathVariable sessionSeq: Long) =
        SimulationSessionResponse.from(sessionQueryUseCase.getDetail(user.userSeq, sessionSeq))
}
