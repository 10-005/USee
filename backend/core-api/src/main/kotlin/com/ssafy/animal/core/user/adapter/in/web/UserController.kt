package com.ssafy.animal.core.user.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.core.user.application.port.`in`.UserProfileUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestParam
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import com.ssafy.animal.common.auth.Role

@Tag(name = "사용자 프로필", description = "내 프로필 조회·수정, 다른 회원의 공개 프로필. 탈퇴는 '회원 탈퇴' 태그")
@RestController
@RequestMapping("/api/core/v1/users")
class UserController(
    private val userProfileUseCase: UserProfileUseCase,
) {

    /** 3.1 내 프로필. SHELTER는 9.3을 쓴다(Q451) */
    @Operation(
        summary = "내 프로필 조회",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`. 보호소는 `GET /api/core/v1/shelters/me`를 쓴다)\n" +
            "- 실명·생년월일·전화번호를 포함한 본인 정보를 준다. `shelterSeq`는 없다.\n" +
            "- 프로필 사진이 없으면 `profileImage`는 null.\n" +
            "- 정지 계정도 조회할 수 있고 `accountStatus=SUSPENDED`로 내려온다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MyProfileSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "프로필 사진 있음", value = MY_PROFILE_OK),
                        ExampleObject(
                            name = "프로필 사진 없음",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":17,"role":"USER","nickname":"반려가족","name":"김가람","birthDate":"1997-05-12","phone":"01012345678","profileImage":null,"accountStatus":"ACTIVE"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "보호소(SHELTER) 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun getMe(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) =
        MyProfileResponse.from(userProfileUseCase.getMe(user.userSeq))

    /** 닉네임 중복 확인(명세 외). 가입(2.5)·프로필 수정(3.2) 화면에서 제출 전에 쓴다 */
    @Operation(
        summary = "닉네임 중복 확인",
        description = "- 🔓 로그인 없이 호출할 수 있다(카카오 가입 단계에는 access 토큰이 없음).\n" +
            "- 형식은 가입·수정과 같다: 2~10자, 한글·영문·숫자와 `.` `_`만. 틀리거나 비어 있으면 400.\n" +
            "- 중복 판정도 같다: 탈퇴하지 않은 USER의 닉네임과 대소문자 구분 없이 비교한다.\n" +
            "- USER로 로그인해 호출하면 본인의 현재 닉네임은 `available=true`(3.2에 그대로 보내도 409가 아님).\n" +
            "- 결과는 확인 시점 기준이다. 제출할 때 다른 사람이 먼저 쓰면 2.5·3.2가 409 `USER_DUPLICATE_NICKNAME`을 낸다.",
    )
    @SecurityRequirements
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "형식이 맞음. `available`로 사용 가능 여부",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = NicknameAvailabilitySuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "사용 가능",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"nickname":"반려가족","available":true},"errors":null,"traceId":"8f3a2c1d9e7b4a60"}""",
                        ),
                        ExampleObject(
                            name = "이미 사용 중",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"nickname":"반려가족","available":false},"errors":null,"traceId":"8f3a2c1d9e7b4a60"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "닉네임이 없거나 형식이 틀림. 입력을 고친 뒤 다시 호출",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "형식 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"nickname","reason":"닉네임은 2~10자의 한글·영문·숫자와 . _만 쓸 수 있습니다."}],"traceId":"8f3a2c1d9e7b4a60"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/nickname-availability")
    fun checkNickname(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser?,
        @Parameter(description = "확인할 닉네임. 2~10자, 한글·영문·숫자와 . _만", example = "반려가족", required = true)
        @RequestParam(required = false) nickname: String?,
    ): NicknameAvailabilityResponse {
        val value = nickname.orEmpty()
        val self = user?.takeIf { it.role == Role.USER }?.userSeq
        return NicknameAvailabilityResponse(value, userProfileUseCase.isNicknameAvailable(value, self))
    }

    /** 3.2 내 프로필 부분 갱신 */
    @Operation(
        summary = "내 프로필 수정",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 부분 갱신: 보낸 필드만 바꾸고 생략한 필드는 유지한다.\n" +
            "- `nickname`·`name`·`birthDate`·`phone`은 필수 정보라 null·공백으로 바꿀 수 없다(400).\n" +
            "- `profileImageSeq`: 값이면 교체, null이면 해제. 본인이 확정(`POST /api/core/v1/images`)한 이미지만 쓸 수 있다.\n" +
            "- 교체·해제로 쓰이지 않게 된 이전 사진은 다른 곳에서 참조하지 않을 때만 삭제된다.\n" +
            "- 지금과 같은 닉네임·전화번호를 다시 보내도 중복(409)이 아니다. 닉네임 중복은 대소문자를 구분하지 않는다.\n" +
            "- 검사 순서: null·공백 → 형식 → 닉네임 중복 → 전화번호 중복 → 프로필 사진.\n" +
            "- 응답은 수정된 프로필(내 프로필 조회와 같은 형태).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 성공. 수정된 프로필",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MyProfileSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 성공", value = MY_PROFILE_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수 정보를 null·공백으로 바꾸려 함, 형식 오류, 또는 본문을 읽을 수 없음(birthDate 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "필수 정보 삭제 시도",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"nickname","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "형식 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"nickname","reason":"닉네임은 2~10자의 한글·영문·숫자와 . _만 쓸 수 있습니다."},{"field":"phone","reason":"전화번호는 30자 이하입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            description = "정지된 계정, 보호소(SHELTER) 계정, 또는 본인이 올리지 않은 프로필 사진",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "profileImageSeq에 해당하는 이미지가 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "다른 활성 회원이 쓰는 닉네임·전화번호. 해당 항목만 고쳐 다시 보낸다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
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
    @PatchMapping("/me")
    @PreAuthorize("hasRole('USER')")
    fun updateMe(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @RequestBody request: UpdateMyProfileRequest,
    ) = MyProfileResponse.from(userProfileUseCase.updateMe(user.userSeq, request.toCommand()))

    /** 3.5 공개 프로필 */
    @Operation(
        summary = "공개 프로필 조회",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 다른 회원의 공개 정보만 준다. 실명·생년월일·전화번호는 주지 않는다.\n" +
            "- USER면 `displayName`은 닉네임, `profileImage`는 회원 사진. SHELTER면 보호소명과 보호소 프로필 사진.\n" +
            "- 없는 회원·탈퇴한 회원은 404 `USER_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = PublicProfileSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "일반 회원",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":18,"displayName":"발견도우미","role":"USER","profileImage":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "보호소 계정",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":55,"displayName":"행복 동물보호센터","role":"SHELTER","profileImage":{"imageSeq":2001,"imageUrl":"https://cdn.example.com/images/2026/11/7a8b9c0d.png"}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "userSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "userSeq 타입 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"userSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도, 그 외에는 재로그인",
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
            responseCode = "404",
            description = "없는 회원, 탈퇴한 회원",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "USER_NOT_FOUND",
                            value = """{"status":404,"code":"USER_NOT_FOUND","message":"회원을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @GetMapping("/{userSeq}/profile")
    // 회원 번호는 INTEGER라 Int로 받는다. 범위를 넘으면 타입 변환 실패로 400 COMMON_INVALID_INPUT(500이 나지 않게)
    fun getPublicProfile(@Parameter(description = "조회할 회원 번호", example = "18") @PathVariable userSeq: Int) =
        PublicProfileResponse.from(userProfileUseCase.getPublicProfile(userSeq.toLong()))

    private companion object {
        // 문서 예시 전용
        const val MY_PROFILE_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"userSeq":17,"role":"USER","nickname":"반려가족","name":"김가람","birthDate":"1997-05-12","phone":"01012345678","profileImage":{"imageSeq":1301,"imageUrl":"https://cdn.example.com/images/2026/11/3f1c2a9e.jpg"},"accountStatus":"ACTIVE"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
