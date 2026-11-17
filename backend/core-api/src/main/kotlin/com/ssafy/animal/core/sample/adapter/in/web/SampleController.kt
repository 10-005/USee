package com.ssafy.animal.core.sample.adapter.`in`.web

import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.sample.application.port.`in`.SampleUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** DTO만 반환한다. 공통 봉투(ApiResponse)는 common의 ApiResponseBodyAdvice가 씌운다. */
@Tag(name = "샘플", description = "구조 예시용 CRUD. 실제 기능 아님")
@RestController
@RequestMapping("/api/core/v1/samples")
class SampleController(
    private val sampleUseCase: SampleUseCase,
) {

    @Operation(
        summary = "샘플 생성",
        description = "- 로그인 필요\n" +
            "- 성공 메시지는 `샘플이 생성되었습니다.`",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "생성 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SampleSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "생성 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"샘플이 생성되었습니다.","data":{"sampleSeq":1,"name":"첫 샘플","description":"예시 설명","regDttm":"2026-11-09T10:00:00+09:00","modDttm":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "name 누락·공백·길이 초과, description 길이 초과, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "name 공백", value = INVALID_NAME_BLANK),
                        ExampleObject(name = "name 길이 초과", value = INVALID_NAME_SIZE),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정",
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
            description = "정지된 계정",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("샘플이 생성되었습니다.")
    fun create(@Valid @RequestBody request: CreateSampleRequest) =
        SampleResponse.from(sampleUseCase.create(request.toCommand()))

    @Operation(summary = "샘플 단건 조회", description = "- 로그인 필요\n")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SampleSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = SAMPLE_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "sampleSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "sampleSeq 타입 오류", value = INVALID_SAMPLE_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효",
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
            description = "없는 샘플",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SAMPLE_NOT_FOUND", value = SAMPLE_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{sampleSeq}")
    fun get(@Parameter(description = "샘플 번호", example = "1") @PathVariable sampleSeq: Long) =
        SampleResponse.from(sampleUseCase.get(sampleSeq))

    @Operation(
        summary = "샘플 목록",
        description = "- 로그인 필요\n" +
            "- `size` 기본 20.\n" +
            "- `sort` 허용 필드: `regDttm`, `name`. 기본 `regDttm,desc`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SamplePageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"sampleSeq":1,"name":"첫 샘플","description":"예시 설명","regDttm":"2026-11-09T10:00:00+09:00","modDttm":null}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용하지 않는 sort 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "정렬 필드 오류", value = INVALID_SORT)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효",
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
    )
    @GetMapping
    fun getPage(@PageableDefault(size = 20) pageable: Pageable) =
        PageResponse.of(sampleUseCase.getPage(pageable).map(SampleResponse::from))

    @Operation(
        summary = "샘플 삭제",
        description = "- 로그인 필요\n" +
            "- 성공은 204가 아니라 200이고 `data`는 null(봉투 있음).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "삭제 완료. data는 null",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SampleNoDataSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "삭제 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "sampleSeq가 정수가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "sampleSeq 타입 오류", value = INVALID_SAMPLE_SEQ_TYPE)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정",
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
            description = "정지된 계정",
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
            description = "없는 샘플",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SAMPLE_NOT_FOUND", value = SAMPLE_NOT_FOUND)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{sampleSeq}")
    fun delete(@Parameter(description = "샘플 번호", example = "1") @PathVariable sampleSeq: Long) {
        sampleUseCase.delete(sampleSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val SAMPLE_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"sampleSeq":1,"name":"첫 샘플","description":"예시 설명","regDttm":"2026-11-09T10:00:00+09:00","modDttm":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_NAME_BLANK =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"name","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_NAME_SIZE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"name","reason":"크기가 0에서 50 사이여야 합니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SAMPLE_SEQ_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sampleSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SORT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: foo (허용: regDttm, name)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SAMPLE_NOT_FOUND =
            """{"status":404,"code":"SAMPLE_NOT_FOUND","message":"샘플을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
