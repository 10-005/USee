package com.ssafy.animal.core.report.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.report.application.port.`in`.ReportCommand
import com.ssafy.animal.core.report.application.port.`in`.ReportUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 15장 신고. 로그인(USER·SHELTER) */
@RestController
class ReportController(
    private val reportUseCase: ReportUseCase,
) {

    /** 15.1 */
    @PostMapping("/api/core/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    fun report(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: ReportRequest) =
        reportUseCase.report(user.userSeq, ReportCommand(request.targetType!!, request.targetSeq!!, request.reasonCode))
}

/** reasonCode 누락은 REPORT_REASON_REQUIRED라 Bean Validation으로 막지 않는다 */
data class ReportRequest(
    @field:NotBlank
    val targetType: String?,
    @field:NotNull
    val targetSeq: Long?,
    val reasonCode: String?,
)
