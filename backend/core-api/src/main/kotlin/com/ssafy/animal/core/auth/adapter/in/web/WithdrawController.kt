package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.core.auth.application.port.`in`.WithdrawUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 명세 2.6. 경로는 /users/me지만 인증·회원 장(2장)에 속해 auth에 둔다. */
@Tag(name = "회원 탈퇴", description = "일반 회원(USER) 탈퇴")
@RestController
class WithdrawController(
    private val withdrawUseCase: WithdrawUseCase,
) {

    @Operation(
        summary = "회원 탈퇴",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 회원·인증 계정을 소프트 삭제한다. 탈퇴 후에는 같은 닉네임·전화번호로 다시 가입할 수 있다.\n" +
            "- 커밋 뒤 이 회원의 모든 기기 refresh 토큰을 폐기하고 `UserDeleted` 이벤트를 발행한다. 이 후처리가 실패해도 탈퇴는 확정(204)이다.\n" +
            "- 탈퇴한 회원의 공개 프로필 조회는 404 `USER_NOT_FOUND`가 된다.\n" +
            "- 서버는 카카오 재인증을 요구하지 않는다. 확인 절차는 앱 화면에서 한다.\n" +
            "- access 토큰은 만료까지 남지만, 이후 쓰기 요청(POST·PATCH·DELETE)은 401 `AUTH_TOKEN_INVALID`다. 이 API를 다시 호출해도 401이다.\n" +
            "- 204를 받으면 앱에 저장한 토큰을 모두 지운다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "탈퇴 완료. 본문 없음"),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 이미 탈퇴한 계정. 만료면 갱신 후 재시도, 그 외에는 앱의 토큰만 지운다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_UNAUTHORIZED", value = FailExamples.AUTH_UNAUTHORIZED),
                        ExampleObject(name = "AUTH_TOKEN_EXPIRED", value = FailExamples.AUTH_TOKEN_EXPIRED),
                        ExampleObject(name = "AUTH_TOKEN_INVALID", value = FailExamples.AUTH_TOKEN_INVALID),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정, 또는 보호소(SHELTER) 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                    ],
                ),
            ],
        ),
    )
    @DeleteMapping("/api/core/v1/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    fun withdraw(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) {
        withdrawUseCase.withdraw(user.userSeq)
    }
}
