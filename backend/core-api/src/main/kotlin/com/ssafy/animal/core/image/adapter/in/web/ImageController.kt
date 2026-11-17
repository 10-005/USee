package com.ssafy.animal.core.image.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "이미지 업로드",
    description = "S3 presigned PUT으로 사진을 올리고 확정해 imageSeq를 받는다. 순서: 업로드 주소 발급 → S3 PUT → 이미지 확정 → 연결 API",
)
@RestController
@RequestMapping("/api/core/v1/images")
class ImageController(
    private val imageUseCase: ImageUseCase,
) {

    /** 4.1 presigned PUT 주소 발급 */
    @Operation(
        summary = "업로드 주소 발급",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- JPG/JPEG(`image/jpeg`)·PNG(`image/png`)만, 파일당 최대 10MB(10485760바이트).\n" +
            "- 저장 키(`imageKey`)는 서버가 만든다(`images/{yyyy}/{MM}/{uuid}.{jpg|png}`, 확장자는 contentType으로 정함).\n" +
            "- 받은 `uploadUrl`로 S3에 직접 `PUT`한다. 서명에 묶인 헤더 4개를 그대로 보내야 하며, 하나라도 다르면 S3가 403으로 거부한다.\n" +
            "  - `Content-Type`: 요청의 contentType\n" +
            "  - `Content-Length`: 요청의 byteSize\n" +
            "  - `x-amz-tagging: upload=pending`\n" +
            "  - `If-None-Match: *`\n" +
            "- `uploadUrl`과 확정 대기 정보는 `expiresInSeconds`(600초) 동안 유효하다. 지나면 이 API부터 다시 호출한다.\n" +
            "- 사진을 고르면 바로 발급·PUT까지 하고, 등록·전송 버튼을 누를 때 이미지 확정(`POST /api/core/v1/images`)을 호출한다.\n" +
            "- 확정하지 않은 객체는 버킷 수명주기 규칙이 자동으로 지운다. 별도 취소 API는 없다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "발급 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UploadUrlSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "발급 성공",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"uploadUrl":"https://animal-bucket.s3.ap-northeast-2.amazonaws.com/images/2026/11/3f1c2a9e.jpg?X-Amz-Signature=...","imageKey":"images/2026/11/3f1c2a9e.jpg","expiresInSeconds":600},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "contentType 누락·공백, byteSize 누락·0 이하, fileName 255자 초과, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = INVALID_UPLOAD_URL_INPUT),
                        ExampleObject(name = "byteSize 0 이하", value = INVALID_BYTE_SIZE),
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
            responseCode = "413",
            description = "byteSize가 10MB 초과. 사진을 줄여 다시 요청한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_TOO_LARGE", value = IMAGE_TOO_LARGE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "415",
            description = "contentType이 image/jpeg·image/png가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_UNSUPPORTED_TYPE", value = IMAGE_UNSUPPORTED_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "503",
            description = "서버의 저장소(S3) 설정이 비어 있음. 잠시 후 재시도한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMON_SERVICE_UNAVAILABLE", value = FailExamples.COMMON_SERVICE_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping("/upload-urls")
    fun issueUploadUrl(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: UploadUrlRequest,
    ) = UploadUrlResponse.from(imageUseCase.issueUploadUrl(request.toCommand(user.userSeq)))

    /** 4.2 업로드한 이미지 확정 */
    @Operation(
        summary = "이미지 확정",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- S3 PUT을 끝낸 `imageKey`를 확정해 `imageSeq`를 받는다. 이 imageSeq로 게시물·반려동물·프로필·댓글·채팅 등 연결 API를 호출한다.\n" +
            "- 업로드 주소를 발급받은 본인만, 발급 후 600초 안에 확정할 수 있다.\n" +
            "- 클라이언트가 보낸 값은 믿지 않고 S3에 실제로 올라간 객체의 크기·MIME을 다시 검사한다. MIME은 발급 때의 contentType과도 같아야 한다.\n" +
            "- 한 imageKey는 한 번만 확정된다. 같은 키로 다시 호출하면 404다.\n" +
            "- 연결 API는 본인이 확정한 이미지만 받는다(아니면 403 `IMAGE_NOT_OWNED`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "확정 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ImageConfirmSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "확정 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"이미지가 등록되었습니다.","data":{"imageSeq":1001,"imageUrl":"https://cdn.example.com/images/2026/11/3f1c2a9e.jpg","mimeType":"image/jpeg","byteSize":1234567},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "imageKey 누락·공백·512자 초과, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "imageKey 누락", value = INVALID_IMAGE_KEY_INPUT),
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
            responseCode = "404",
            description = "발급 정보가 없음(600초 만료, 다른 회원이 발급, 이미 확정됨) 또는 S3에 객체가 없음(PUT 전·실패). 업로드 주소 발급부터 다시 한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "413",
            description = "S3에 올라간 실제 파일이 10MB 초과",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_TOO_LARGE", value = IMAGE_TOO_LARGE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "415",
            description = "S3에 올라간 실제 MIME이 JPG·PNG가 아니거나 발급 때의 contentType과 다름",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "IMAGE_UNSUPPORTED_TYPE", value = IMAGE_UNSUPPORTED_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "503",
            description = "서버의 저장소(S3) 설정이 비어 있음. 잠시 후 재시도한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMON_SERVICE_UNAVAILABLE", value = FailExamples.COMMON_SERVICE_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("이미지가 등록되었습니다.")
    fun confirm(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: ConfirmImageRequest,
    ) = ImageResponse.from(imageUseCase.confirm(user.userSeq, request.imageKey!!))

    private companion object {
        // 문서 예시 전용
        const val INVALID_UPLOAD_URL_INPUT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"contentType","reason":"공백일 수 없습니다"},{"field":"byteSize","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_BYTE_SIZE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"byteSize","reason":"0보다 커야 합니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_IMAGE_KEY_INPUT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"imageKey","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_TOO_LARGE =
            """{"status":413,"code":"IMAGE_TOO_LARGE","message":"이미지는 10MB 이하만 올릴 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_UNSUPPORTED_TYPE =
            """{"status":415,"code":"IMAGE_UNSUPPORTED_TYPE","message":"JPG, PNG 이미지만 올릴 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
