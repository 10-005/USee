package com.ssafy.animal.core.bookmark.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.bookmark.application.port.`in`.AddedBookmark
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkItem
import com.ssafy.animal.core.bookmark.application.port.`in`.BookmarkUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 16장 북마크(USER 전용, Q452 경로) */
@Tag(name = "북마크", description = "일반 회원(USER)이 실종·발견 신고와 보호동물 게시물을 저장한다")
@RestController
@RequestMapping("/api/core/v1/users/me/bookmarks")
@PreAuthorize("hasRole('USER')")
class BookmarkController(
    private val bookmarkUseCase: BookmarkUseCase,
) {

    /** 16.1 필터 targetType, 정렬 regDttm만 */
    @Operation(
        summary = "내 북마크 목록",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- `targetType`을 생략하면 세 종류(MISSING·FOUND·SHELTER_ANIMAL)를 함께 준다.\n" +
            "- 정렬 허용 필드: `regDttm`(북마크한 시각) 하나만. 기본 `regDttm,desc`. size 기본 20.\n" +
            "- 원본 게시물이 삭제됐거나 신고 작성자가 탈퇴하면 목록에서 빠진다(북마크 행은 남음). 신고가 CLOSED거나 보호동물이 ADOPTED여도 남는다.\n" +
            "- 없으면 빈 `content`로 200.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = BookmarkPageSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "목록",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"bookmarkSeq":701,"targetType":"MISSING","targetSeq":101,"regDttm":"2026-11-09T10:15:30+09:00","postSummary":{"classification":{"combinationSeq":88,"speciesSeq":1,"speciesCode":"DOG","speciesName":"개","breedSeq":12,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"},"gender":"MALE","age":"3살","status":"ACTIVE","address":"서울특별시 강남구 역삼동","occurredDttm":"2026-11-08T18:30:00+09:00","representativeImageUrl":"https://cdn.example.com/images/2026/11/3f1c2a9e.jpg"}}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "북마크 없음",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "북마크할 수 없는 targetType(USER 등), targetType 값 오류, 또는 regDttm 밖의 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "북마크할 수 없는 대상", value = NOT_BOOKMARKABLE_TYPE),
                        ExampleObject(name = "targetType 값 오류", value = INVALID_TARGET_TYPE),
                        ExampleObject(
                            name = "정렬 필드 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: targetSeq (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
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
    @GetMapping
    fun list(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대상 종류 필터. MISSING·FOUND·SHELTER_ANIMAL 중 하나. 생략하면 전체", example = "MISSING")
        @RequestParam(required = false) targetType: TargetType?,
        pageable: Pageable,
    ) = bookmarkUseCase.list(user.userSeq, targetType, pageable)

    /** 16.2 */
    @Operation(
        summary = "북마크 등록",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 대상은 MISSING(실종 신고)·FOUND(발견 신고)·SHELTER_ANIMAL(보호동물) 3종.\n" +
            "- 삭제된 게시물, 작성자가 탈퇴한 신고는 404 `COMMON_NOT_FOUND`.\n" +
            "- 멱등 아님: 이미 저장한 게시물은 409 `USER_BOOKMARK_ALREADY_EXISTS`. 앱은 \"이미 저장됨\"으로 처리한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "등록 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AddedBookmarkSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "등록 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"bookmarkSeq":701,"targetType":"MISSING","targetSeq":101},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "targetType·targetSeq 누락, 북마크할 수 없는 targetType(USER 등), 또는 본문을 읽을 수 없음(정의되지 않은 targetType 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "필수값 누락",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetType","reason":"널이어서는 안됩니다"},{"field":"targetSeq","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "북마크할 수 없는 대상", value = NOT_BOOKMARKABLE_TYPE),
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
        ApiResponse(
            responseCode = "404",
            description = "대상 게시물이 없음·삭제됨, 또는 신고 작성자가 탈퇴함",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "COMMON_NOT_FOUND",
                            value = """{"status":404,"code":"COMMON_NOT_FOUND","message":"요청한 리소스를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 저장한 게시물. 앱은 \"이미 저장됨\"으로 처리한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "USER_BOOKMARK_ALREADY_EXISTS",
                            value = """{"status":409,"code":"USER_BOOKMARK_ALREADY_EXISTS","message":"이미 저장한 게시물입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: AddBookmarkRequest,
    ) = bookmarkUseCase.add(user.userSeq, request.targetType!!, request.targetSeq!!)

    /** 16.3 */
    @Operation(
        summary = "북마크 해제",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 본인 북마크를 물리 삭제한다. 원본 게시물이 삭제된 뒤에도 해제할 수 있다.\n" +
            "- 멱등 아님: 저장하지 않은(이미 해제한) 북마크는 404 `USER_BOOKMARK_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "해제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "북마크할 수 없는 targetType(USER 등), targetType 값 오류, 또는 targetSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "북마크할 수 없는 대상", value = NOT_BOOKMARKABLE_TYPE),
                        ExampleObject(name = "targetType 값 오류", value = INVALID_TARGET_TYPE),
                        ExampleObject(
                            name = "targetSeq 타입 오류",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
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
        ApiResponse(
            responseCode = "404",
            description = "저장하지 않은 북마크",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "USER_BOOKMARK_NOT_FOUND",
                            value = """{"status":404,"code":"USER_BOOKMARK_NOT_FOUND","message":"북마크를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @DeleteMapping("/{targetType}/{targetSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "대상 종류. MISSING·FOUND·SHELTER_ANIMAL 중 하나", example = "MISSING") @PathVariable targetType: TargetType,
        @Parameter(description = "대상 게시물 번호", example = "101") @PathVariable targetSeq: Long,
    ) {
        bookmarkUseCase.remove(user.userSeq, targetType, targetSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val NOT_BOOKMARKABLE_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetType","reason":"북마크할 수 없는 대상입니다: USER"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_TARGET_TYPE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetType","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class AddBookmarkRequest(
    @field:Schema(description = "대상 종류. MISSING: 실종 신고, FOUND: 발견 신고, SHELTER_ANIMAL: 보호동물. 그 밖의 값은 400", example = "MISSING")
    @field:NotNull
    val targetType: TargetType?,
    @field:Schema(description = "대상 게시물 번호", example = "101")
    @field:NotNull
    val targetSeq: Long?,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class BookmarkPageSuccessSchema : SuccessResponseSchema<PageResponse<BookmarkItem>>()
abstract class AddedBookmarkSuccessSchema : SuccessResponseSchema<AddedBookmark>()
