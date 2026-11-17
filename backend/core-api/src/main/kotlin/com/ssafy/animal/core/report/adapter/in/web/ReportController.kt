package com.ssafy.animal.core.report.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.report.application.port.`in`.ReportCommand
import com.ssafy.animal.core.report.application.port.`in`.ReportReceipt
import com.ssafy.animal.core.report.application.port.`in`.ReportUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "신고", description = "회원·실종/발견 게시글·댓글 신고 접수. 접수만 하고 조회·심사 API는 없다")
@RestController
class ReportController(
    private val reportUseCase: ReportUseCase,
) {

    /** 15.1 */
    @Operation(
        summary = "신고 접수",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 대상 5종: `USER`(일반 회원), `MISSING`·`FOUND`(게시글), `MISSING_COMMENT`·`FOUND_COMMENT`(댓글). 보호동물 게시글·보호소 계정은 신고할 수 없다.\n" +
            "- 댓글 신고는 댓글 DTO의 `commentType`을 그대로 `targetType`에 넣는다. 채팅 상대 신고는 `USER`.\n" +
            "- 사유는 선택형 `reasonCode`만 받는다(자유 입력 없음). 대상별 허용 값:\n" +
            "  - 게시글(`MISSING`·`FOUND`): `FALSE_INFO`(허위 정보), `SPAM_AD`(스팸·광고), `OFFENSIVE`(욕설·불쾌한 내용), `OTHER`(기타)\n" +
            "  - 회원·댓글(`USER`·`MISSING_COMMENT`·`FOUND_COMMENT`): `HARASSMENT`(욕설·비방·괴롭힘), `SPAM_AD`(스팸·광고), `OBSCENE`(음란·부적절), `OTHER`(기타)\n" +
            "- 검사 순서: 대상 종류 → 사유 누락 → 사유 값 → 대상·사유 조합 → 대상 존재 → 중복.\n" +
            "- 같은 대상은 회원당 한 번만 신고할 수 있다(409). 사유를 바꿔도 다시 접수되지 않는다.\n" +
            "- 삭제된 게시글·댓글, 작성자가 탈퇴한 게시글, 탈퇴한 회원은 404.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "접수 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ReportReceiptSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "접수 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"reportSeq":601,"status":"PENDING"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "targetType·targetSeq 누락, 신고할 수 없는 대상(5종 밖·보호소 계정), 사유 누락, 정의되지 않은 사유, 대상과 맞지 않는 사유, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "필수값 누락",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetType","reason":"공백일 수 없습니다"},{"field":"targetSeq","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "신고할 수 없는 대상",
                            value = """{"status":400,"code":"REPORT_TARGET_NOT_ALLOWED","message":"신고할 수 없는 대상입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "사유 누락",
                            value = """{"status":400,"code":"REPORT_REASON_REQUIRED","message":"신고 사유를 선택해 주세요.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "정의되지 않은 사유",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"reasonCode","reason":"정의되지 않은 사유입니다: RUDE"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "대상과 맞지 않는 사유",
                            value = """{"status":400,"code":"REPORT_REASON_NOT_ALLOWED","message":"이 대상에는 선택할 수 없는 신고 사유입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
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
            description = "대상이 없음·삭제됨, 작성자가 탈퇴한 게시글, 또는 탈퇴한 회원",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "REPORT_TARGET_NOT_FOUND",
                            value = """{"status":404,"code":"REPORT_TARGET_NOT_FOUND","message":"신고 대상을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 신고한 대상. 앱은 \"이미 신고함\"으로 처리한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "REPORT_ALREADY_EXISTS",
                            value = """{"status":409,"code":"REPORT_ALREADY_EXISTS","message":"이미 신고한 대상입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/api/core/v1/reports")
    @ResponseStatus(HttpStatus.CREATED)
    fun report(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: ReportRequest,
    ) = reportUseCase.report(user.userSeq, ReportCommand(request.targetType!!, request.targetSeq!!, request.reasonCode))
}

/** reasonCode 누락은 REPORT_REASON_REQUIRED라 Bean Validation으로 막지 않는다 */
data class ReportRequest(
    @field:Schema(
        description = "신고 대상 종류. USER: 일반 회원, MISSING: 실종 신고 게시글, FOUND: 발견 신고 게시글, MISSING_COMMENT: 실종 신고 댓글, FOUND_COMMENT: 발견 신고 댓글",
        example = "MISSING_COMMENT",
    )
    @field:NotBlank
    val targetType: String?,
    @field:Schema(description = "신고 대상 번호(회원이면 userSeq, 게시글·댓글이면 각 번호)", example = "44")
    @field:NotNull
    val targetSeq: Long?,
    @field:Schema(
        description = "신고 사유 코드. FALSE_INFO·OFFENSIVE는 게시글만, HARASSMENT·OBSCENE은 회원·댓글만, SPAM_AD·OTHER는 모두 가능. 누락·공백이면 400 REPORT_REASON_REQUIRED",
        example = "HARASSMENT",
    )
    val reasonCode: String?,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ReportReceiptSuccessSchema : SuccessResponseSchema<ReportReceipt>()
