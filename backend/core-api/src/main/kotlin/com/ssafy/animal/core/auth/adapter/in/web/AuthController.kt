package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.core.auth.application.port.`in`.AuthUseCase
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** verify·register·refresh는 비인증 경로(application.yml security.permit-paths), logout은 로그인 필요 */
@Tag(name = "인증", description = "카카오 로그인·회원가입, 토큰 갱신, 로그아웃. 보호소 로그인은 '보호소 로그인' 태그")
@RestController
@RequestMapping("/api/core/v1/auth")
class AuthController(
    private val authUseCase: AuthUseCase,
    private val kakaoRegisterUseCase: KakaoRegisterUseCase,
) {

    @Operation(
        summary = "카카오 로그인",
        description = "- 🔓 인증 불필요\n" +
            "- 앱이 카카오 SDK로 받은 access token을 카카오에 검증해 회원을 찾는다.\n" +
            "- 기존 회원: `requiresRegistration=false`와 토큰·회원 정보를 준다. `registrationToken`은 null.\n" +
            "- 미가입: `requiresRegistration=true`와 `registrationToken`만 준다. 토큰·`user`는 null. 회원 행은 아직 만들지 않는다.\n" +
            "- `registrationToken`은 10분 유효. 만료되면 이 API를 다시 호출한다.\n" +
            "- 카카오 응답이 3초 안에 오지 않으면 503.\n" +
            "- 다음 호출: 미가입이면 `POST /api/core/v1/auth/kakao/register`. 기존 회원이면 받은 accessToken으로 다른 API 호출.",
    )
    @SecurityRequirements
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "검증 성공. 기존 회원이면 로그인, 미가입이면 가입 컨텍스트 발급",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = KakaoVerifySuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "기존 회원 로그인",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"requiresRegistration":false,"registrationToken":null,"accessToken":"jwt-access-token","refreshToken":"jwt-refresh-token","tokenType":"Bearer","expiresIn":3600,"user":{"userSeq":17,"role":"USER","nickname":"반려가족"}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "미가입(회원가입 필요)",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"requiresRegistration":true,"registrationToken":"short-lived-registration-context","accessToken":null,"refreshToken":null,"tokenType":null,"expiresIn":null,"user":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "kakaoAccessToken 누락·공백, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "kakaoAccessToken 누락", value = INVALID_KAKAO_ACCESS_TOKEN_INPUT),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "카카오 토큰이 만료·무효이거나 다른 앱에서 발급됨. 카카오 로그인부터 다시 한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_INVALID_KAKAO_TOKEN", value = AUTH_INVALID_KAKAO_TOKEN)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이 카카오 계정이 일반 회원이 아닌 계정과 연결돼 있음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_KAKAO_ACCOUNT_CONFLICT", value = AUTH_KAKAO_ACCOUNT_CONFLICT)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "503",
            description = "카카오 API 장애·시간 초과(3초). 잠시 후 재시도한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMON_SERVICE_UNAVAILABLE", value = FailExamples.COMMON_SERVICE_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping("/kakao/verify")
    fun verifyKakao(@Valid @RequestBody request: KakaoVerifyRequest) =
        KakaoVerifyResponse.from(authUseCase.verifyKakao(request.kakaoAccessToken!!))

    @Operation(
        summary = "카카오 회원가입",
        description = "- 🔓 인증 불필요. 대신 카카오 로그인(2.1)에서 받은 `registrationToken`이 필요하다.\n" +
            "- 필수 정보 4개(닉네임·이름·생년월일·전화번호)를 받아 일반 회원(USER)을 만들고 바로 로그인 토큰을 준다.\n" +
            "- 검사 순서: 가입 컨텍스트 → 필수값 누락 → 형식 → 카카오 계정 중복 → 닉네임 중복 → 전화번호 중복.\n" +
            "- 실패해도 `registrationToken`은 남아 있어 10분 안에는 값을 고쳐 다시 보낼 수 있다. 성공하면 즉시 폐기된다.\n" +
            "- 응답에 `expiresIn`은 없다.\n" +
            "- 프로필 사진은 가입 후 프로필 수정 API로 설정한다.",
    )
    @SecurityRequirements
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "가입 완료, 토큰 발급",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = KakaoRegisterSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "가입 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":17,"role":"USER","nickname":"반려가족","accessToken":"jwt-access-token","refreshToken":"jwt-refresh-token","tokenType":"Bearer"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락, 형식 오류, 또는 본문을 읽을 수 없음(birthDate 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "필수값 누락",
                            value = """{"status":400,"code":"USER_REQUIRED_PROFILE_MISSING","message":"필수 회원 정보가 없습니다.","data":null,"errors":[{"field":"name","reason":"필수 입력값입니다."},{"field":"phone","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "형식 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"nickname","reason":"닉네임은 2~10자의 한글·영문·숫자와 . _만 쓸 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "registrationToken이 없거나 만료(10분)·이미 사용됨. 카카오 로그인(2.1)부터 다시 한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "AUTH_INVALID_REGISTRATION_CONTEXT",
                            value = """{"status":401,"code":"AUTH_INVALID_REGISTRATION_CONTEXT","message":"가입 정보가 없거나 만료되었습니다. 카카오 로그인을 다시 해 주세요.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 가입된 카카오 계정, 또는 닉네임·전화번호 중복. 중복 항목만 고쳐 다시 보낸다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "이미 가입된 카카오 계정", value = AUTH_KAKAO_ACCOUNT_CONFLICT),
                        ExampleObject(
                            name = "닉네임 중복",
                            value = """{"status":409,"code":"USER_DUPLICATE_NICKNAME","message":"이미 사용 중인 닉네임입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "전화번호 중복",
                            value = """{"status":409,"code":"USER_DUPLICATE_PHONE","message":"이미 사용 중인 전화번호입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/kakao/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerKakao(@RequestBody request: KakaoRegisterRequest) =
        KakaoRegisterResponse.from(kakaoRegisterUseCase.register(request.toCommand()))

    @Operation(
        summary = "토큰 갱신",
        description = "- 🔓 인증 불필요(Authorization 헤더 대신 본문의 refresh 토큰으로 확인)\n" +
            "- access 토큰이 만료(401 `AUTH_TOKEN_EXPIRED`)되면 호출한다.\n" +
            "- rotation: 성공하면 요청에 쓴 refresh 토큰은 즉시 무효가 되고 새 access·refresh 토큰을 준다. 둘 다 저장한다.\n" +
            "- 이미 쓴 refresh 토큰을 다시 보내면 탈취로 보고 이 회원의 모든 기기 refresh 토큰을 폐기한다(401).\n" +
            "- 그러므로 갱신 요청은 한 번에 하나만 보낸다. 동시에 같은 토큰으로 두 번 보내면 두 번째가 전체 로그아웃을 일으킨다.\n" +
            "- 보호소 계정은 갱신 후에도 같은 보호소(shelterSeq) 권한을 유지한다.\n" +
            "- 401을 받으면 재시도하지 말고 재로그인한다.",
    )
    @SecurityRequirements
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "갱신 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = TokenSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "갱신 성공",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"accessToken":"jwt-access-token","refreshToken":"jwt-refresh-token","tokenType":"Bearer","expiresIn":3600},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "refreshToken 누락·공백, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "refreshToken 누락", value = INVALID_REFRESH_TOKEN_INPUT),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "refresh 토큰의 서명·만료 오류, 이미 사용한 토큰 재사용(전체 폐기), 탈퇴한 회원. 재로그인한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_INVALID_REFRESH_TOKEN", value = AUTH_INVALID_REFRESH_TOKEN)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "403",
            description = "정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest) =
        TokenResponse.from(authUseCase.refresh(request.refreshToken!!))

    @Operation(
        summary = "로그아웃",
        description = "- 로그인 필요\n" +
            "- 이 기기의 refresh 토큰만 폐기한다. 다른 기기 로그인은 유지된다.\n" +
            "- access 토큰은 폐기하지 않는다(만료까지 유효). 앱이 직접 지운다.\n" +
            "- 멱등: refresh 토큰이 이미 폐기·만료됐거나 다른 회원의 토큰이어도 204로 끝난다(다른 회원 토큰은 건드리지 않음).\n" +
            "- `fcmToken`을 보내면 이 기기의 푸시 알림만 끈다. 생략하면 푸시 설정은 그대로다.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "로그아웃 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "refreshToken 누락·공백, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "refreshToken 누락", value = INVALID_REFRESH_TOKEN_INPUT),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도, 그 외에는 앱의 토큰만 지운다",
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
            description = "정지된 계정. 앱의 토큰만 지운다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: LogoutRequest,
    ) {
        authUseCase.logout(user.userSeq, request.refreshToken!!, request.fcmToken)
    }

    private companion object {
        // 문서 예시 전용
        const val INVALID_KAKAO_ACCESS_TOKEN_INPUT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"kakaoAccessToken","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_REFRESH_TOKEN_INPUT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"refreshToken","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val AUTH_INVALID_KAKAO_TOKEN =
            """{"status":401,"code":"AUTH_INVALID_KAKAO_TOKEN","message":"유효하지 않은 카카오 인증 정보입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val AUTH_INVALID_REFRESH_TOKEN =
            """{"status":401,"code":"AUTH_INVALID_REFRESH_TOKEN","message":"유효하지 않은 refresh 토큰입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val AUTH_KAKAO_ACCOUNT_CONFLICT =
            """{"status":409,"code":"AUTH_KAKAO_ACCOUNT_CONFLICT","message":"이미 다른 계정과 연결된 카카오 계정입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
