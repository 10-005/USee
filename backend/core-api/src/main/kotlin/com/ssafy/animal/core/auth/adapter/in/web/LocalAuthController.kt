package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginResult
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 2.2 비인증 경로(application.yml security.permit-paths) */
@Tag(name = "보호소 로그인", description = "사전 발급된 보호소(SHELTER) 계정의 아이디·비밀번호 로그인")
@RestController
@RequestMapping("/api/core/v1/auth/local")
class LocalAuthController(
    private val localLoginUseCase: LocalLoginUseCase,
) {

    @Operation(
        summary = "보호소 로그인",
        description = "- 🔓 인증 불필요\n" +
            "- 사전 발급된 보호소(SHELTER) 계정 전용. 일반 회원은 카카오 로그인을 쓴다. 회원가입·비밀번호 재설정 API는 없다.\n" +
            "- 없는 ID, 틀린 비밀번호, 탈퇴 계정, 보호소가 아닌 계정은 모두 같은 401 `AUTH_INVALID_CREDENTIALS`로 응답한다(계정 존재 여부를 드러내지 않음).\n" +
            "- 실패 제한: 같은 `loginId`로 5회 연속 실패하면 15분 동안 429 `AUTH_LOGIN_LOCKED`. 없는 ID도 같은 규칙으로 센다.\n" +
            "- 차단 중에는 비밀번호가 맞아도 429다. 차단은 15분 뒤 자동으로 풀리고, 차단 중 시도는 차단 시간을 늘리지 않는다.\n" +
            "- 실패 횟수는 마지막 실패 후 15분이 지나면 사라진다. 로그인에 성공하면 바로 초기화된다. 400(입력 오류)은 횟수에 넣지 않는다.\n" +
            "- 정지 계정은 비밀번호가 맞으면 403 `AUTH_ACCOUNT_UNAVAILABLE`(실패 횟수에 넣지 않음).\n" +
            "- 응답의 `shelterSeq`는 항상 있다. 같은 값이 access 토큰에도 들어 있어 보호소 API 권한에 쓰인다.\n" +
            "- 다음 호출: 받은 accessToken으로 보호소 API 호출, 만료되면 `POST /api/core/v1/auth/refresh`. 보호소 정보는 `GET /api/core/v1/shelters/me`.",
    )
    @SecurityRequirements
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "로그인 성공, 토큰 발급",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = LocalLoginSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "로그인 성공",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"accessToken":"jwt-access-token","refreshToken":"jwt-refresh-token","tokenType":"Bearer","expiresIn":3600,"role":"SHELTER","userSeq":55,"shelterSeq":7},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "loginId·password 누락·공백, loginId 255자 초과, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = INVALID_LOCAL_LOGIN_INPUT),
                        ExampleObject(name = "loginId 길이 초과", value = INVALID_LOGIN_ID_SIZE),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "ID·비밀번호 불일치(없는 ID, 탈퇴·보호소 아닌 계정 포함). 실패 횟수에 들어간다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_INVALID_CREDENTIALS", value = AUTH_INVALID_CREDENTIALS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정, 또는 소속 보호소가 연결되지 않은 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "429",
            description = "5회 연속 실패로 15분 차단 중. 비밀번호가 맞아도 거부한다. 15분 뒤 다시 시도한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_LOGIN_LOCKED", value = AUTH_LOGIN_LOCKED)],
                ),
            ],
        ),
    )
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LocalLoginRequest) =
        LocalLoginResponse.from(localLoginUseCase.login(request.loginId!!, request.password!!))

    private companion object {
        // 문서 예시 전용
        const val INVALID_LOCAL_LOGIN_INPUT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"loginId","reason":"공백일 수 없습니다"},{"field":"password","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_LOGIN_ID_SIZE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"loginId","reason":"크기가 0에서 255 사이여야 합니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val AUTH_INVALID_CREDENTIALS =
            """{"status":401,"code":"AUTH_INVALID_CREDENTIALS","message":"아이디 또는 비밀번호가 올바르지 않습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val AUTH_LOGIN_LOCKED =
            """{"status":429,"code":"AUTH_LOGIN_LOCKED","message":"로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class LocalLoginRequest(
    // 길이 상한: Redis 키(auth:login-fail:{loginId})에 그대로 들어가므로 login_identifier 컬럼 길이로 막는다
    @field:Schema(description = "보호소 로그인 ID. 최대 255자", example = "shelter01")
    @field:NotBlank @field:Size(max = 255)
    val loginId: String?,
    @field:Schema(description = "비밀번호", example = "password123!")
    @field:NotBlank
    val password: String?,
)

data class LocalLoginResponse(
    @field:Schema(description = "access 토큰", example = "jwt-access-token")
    val accessToken: String,
    @field:Schema(description = "refresh 토큰", example = "jwt-refresh-token")
    val refreshToken: String,
    @field:Schema(description = "항상 Bearer", example = "Bearer")
    val tokenType: String,
    @field:Schema(description = "access 토큰 유효 시간(초)", example = "3600")
    val expiresIn: Long,
    @field:Schema(description = "역할. 보호소 로그인은 항상 SHELTER(보호소 담당자)", example = "SHELTER")
    val role: Role,
    @field:Schema(description = "보호소 담당자 회원 번호", example = "55")
    val userSeq: Long,
    @field:Schema(description = "소속 보호소 번호. 항상 있다", example = "7")
    val shelterSeq: Long,
) {
    companion object {
        fun from(result: LocalLoginResult) = LocalLoginResponse(
            accessToken = result.tokens.accessToken,
            refreshToken = result.tokens.refreshToken,
            tokenType = "Bearer",
            expiresIn = result.tokens.expiresIn,
            role = Role.SHELTER,
            userSeq = result.userSeq,
            shelterSeq = result.shelterSeq,
        )
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class LocalLoginSuccessSchema : SuccessResponseSchema<LocalLoginResponse>()
