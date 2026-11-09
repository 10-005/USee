package com.ssafy.animal.core.comment.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.comment.application.port.`in`.CommentDetail
import com.ssafy.animal.core.comment.application.port.`in`.CommentUseCase
import com.ssafy.animal.core.comment.application.port.`in`.CreateCommentCommand
import com.ssafy.animal.core.comment.application.port.`in`.CreatedComment
import com.ssafy.animal.core.comment.application.port.`in`.UpdateCommentCommand
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
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Optional
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.BusinessException

/** 13장 댓글·답글. 권한은 로그인(USER·SHELTER 모두) */
@Tag(name = "댓글", description = "실종·발견 신고 게시물의 댓글·답글(깊이 1). 게시물 targetType MISSING·FOUND ↔ commentType MISSING_COMMENT·FOUND_COMMENT")
@RestController
@RequestMapping("/api/core/v1/comments")
class CommentController(
    private val commentUseCase: CommentUseCase,
) {

    /** 13.1 루트 댓글 전량 배열(Q480) */
    @Operation(
        summary = "루트 댓글 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 게시물의 루트 댓글을 페이징 없이 전부 배열로 준다. `commentSeq` 내림차순(최신순) 고정. page·size·sort는 받지 않는다.\n" +
            "- 삭제된 루트도 `isDeleted=true`, `body`·`image`=null로 포함한다(답글 자리 유지).\n" +
            "- `targetType`은 MISSING·FOUND만. 다른 값(SHELTER_ANIMAL 등)은 400.\n" +
            "- 게시물이 없거나 삭제됐거나 작성자가 탈퇴했으면 404 `COMMENT_TARGET_NOT_FOUND`.\n" +
            "- 답글은 `replyCount`가 0보다 크면 `GET /api/core/v1/comments/{commentType}/{commentSeq}/replies`로 받는다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "루트 댓글 배열. 댓글이 없으면 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommentListSuccessSchema::class),
                    examples = [ExampleObject(name = "루트 댓글 목록", value = ROOT_LIST_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "targetType·targetSeq 누락, 타입 불일치(없는 enum 값·숫자 아님), 또는 MISSING·FOUND 밖의 targetType",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수 파라미터 누락", value = MISSING_PARAMETER),
                        ExampleObject(name = "타입 불일치", value = TARGET_SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "댓글 대상이 아닌 targetType", value = TARGET_TYPE_NOT_ALLOWED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
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
            description = "게시물 없음·삭제·작성자 탈퇴",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMENT_TARGET_NOT_FOUND", value = COMMENT_TARGET_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping
    fun listRoots(
        @Parameter(description = "게시물 종류. MISSING(실종 신고) 또는 FOUND(발견 신고)", example = "MISSING")
        @RequestParam targetType: TargetType,
        @Parameter(description = "게시물 번호", example = "101")
        @RequestParam targetSeq: Long,
    ) = commentUseCase.listRoots(targetType, targetSeq)

    /** 13.2 답글 전량 배열 */
    @Operation(
        summary = "답글 목록",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 루트 댓글 하나의 답글을 페이징 없이 전부 배열로 준다. `regDttm` 오름차순(동률 `commentSeq` 오름차순) 고정.\n" +
            "- 루트가 삭제됐어도 답글은 조회된다. 삭제된 답글도 `isDeleted=true`로 포함한다.\n" +
            "- `commentType`은 MISSING_COMMENT·FOUND_COMMENT만. 다른 값은 400.\n" +
            "- 댓글이 없거나, 답글을 루트로 지정했거나, 게시물이 삭제·비노출이면 404 `COMMENT_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "답글 배열. 답글이 없으면 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommentListSuccessSchema::class),
                    examples = [ExampleObject(name = "답글 목록", value = REPLY_LIST_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "경로 타입 불일치(없는 enum 값·숫자 아님), 또는 MISSING_COMMENT·FOUND_COMMENT 밖의 commentType",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "타입 불일치", value = COMMENT_TYPE_MISMATCH),
                        ExampleObject(name = "댓글 종류가 아닌 commentType", value = COMMENT_TYPE_NOT_ALLOWED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
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
            description = "루트 댓글 없음, 답글을 루트로 지정, 또는 게시물 삭제·작성자 탈퇴",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMENT_NOT_FOUND", value = COMMENT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{commentType}/{commentSeq}/replies")
    fun listReplies(
        @Parameter(description = "댓글 종류. MISSING_COMMENT 또는 FOUND_COMMENT", example = "MISSING_COMMENT")
        @PathVariable commentType: TargetType,
        @Parameter(description = "루트 댓글 번호", example = "44")
        @PathVariable commentSeq: Long,
    ) = commentUseCase.listReplies(commentType, commentSeq)

    /** 13.6 단건(알림 진입용) */
    @Operation(
        summary = "댓글 단건 조회",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- 알림(COMMENT·COMMENT_REPLY)에서 들어와 해당 댓글을 맨 위에 고정해 보여 줄 때 쓴다. 게시물 상세와 동시에 호출해도 된다.\n" +
            "- `commentType`은 알림의 `targetType`으로 정한다(MISSING → MISSING_COMMENT, FOUND → FOUND_COMMENT).\n" +
            "- 삭제된 댓글도 404가 아니라 200으로 `isDeleted=true`, `body`·`image`=null을 준다.\n" +
            "- 답글이면 루트를 함께 보여 주려면 `parentCommentSeq`로 이 API를 한 번 더 호출한다.\n" +
            "- 고정한 댓글은 13.1 목록에도 다시 나오므로 `commentSeq`로 중복을 제거한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "댓글 단건(삭제된 댓글 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommentDetailSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "댓글", value = DETAIL_SUCCESS),
                        ExampleObject(name = "삭제된 댓글", value = DELETED_DETAIL_SUCCESS),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "경로 타입 불일치(없는 enum 값·숫자 아님), 또는 MISSING_COMMENT·FOUND_COMMENT 밖의 commentType",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "타입 불일치", value = COMMENT_TYPE_MISMATCH),
                        ExampleObject(name = "댓글 종류가 아닌 commentType", value = COMMENT_TYPE_NOT_ALLOWED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 갱신 후 재시도",
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
            description = "댓글 없음, 또는 게시물 삭제·작성자 탈퇴",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMENT_NOT_FOUND", value = COMMENT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{commentType}/{commentSeq}")
    fun get(
        @Parameter(description = "댓글 종류. MISSING_COMMENT 또는 FOUND_COMMENT", example = "MISSING_COMMENT")
        @PathVariable commentType: TargetType,
        @Parameter(description = "댓글 번호", example = "45")
        @PathVariable commentSeq: Long,
    ) = commentUseCase.get(commentType, commentSeq)

    /** 13.3 */
    @Operation(
        summary = "댓글·답글 작성",
        description = "- 로그인 필요(USER·SHELTER)\n" +
            "- `parentCommentSeq`가 없으면 루트 댓글, 있으면 그 루트의 답글이다. 답글의 답글은 없다(깊이 1).\n" +
            "- `body`와 `imageSeq` 중 하나는 있어야 한다. 공백뿐인 `body`는 없는 것으로 본다.\n" +
            "- 검사 순서: targetType → 내용 비어 있음 → 게시물 → 부모 댓글 → 이미지.\n" +
            "- 부모 댓글이 없거나 다른 게시물의 댓글이면 404 `COMMENT_NOT_FOUND`. 부모가 답글이면 400 `COMMENT_DEPTH_EXCEEDED`. 부모가 삭제됐거나 부모 작성자가 탈퇴했으면 409 `COMMENT_PARENT_UNAVAILABLE`.\n" +
            "- `imageSeq`는 본인이 올린 이미지만(4.1 업로드 후 받은 번호).\n" +
            "- 알림: 루트 댓글은 게시물 작성자에게 COMMENT, 답글은 루트 작성자에게만 COMMENT_REPLY. 본인에게는 보내지 않는다.\n" +
            "- 응답은 번호만 준다. 화면 갱신은 13.1·13.2를 다시 부르거나 13.6으로 단건을 받는다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "작성 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommentCreatedSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "루트 댓글",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"commentType":"MISSING_COMMENT","commentSeq":44,"parentCommentSeq":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "답글",
                            value = """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"commentType":"MISSING_COMMENT","commentSeq":45,"parentCommentSeq":44},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "targetType·targetSeq 누락, MISSING·FOUND 밖의 targetType, 본문·사진 둘 다 없음, 답글에 답글, 또는 본문을 읽을 수 없음(없는 enum 값 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "필수값 누락",
                            value = """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetSeq","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "댓글 대상이 아닌 targetType", value = TARGET_TYPE_NOT_ALLOWED),
                        ExampleObject(name = "COMMENT_EMPTY", value = COMMENT_EMPTY),
                        ExampleObject(
                            name = "COMMENT_DEPTH_EXCEEDED",
                            value = """{"status":400,"code":"COMMENT_DEPTH_EXCEEDED","message":"답글에는 답글을 달 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
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
            description = "정지된 계정, 또는 본인이 올리지 않은 이미지. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "게시물 없음·삭제·작성자 탈퇴, 부모 댓글 없음(다른 게시물의 댓글 포함), 또는 이미지 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "COMMENT_TARGET_NOT_FOUND", value = COMMENT_TARGET_NOT_FOUND),
                        ExampleObject(name = "부모 댓글 없음", value = COMMENT_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "부모 댓글이 삭제됐거나 부모 작성자가 탈퇴함. 답글 대신 루트 댓글로 쓴다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(
                            name = "COMMENT_PARENT_UNAVAILABLE",
                            value = """{"status":409,"code":"COMMENT_PARENT_UNAVAILABLE","message":"답글을 달 수 없는 댓글입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreateCommentRequest) =
        commentUseCase.create(user.userSeq, request.toCommand())

    /** 13.4 */
    @Operation(
        summary = "댓글·답글 수정",
        description = "- 로그인 필요. 작성자 본인만\n" +
            "- 부분 수정: 필드를 보내지 않으면 유지, `null`을 보내면 비운다(`imageSeq: null`은 사진 해제).\n" +
            "- 수정 후 본문(공백뿐이면 없음으로 봄)과 사진이 모두 없으면 400 `COMMENT_EMPTY`.\n" +
            "- 새 `imageSeq`는 본인이 올린 이미지만. 바뀌어 더는 참조되지 않는 예전 이미지는 서버가 정리한다.\n" +
            "- 남의 댓글, 삭제된 댓글, 없는 댓글, 게시물이 삭제·비노출이면 모두 404 `COMMENT_NOT_FOUND`.\n" +
            "- 응답은 수정된 댓글 전체(13.1과 같은 형태).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정된 댓글",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CommentDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 완료", value = UPDATED_DETAIL_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "경로 타입 불일치, MISSING_COMMENT·FOUND_COMMENT 밖의 commentType, 수정 후 내용 비어 있음, 또는 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "타입 불일치", value = COMMENT_TYPE_MISMATCH),
                        ExampleObject(name = "댓글 종류가 아닌 commentType", value = COMMENT_TYPE_NOT_ALLOWED),
                        ExampleObject(name = "COMMENT_EMPTY", value = COMMENT_EMPTY),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
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
            description = "정지된 계정, 또는 본인이 올리지 않은 이미지. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "댓글 없음·남의 댓글·삭제된 댓글·게시물 비노출, 또는 이미지 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "COMMENT_NOT_FOUND", value = COMMENT_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
    )
    @PatchMapping("/{commentType}/{commentSeq}")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "댓글 종류. MISSING_COMMENT 또는 FOUND_COMMENT", example = "MISSING_COMMENT")
        @PathVariable commentType: TargetType,
        @Parameter(description = "댓글 번호", example = "45")
        @PathVariable commentSeq: Long,
        @RequestBody request: UpdateCommentRequest,
    ) = commentUseCase.update(user.userSeq, commentType, commentSeq, UpdateCommentCommand(request.body, request.imageSeq))

    /** 13.5 */
    @Operation(
        summary = "댓글·답글 삭제",
        description = "- 로그인 필요. 작성자 본인만\n" +
            "- 소프트 삭제. 목록·단건 조회에는 `isDeleted=true`로 남고 `body`·`image`는 null로 내려간다.\n" +
            "- 루트를 삭제해도 답글은 지우지 않는다. 삭제된 루트에는 새 답글을 달 수 없다.\n" +
            "- 첨부 사진 참조를 끊고, 더는 참조되지 않으면 원본을 정리한다.\n" +
            "- 관련 COMMENT·COMMENT_REPLY 알림의 본문은 \"삭제된 댓글입니다\"로 바뀐다.\n" +
            "- 멱등 아님: 이미 삭제한 댓글을 다시 삭제하면 404 `COMMENT_NOT_FOUND`.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "경로 타입 불일치, 또는 MISSING_COMMENT·FOUND_COMMENT 밖의 commentType",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "타입 불일치", value = COMMENT_TYPE_MISMATCH),
                        ExampleObject(name = "댓글 종류가 아닌 commentType", value = COMMENT_TYPE_NOT_ALLOWED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 갱신 후 재시도",
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
            description = "댓글 없음·남의 댓글·이미 삭제됨·게시물 비노출",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "COMMENT_NOT_FOUND", value = COMMENT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{commentType}/{commentSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "댓글 종류. MISSING_COMMENT 또는 FOUND_COMMENT", example = "MISSING_COMMENT")
        @PathVariable commentType: TargetType,
        @Parameter(description = "댓글 번호", example = "45")
        @PathVariable commentSeq: Long,
    ) {
        commentUseCase.delete(user.userSeq, commentType, commentSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val ROOT_LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"commentType":"MISSING_COMMENT","commentSeq":46,"parentCommentSeq":null,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":31,"displayName":"구미보호소","role":"SHELTER","profileImage":{"imageSeq":1301,"imageUrl":"https://cdn.example.com/images/shelter-7.jpg"}},"body":null,"image":{"imageSeq":1701,"imageUrl":"https://cdn.example.com/images/comment-1701.jpg"},"isDeleted":false,"replyCount":0,"regDttm":"2026-10-30T09:10:00+09:00","modDttm":null},{"commentType":"MISSING_COMMENT","commentSeq":44,"parentCommentSeq":null,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":17,"displayName":"반려가족","role":"USER","profileImage":null},"body":"근처에서 본 것 같아요.","image":null,"isDeleted":false,"replyCount":2,"regDttm":"2026-10-29T17:50:00+09:00","modDttm":null}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REPLY_LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{"commentType":"MISSING_COMMENT","commentSeq":45,"parentCommentSeq":44,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":20,"displayName":"동네주민","role":"USER","profileImage":null},"body":"저도 어제 봤어요.","image":null,"isDeleted":false,"replyCount":0,"regDttm":"2026-10-29T18:00:00+09:00","modDttm":null},{"commentType":"MISSING_COMMENT","commentSeq":47,"parentCommentSeq":44,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":22,"displayName":"(탈퇴한 사용자)","role":"USER","profileImage":null},"body":null,"image":null,"isDeleted":true,"replyCount":0,"regDttm":"2026-10-29T18:30:00+09:00","modDttm":"2026-10-29T19:00:00+09:00"}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"commentType":"MISSING_COMMENT","commentSeq":45,"parentCommentSeq":44,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":20,"displayName":"동네주민","role":"USER","profileImage":null},"body":"저도 어제 봤어요.","image":null,"isDeleted":false,"replyCount":0,"regDttm":"2026-10-29T18:00:00+09:00","modDttm":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DELETED_DETAIL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"commentType":"MISSING_COMMENT","commentSeq":44,"parentCommentSeq":null,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":17,"displayName":"반려가족","role":"USER","profileImage":null},"body":null,"image":null,"isDeleted":true,"replyCount":2,"regDttm":"2026-10-29T17:50:00+09:00","modDttm":"2026-10-30T08:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATED_DETAIL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"commentType":"MISSING_COMMENT","commentSeq":44,"parentCommentSeq":null,"targetType":"MISSING","targetSeq":101,"author":{"userSeq":17,"displayName":"반려가족","role":"USER","profileImage":null},"body":"수정된 설명","image":null,"isDeleted":false,"replyCount":2,"regDttm":"2026-10-29T17:50:00+09:00","modDttm":"2026-10-30T08:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MISSING_PARAMETER =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val TARGET_SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val TARGET_TYPE_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"targetType","reason":"허용하지 않는 값입니다: SHELTER_ANIMAL"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val COMMENT_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"commentType","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val COMMENT_TYPE_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"commentType","reason":"허용하지 않는 값입니다: MISSING"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val COMMENT_EMPTY =
            """{"status":400,"code":"COMMENT_EMPTY","message":"댓글 내용이나 사진 중 하나는 있어야 합니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val COMMENT_NOT_FOUND =
            """{"status":404,"code":"COMMENT_NOT_FOUND","message":"댓글을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val COMMENT_TARGET_NOT_FOUND =
            """{"status":404,"code":"COMMENT_TARGET_NOT_FOUND","message":"댓글을 달 게시물을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class CreateCommentRequest(
    @field:Schema(description = "게시물 종류. MISSING(실종 신고) 또는 FOUND(발견 신고)만. 다른 값은 400 COMMON_INVALID_INPUT", allowableValues = ["MISSING", "FOUND"], example = "MISSING")
    @field:NotNull
    val targetType: String?,
    @field:Schema(description = "게시물 번호", example = "101")
    @field:NotNull
    val targetSeq: Long?,
    @field:Schema(description = "답글이면 같은 게시물의 루트 댓글 번호. 루트 댓글이면 null 또는 생략", nullable = true, example = "null")
    val parentCommentSeq: Long?,
    @field:Schema(description = "본문. imageSeq가 없으면 필수(공백뿐이면 없는 것으로 봄)", nullable = true, example = "근처에서 본 것 같아요.")
    val body: String?,
    @field:Schema(description = "첨부 사진 번호(0~1개). 본인이 올린 이미지만", nullable = true, example = "null")
    val imageSeq: Long?,
) {
    // 모르는 문자열도 JSON 변환 오류(COMMON_INVALID_JSON)가 아니라 명세(13.3)대로 400 COMMON_INVALID_INPUT
    fun toCommand() = CreateCommentCommand(
        TargetType.entries.firstOrNull { it.name == targetType }
            ?: throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("targetType", "MISSING 또는 FOUND만 허용합니다."))),
        targetSeq!!, parentCommentSeq, body, imageSeq,
    )
}

/** 13.4 부분 수정. 미전달은 null(유지), 명시적 null은 Optional.empty()(해제) */
data class UpdateCommentRequest(
    @field:Schema(description = "본문. 생략하면 유지, null이면 비움", nullable = true, example = "수정된 설명")
    val body: Optional<String>? = null,
    @field:Schema(description = "첨부 사진 번호. 생략하면 유지, null이면 사진 해제. 본인이 올린 이미지만", nullable = true, example = "null")
    val imageSeq: Optional<Long>? = null,
)

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class CommentListSuccessSchema : SuccessResponseSchema<List<CommentDetail>>()
abstract class CommentDetailSuccessSchema : SuccessResponseSchema<CommentDetail>()
abstract class CommentCreatedSuccessSchema : SuccessResponseSchema<CreatedComment>()
