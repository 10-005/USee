package com.ssafy.animal.core.rescue.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.application.port.`in`.RescueSource
import com.ssafy.animal.core.rescue.application.port.`in`.RescueUseCase
import com.ssafy.animal.core.rescue.domain.RescueStatus
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
import org.springframework.security.access.prepost.PreAuthorize
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

/** 10장은 모두 SHELTER 전용(보호소 웹 "구조일지") */
@Tag(name = "구조 기록", description = "보호소 웹 '구조일지'. 모두 SHELTER 전용이고 대상은 토큰의 보호소. 다른 보호소의 구조는 404로 감춘다")
@RestController
@RequestMapping("/api/core/v1/rescues")
@PreAuthorize("hasRole('SHELTER')")
class RescueController(
    private val rescueUseCase: RescueUseCase,
) {

    /** 10.1 우리 보호소 구조 기록 + 상태별 건수(size 기본 8) */
    @Operation(
        summary = "우리 보호소 구조 기록 목록",
        description = "- SHELTER만\n" +
            "- 토큰 보호소의 삭제되지 않은 구조 기록 목록과 상태별 건수(`statusCounts`)를 한 응답으로 준다.\n" +
            "- 필터는 모두 선택이고, 생략하면 우리 보호소 구조 기록 전량이다.\n" +
            "- `period`: `rescuedDttm`(실제 구조 일시)이 요청 시각 기준 최근 N일 이내.\n" +
            "- `speciesSeq`: 목록의 동물 정보와 같은 기준(REPORT는 발견 신고, DIRECT는 가장 먼저 등록된 보호동물)으로 거른다. 동물 정보가 아직 없는 DIRECT 구조는 빠진다.\n" +
            "- `statusCounts`: `total`·`PROCESSING`·`DONE`·`FAILED`·`CANCELED`. `status`를 뺀 나머지 필터만 적용해 세므로 탭을 바꿔도 다른 탭 숫자가 유지된다.\n" +
            "- `page.totalElements`는 현재 `status` 탭의 건수다(`status` 생략이면 `total`과 같다).\n" +
            "- size 기본 **8**. sort 허용 `regDttm`·`completedDttm`, 기본 `regDttm,desc`. `completedDttm` 정렬에서 진행 중(완료 시각 null)은 방향과 상관없이 뒤로 간다.\n" +
            "- 항목의 `animal`이 null이면 화면은 '-'로 표시한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RescuePageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = PAGE_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "허용값 밖의 status·period·source, 숫자가 아닌 speciesSeq·foundReportSeq, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "status 허용값 밖", value = INVALID_STATUS_PARAM),
                        ExampleObject(name = "정렬 필드 허용 밖", value = INVALID_SORT),
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
            responseCode = "403",
            description = "SHELTER가 아닌 계정. 재시도하지 않는다",
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
    fun getPage(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "구조 상태 탭. PROCESSING=진행 중, DONE=완료, FAILED=실패, CANCELED=취소. 생략하면 전체", example = "PROCESSING")
        @RequestParam(required = false) status: RescueStatus?,
        @Parameter(description = "구조 기간. rescuedDttm이 최근 7·30·90일 이내. 생략하면 전체", example = "RECENT_30D")
        @RequestParam(required = false) period: RecentPeriod?,
        @Parameter(description = "축종 번호(5.1). 생략하면 전체", example = "1")
        @RequestParam(required = false) speciesSeq: Int?,
        @Parameter(description = "접수 경로. REPORT=발견 신고 연계, DIRECT=직접 접수. 생략하면 전체", example = "REPORT")
        @RequestParam(required = false) source: RescueSource?,
        @Parameter(description = "이 발견 신고에 연결된 구조만", example = "210")
        @RequestParam(required = false) foundReportSeq: Int?,
        @PageableDefault(size = 8) pageable: Pageable,
    ) = RescuePageResponse.from(
        rescueUseCase.getPage(user.shelterSeq, RescueFilter(status, period, speciesSeq, source, foundReportSeq), pageable),
    )

    /** 10.2 */
    @Operation(
        summary = "구조 기록 상세",
        description = "- SHELTER만\n" +
            "- 우리 보호소의 삭제되지 않은 구조 기록만 보인다. 없음·삭제·다른 보호소의 구조는 모두 404.\n" +
            "- `foundReportSummary`: 직접 접수(DIRECT)면 null. 발견 신고가 삭제됐어도 남은 정보로 채운다.\n" +
            "- `reporter`는 신고자 실명·전화번호다(현장 대응용으로 접수한 보호소에 공개). 탈퇴한 신고자는 name이 `(탈퇴한 사용자)`.\n" +
            "- `linkedAnimalCount`가 1 이상이면 삭제(10.6)할 수 없다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RescueDetailSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "발견 신고 연계", value = DETAIL_OK),
                        ExampleObject(name = "직접 접수", value = DETAIL_DIRECT_OK),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "rescueSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "rescueSeq 타입 오류", value = INVALID_RESCUE_SEQ)],
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
            responseCode = "403",
            description = "SHELTER가 아닌 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "구조 기록이 없거나 삭제됨, 또는 다른 보호소의 구조",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_NOT_FOUND", value = RESCUE_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{rescueSeq}")
    fun get(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "구조 기록 번호", example = "301") @PathVariable rescueSeq: Long,
    ) = RescueResponse.from(rescueUseCase.get(user.shelterSeq, rescueSeq))

    /** 10.3 발견 신고 연계/직접 접수(PROCESSING) */
    @Operation(
        summary = "구조 접수",
        description = "- SHELTER만\n" +
            "- 항상 PROCESSING으로 만든다. 보호소는 토큰 보호소로 서버가 정한다(요청에 shelterSeq 없음).\n" +
            "- `foundReportSeq`를 보내면 발견 신고 연계, 생략하거나 null이면 직접 접수.\n" +
            "- 담당이 아닌 발견 신고도, CLOSED 신고도 접수할 수 있다. 삭제된 신고만 404.\n" +
            "- 발견 신고 1건에 삭제되지 않은 PROCESSING·DONE 구조는 1건만 둘 수 있다(409). FAILED·CANCELED 뒤에는 다시 접수할 수 있다.\n" +
            "- 필수: `rescueReasonCode`·`rescueAddress`·`rescuedDttm`. `rescuedDttm` 형식이 틀리면 400 `COMMON_INVALID_JSON`.\n" +
            "- 다음 호출: 진행 상황은 10.4로 고치고, 끝나면 10.5로 종료 처리한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "접수 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreatedRescueSuccessSchema::class),
                    examples = [ExampleObject(name = "접수 완료", value = CREATED_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락·공백, 정의되지 않은 사유 코드, 길이 초과, 또는 본문을 읽을 수 없음(일시 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = CREATE_REQUIRED_MISSING),
                        ExampleObject(name = "정의되지 않은 사유 코드", value = INVALID_REASON_CODE),
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
            description = "SHELTER가 아닌 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "foundReportSeq의 발견 신고가 없거나 삭제됨",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "FOUND_REPORT_NOT_FOUND", value = FOUND_REPORT_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이 발견 신고에 이미 PROCESSING·DONE 구조가 있음(동시 접수 포함). 기존 구조를 확인한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_ACTIVE_ALREADY_EXISTS", value = RESCUE_ACTIVE_ALREADY_EXISTS)],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("구조가 접수되었습니다.")
    fun create(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreateRescueRequest) =
        CreatedRescueResponse.from(rescueUseCase.create(user, request.toCommand()))

    /** 10.4 구조 진행 기록 수정 */
    @Operation(
        summary = "구조 기록 수정",
        description = "- SHELTER만\n" +
            "- 부분 수정. 보낸 필드만 바꾸고 생략한 필드는 유지한다.\n" +
            "- `rescueAddressDetail`·`rescueMemo`는 null을 보내면 비운다. `rescueReasonCode`·`rescueAddress`·`rescuedDttm`은 null·공백으로 바꿀 수 없다(400).\n" +
            "- 종료 상태(DONE·FAILED·CANCELED)에서는 `rescueMemo`만 고칠 수 있다. 다른 필드를 보내면 400.\n" +
            "- 응답은 수정이 반영된 상세(10.2와 같은 형태).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 성공. 수정 후 상세",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RescueDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 성공", value = UPDATED_OK)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수 필드 비우기, 정의되지 않은 사유 코드, 길이 초과, 종료 상태에서 메모 외 필드 수정, rescueSeq 타입 오류, 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수 필드 비우기", value = UPDATE_REQUIRED_CLEARED),
                        ExampleObject(name = "종료 상태에서 메모 외 수정", value = UPDATE_TERMINAL),
                        ExampleObject(name = "정의되지 않은 사유 코드", value = INVALID_REASON_CODE),
                        ExampleObject(name = "rescueSeq 타입 오류", value = INVALID_RESCUE_SEQ),
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
            description = "SHELTER가 아닌 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "구조 기록이 없거나 삭제됨, 또는 다른 보호소의 구조",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_NOT_FOUND", value = RESCUE_NOT_FOUND)],
                ),
            ],
        ),
    )
    @PatchMapping("/{rescueSeq}")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "구조 기록 번호", example = "301") @PathVariable rescueSeq: Long,
        @RequestBody request: UpdateRescueRequest,
    ) = RescueResponse.from(rescueUseCase.update(user, rescueSeq, request.toCommand()))

    /** 10.5 DONE/FAILED/CANCELED 처리 */
    @Operation(
        summary = "구조 종료 처리",
        description = "- SHELTER만\n" +
            "- PROCESSING → DONE·FAILED·CANCELED로 한 번만 바꾼다. 종료 상태에서는 되돌리거나 다른 종료 상태로 바꿀 수 없다(409).\n" +
            "- FAILED·CANCELED는 공백 아닌 `resultReason`이 필수다(앞뒤 공백을 지워 저장). DONE이면 `resultReason`을 보내도 null로 저장한다.\n" +
            "- `completedDttm`은 서버 시각으로 기록한다.\n" +
            "- 검사 순서: 구조 존재(404) → 상태 전이(409) → 사유(400).\n" +
            "- 다음 호출: DONE이면 이 구조로 보호동물을 등록할 수 있다(11.4 `rescueSeq`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "종료 처리 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RescueStatusSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "DONE", value = STATUS_DONE_OK),
                        ExampleObject(name = "FAILED", value = STATUS_FAILED_OK),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "status 누락, FAILED·CANCELED에 사유 없음, rescueSeq 타입 오류, 본문을 읽을 수 없음(정의되지 않은 status 값 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "status 누락", value = STATUS_MISSING),
                        ExampleObject(name = "RESCUE_RESULT_REASON_REQUIRED", value = RESCUE_RESULT_REASON_REQUIRED),
                        ExampleObject(name = "rescueSeq 타입 오류", value = INVALID_RESCUE_SEQ),
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
            description = "SHELTER가 아닌 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "구조 기록이 없거나 삭제됨, 또는 다른 보호소의 구조",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_NOT_FOUND", value = RESCUE_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 종료된 구조이거나 status=PROCESSING을 보냄. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_INVALID_TRANSITION", value = RESCUE_INVALID_TRANSITION)],
                ),
            ],
        ),
    )
    @PatchMapping("/{rescueSeq}/status")
    fun changeStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "구조 기록 번호", example = "301") @PathVariable rescueSeq: Long,
        @Valid @RequestBody request: ChangeRescueStatusRequest,
    ) = RescueStatusResponse.from(rescueUseCase.changeStatus(user, rescueSeq, request.status!!, request.resultReason))

    /** 10.6 논리 삭제 */
    @Operation(
        summary = "구조 기록 삭제",
        description = "- SHELTER만\n" +
            "- 논리 삭제. 상태와 관계없이 지울 수 있다.\n" +
            "- 이 구조에 연결된 보호동물이 한 마리라도 있으면 거부한다(409). 보호동물을 먼저 삭제(11.9)해야 한다.\n" +
            "- 이미 삭제된 구조를 다시 지우면 404.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "rescueSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "rescueSeq 타입 오류", value = INVALID_RESCUE_SEQ)],
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
            description = "SHELTER가 아닌 계정, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "구조 기록이 없거나 이미 삭제됨, 또는 다른 보호소의 구조",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_NOT_FOUND", value = RESCUE_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "연결된 보호동물이 있음. 보호동물을 먼저 삭제한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "RESCUE_HAS_ANIMALS", value = RESCUE_HAS_ANIMALS)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{rescueSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "구조 기록 번호", example = "301") @PathVariable rescueSeq: Long,
    ) {
        rescueUseCase.delete(user, rescueSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val PAGE_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"statusCounts":{"total":24,"PROCESSING":3,"DONE":16,"FAILED":3,"CANCELED":2},"page":{"content":[{"rescueSeq":301,"status":"PROCESSING","source":"REPORT","foundReportSeq":210,"rescuedDttm":"2026-10-23T17:47:00+09:00","rescueReasonCode":"WANDERING","rescueMemo":"공원 벤치 아래 은신 중, 포획틀 필요","resultReason":null,"animal":{"speciesName":"고양이","breedName":"코리안숏헤어","age":"0살","gender":"UNKNOWN","isNeutered":null,"colorName":"흰색","weight":"약 3kg","representativeImageUrl":"https://cdn.example.com/images/1601.jpg"},"regDttm":"2026-10-23T18:00:00+09:00"},{"rescueSeq":298,"status":"PROCESSING","source":"DIRECT","foundReportSeq":null,"rescuedDttm":"2026-10-20T09:10:00+09:00","rescueReasonCode":"INJURY_ILLNESS","rescueMemo":null,"resultReason":null,"animal":null,"regDttm":"2026-10-20T09:30:00+09:00"}],"page":0,"size":8,"totalElements":3,"totalPages":1,"hasNext":false}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"rescueSeq":301,"shelterSeq":7,"source":"REPORT","foundReportSeq":210,"status":"PROCESSING","rescueReasonCode":"WANDERING","rescueAddress":"구미시 인동동 123-12","rescueAddressDetail":"송산도서관 인근 마을","rescuedDttm":"2026-10-23T17:47:00+09:00","rescueMemo":"공원 벤치 아래 은신 중, 포획틀 필요","resultReason":null,"regDttm":"2026-10-23T18:00:00+09:00","modDttm":null,"completedDttm":null,"linkedAnimalCount":0,"foundReportSummary":{"foundReportSeq":210,"classification":{"combinationSeq":88,"speciesSeq":2,"speciesCode":"CAT","speciesName":"고양이","breedSeq":41,"breedName":"코리안숏헤어","colorSeq":1,"colorName":"흰색"},"age":"0살","gender":"UNKNOWN","isNeutered":null,"weight":"약 3kg","address":"구미시 인동동 123-123 구미아파트","occurredDttm":"2026-10-19T18:30:00+09:00","representativeImageUrl":"https://cdn.example.com/images/1601.jpg","reporter":{"name":"홍길동","phone":"01012341234"},"status":"ACTIVE"}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_DIRECT_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"rescueSeq":298,"shelterSeq":7,"source":"DIRECT","foundReportSeq":null,"status":"DONE","rescueReasonCode":"INJURY_ILLNESS","rescueAddress":"구미시 진평동 45","rescueAddressDetail":null,"rescuedDttm":"2026-10-20T09:10:00+09:00","rescueMemo":null,"resultReason":null,"regDttm":"2026-10-20T09:30:00+09:00","modDttm":"2026-10-20T15:00:00+09:00","completedDttm":"2026-10-20T15:00:00+09:00","linkedAnimalCount":1,"foundReportSummary":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATED_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"rescueSeq":301,"shelterSeq":7,"source":"REPORT","foundReportSeq":210,"status":"PROCESSING","rescueReasonCode":"INJURY_ILLNESS","rescueAddress":"구미시 인동동 123-12","rescueAddressDetail":"송산도서관 인근 마을","rescuedDttm":"2026-10-23T17:47:00+09:00","rescueMemo":"포획틀 설치 완료","resultReason":null,"regDttm":"2026-10-23T18:00:00+09:00","modDttm":"2026-10-24T10:00:00+09:00","completedDttm":null,"linkedAnimalCount":0,"foundReportSummary":{"foundReportSeq":210,"classification":{"combinationSeq":88,"speciesSeq":2,"speciesCode":"CAT","speciesName":"고양이","breedSeq":41,"breedName":"코리안숏헤어","colorSeq":1,"colorName":"흰색"},"age":"0살","gender":"UNKNOWN","isNeutered":null,"weight":"약 3kg","address":"구미시 인동동 123-123 구미아파트","occurredDttm":"2026-10-19T18:30:00+09:00","representativeImageUrl":"https://cdn.example.com/images/1601.jpg","reporter":{"name":"홍길동","phone":"01012341234"},"status":"ACTIVE"}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CREATED_OK =
            """{"status":201,"code":"SUCCESS","message":"구조가 접수되었습니다.","data":{"rescueSeq":301,"status":"PROCESSING","regDttm":"2026-10-23T18:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_DONE_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"rescueSeq":301,"status":"DONE","resultReason":null,"completedDttm":"2026-10-24T09:10:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_FAILED_OK =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"rescueSeq":301,"status":"FAILED","resultReason":"현장 도착 전 이동","completedDttm":"2026-10-24T09:10:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_STATUS_PARAM =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_SORT =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: rescuedDttm (허용: regDttm, completedDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_RESCUE_SEQ =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"rescueSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CREATE_REQUIRED_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"rescueAddress","reason":"공백일 수 없습니다"},{"field":"rescuedDttm","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val INVALID_REASON_CODE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"rescueReasonCode","reason":"정의되지 않은 구조 사유 코드입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATE_REQUIRED_CLEARED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"rescueAddress","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATE_TERMINAL =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"rescueAddress","reason":"종료된 구조는 메모만 수정할 수 있습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_RESULT_REASON_REQUIRED =
            """{"status":400,"code":"RESCUE_RESULT_REASON_REQUIRED","message":"실패·취소 사유를 입력해 주세요.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_NOT_FOUND =
            """{"status":404,"code":"RESCUE_NOT_FOUND","message":"구조 기록을 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val FOUND_REPORT_NOT_FOUND =
            """{"status":404,"code":"FOUND_REPORT_NOT_FOUND","message":"발견 신고를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_ACTIVE_ALREADY_EXISTS =
            """{"status":409,"code":"RESCUE_ACTIVE_ALREADY_EXISTS","message":"이 발견 신고에는 이미 진행 중이거나 완료된 구조 기록이 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_INVALID_TRANSITION =
            """{"status":409,"code":"RESCUE_INVALID_TRANSITION","message":"변경할 수 없는 구조 상태입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val RESCUE_HAS_ANIMALS =
            """{"status":409,"code":"RESCUE_HAS_ANIMALS","message":"연결된 보호동물이 있어 삭제할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
