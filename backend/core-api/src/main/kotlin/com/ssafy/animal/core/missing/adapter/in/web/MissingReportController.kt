package com.ssafy.animal.core.missing.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.missing.application.port.`in`.MissingReportUseCase
import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingStatus
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
import org.springframework.format.annotation.DateTimeFormat
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
import java.time.LocalDate

/** 7장 실종 신고. 조회는 로그인 사용자, 작성은 USER(Q421), 수정·상태·삭제는 작성자 본인(타인은 404, Q434) */
@Tag(name = "실종 신고", description = "실종 신고 검색·상세·내 신고, 등록·수정·상태 변경·삭제. 한 회원이 진행 중(ACTIVE) 신고를 여러 건 둘 수 있다")
@RestController
@RequestMapping("/api/core/v1/missing-reports")
class MissingReportController(
    private val missingReportUseCase: MissingReportUseCase,
) {

    /** 7.1 */
    @Operation(
        summary = "실종 신고 검색",
        description = "- 로그인 필요(USER·SHELTER 모두)\n" +
            "- 모든 필터는 선택. 생략한 조건은 걸지 않는다(status 생략 시 CLOSED 포함 전체).\n" +
            "- 삭제된 신고와 탈퇴한 회원의 신고는 나오지 않는다.\n" +
            "- `occurredFrom`·`occurredTo`: `occurredDttm` 기준 KST 날짜, 양끝 포함. 한쪽만 보내도 된다. from이 to보다 늦으면 400.\n" +
            "- `gender`·`isNeutered`는 포함 매칭: MALE·FEMALE은 UNKNOWN도 포함, UNKNOWN은 UNKNOWN만. true·false는 모름(null)도 포함.\n" +
            "- `keyword`: `description`만 대소문자 무시 부분 일치. 앞뒤 공백을 지우고 비면 조건 없음.\n" +
            "- size 기본 20. sort 허용 필드는 `regDttm`만(기본 `regDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "검색 성공. 결과가 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissingSummaryPageSuccessSchema::class),
                    examples = [ExampleObject(name = "검색 성공", value = PAGE_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "쿼리 타입·허용값 오류(enum·날짜·숫자·불리언), 날짜 역전, 없는 districtCode, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "쿼리 타입·허용값 오류", value = STATUS_TYPE_MISMATCH),
                        ExampleObject(name = "날짜 역전", value = DATE_REVERSED),
                        ExampleObject(name = "CATALOG_INVALID_DISTRICT_CODE", value = CATALOG_INVALID_DISTRICT_CODE),
                        ExampleObject(name = "정렬 필드 오류", value = SORT_NOT_ALLOWED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 토큰 갱신 후 재시도",
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
    fun search(
        @Parameter(description = "신고 상태. ACTIVE: 찾는 중, CLOSED: 종료. 생략하면 전체", example = "ACTIVE")
        @RequestParam(required = false) status: MissingStatus?,
        @Parameter(description = "실종일 시작(YYYY-MM-DD, KST, 포함)", example = "2026-10-01")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredFrom: LocalDate?,
        @Parameter(description = "실종일 끝(YYYY-MM-DD, KST, 포함)", example = "2026-10-31")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredTo: LocalDate?,
        @Parameter(description = "시·군·구 코드 5자리(5.5 값). 없는 코드면 400", example = "27110")
        @RequestParam(required = false) districtCode: String?,
        @Parameter(description = "종 번호(5.1 값)", example = "1")
        @RequestParam(required = false) speciesSeq: Int?,
        @Parameter(description = "성별. MALE·FEMALE은 UNKNOWN도 포함, UNKNOWN은 UNKNOWN만", example = "MALE")
        @RequestParam(required = false) gender: MissingGender?,
        @Parameter(description = "중성화 여부. true·false 모두 모름(null)도 포함", example = "true")
        @RequestParam(required = false) isNeutered: Boolean?,
        @Parameter(description = "특징 설명(description) 검색어", example = "목줄")
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): PageResponse<MissingSummaryResponse> {
        val filter = MissingSearchFilter(status, occurredFrom, occurredTo, districtCode, speciesSeq, gender, isNeutered, keyword)
        return PageResponse.of(missingReportUseCase.search(filter, pageable).map(MissingSummaryResponse::from))
    }

    /** 7.3. 페이지 없이 배열(Q464) */
    @Operation(
        summary = "내 실종 신고 목록",
        description = "- USER만\n" +
            "- 내가 쓴 삭제되지 않은 신고 전체(ACTIVE·CLOSED). 페이지 없이 배열로 준다.\n" +
            "- 정렬은 `regDttm` 내림차순 고정. 파라미터 없음.\n" +
            "- 신고가 없으면 빈 배열.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissingSummaryListSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = LIST_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 토큰 갱신 후 재시도",
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
            description = "SHELTER 계정. 재시도하지 않는다",
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
    fun getMine(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser) =
        missingReportUseCase.getMine(user.userSeq).map(MissingSummaryResponse::from)

    /** 7.2 */
    @Operation(
        summary = "실종 신고 상세",
        description = "- 로그인 필요(USER·SHELTER 모두)\n" +
            "- 없음·삭제·탈퇴한 회원의 신고는 404.\n" +
            "- `contactPhone`: 공개(`isPhonePublic=true`)면 모든 로그인 사용자, 비공개면 작성자 본인에게만 값을 준다. 그 외에는 null.\n" +
            "- `isBookmarked`는 조회자 기준.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissingDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = DETAIL_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "missingReportSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "경로 타입 오류", value = SEQ_TYPE_MISMATCH)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효. 만료면 토큰 갱신 후 재시도",
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
            description = "없음·삭제·탈퇴한 회원의 신고",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "MISSING_REPORT_NOT_FOUND", value = MISSING_REPORT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{missingReportSeq}")
    fun get(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "실종 신고 번호", example = "101") @PathVariable missingReportSeq: Long,
    ) = MissingDetailResponse.from(missingReportUseCase.get(user.userSeq, missingReportSeq))

    /** 7.4 */
    @Operation(
        summary = "실종 신고 등록",
        description = "- USER만\n" +
            "- 진행 중(ACTIVE) 신고가 이미 있어도 새로 등록할 수 있다(회원당 건수 제한 없음).\n" +
            "- 사진은 이미지 업로드 API로 먼저 올리고 받은 `imageSeq` 1~3개를 보낸다. 배열 순서가 표시 순서다.\n" +
            "- `districtCode`는 서버가 좌표를 역지오코딩해 정한다. 실패해도 null로 저장하고 등록은 진행한다.\n" +
            "- 등록이 끝나면 AI 매칭을 요청한다(`aiMatchingRequested=true`).\n" +
            "- 검사 순서: 필수값 → 좌표 범위 → 조합 → 사진(개수·중복·존재·소유) → 길이.\n" +
            "- 응답 message는 `실종 신고가 등록되었습니다.`",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "등록 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CreatedMissingSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "등록 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"실종 신고가 등록되었습니다.","data":{"missingReportSeq":101,"status":"ACTIVE","districtCode":"27110","aiMatchingRequested":true},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락, 좌표 범위·길이 초과, 사진 개수(1~3)·중복, 본문을 읽을 수 없음(enum·날짜 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = CREATE_REQUIRED_MISSING),
                        ExampleObject(name = "좌표 범위 오류", value = LATITUDE_OUT_OF_RANGE),
                        ExampleObject(name = "길이 초과", value = LENGTH_EXCEEDED),
                        ExampleObject(name = "IMAGE_INVALID_COUNT", value = IMAGE_INVALID_COUNT),
                        ExampleObject(name = "사진 중복", value = IMAGE_DUPLICATED),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 토큰 갱신 후 재시도",
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
            description = "SHELTER 계정, 정지된 계정, 또는 남이 올린 사진. 사진 오류는 직접 올린 사진으로 바꿔 보낸다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                        ExampleObject(name = "IMAGE_NOT_OWNED", value = IMAGE_NOT_OWNED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없는 조합 번호 또는 없는 사진 번호",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @SuccessMessage("실종 신고가 등록되었습니다.")
    fun create(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: CreateMissingRequest) =
        CreatedMissingResponse.from(missingReportUseCase.create(user.userSeq, request.toCommand()))

    /** 7.5 */
    @Operation(
        summary = "실종 신고 수정",
        description = "- 로그인 필요. 작성자 본인만(남의 신고는 404)\n" +
            "- 보낸 필드만 바꾼다. `age`·`weight`·`isNeutered`·`microchipNumber`·`description`은 null을 보내면 지운다. 나머지 필드는 null이면 유지.\n" +
            "- 좌표는 `latitude`·`longitude`를 함께 보낸다. 바뀌면 `districtCode`를 다시 구한다(실패하면 null).\n" +
            "- `imageSeqs`는 최종 배열 전체(1~3개). 빠진 사진은 연결을 풀고, 사진 구성이 바뀌면 AI 재매칭을 요청한다(순서만 바뀌면 안 함).\n" +
            "- 종료(CLOSED)된 신고는 수정할 수 없다(409). 먼저 상태 변경(7.6)으로 재개한다.\n" +
            "- 상태는 이 API로 바꾸지 않는다(7.6). 응답은 상세(7.2)와 같다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 완료. 수정된 상세를 준다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissingDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 완료", value = DETAIL_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "좌표 한쪽만 보냄, 좌표 범위·길이 초과, 주소·연락처 빈 값, 사진 개수(1~3)·중복, 경로 타입 오류, 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "좌표 한쪽만 보냄", value = COORDINATE_PAIR_REQUIRED),
                        ExampleObject(name = "좌표 범위 오류", value = LATITUDE_OUT_OF_RANGE),
                        ExampleObject(name = "길이 초과", value = LENGTH_EXCEEDED),
                        ExampleObject(name = "빈 값", value = ADDRESS_BLANK),
                        ExampleObject(name = "IMAGE_INVALID_COUNT", value = IMAGE_INVALID_COUNT),
                        ExampleObject(name = "사진 중복", value = IMAGE_DUPLICATED),
                        ExampleObject(name = "경로 타입 오류", value = SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 토큰 갱신 후 재시도",
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
            description = "정지된 계정, 또는 남이 올린 사진",
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
            description = "신고가 없음·삭제됨·남의 신고, 없는 조합 번호, 없는 사진 번호",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "MISSING_REPORT_NOT_FOUND", value = MISSING_REPORT_NOT_FOUND),
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "종료(CLOSED)된 신고. 상태 변경(7.6)으로 재개한 뒤 수정한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "MISSING_REPORT_CLOSED", value = MISSING_REPORT_CLOSED)],
                ),
            ],
        ),
    )
    @PatchMapping("/{missingReportSeq}")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "실종 신고 번호", example = "101") @PathVariable missingReportSeq: Long,
        @RequestBody request: UpdateMissingRequest,
    ) = MissingDetailResponse.from(missingReportUseCase.update(user.userSeq, missingReportSeq, request.toCommand()))

    /** 7.6 */
    @Operation(
        summary = "실종 신고 상태 변경",
        description = "- 로그인 필요. 작성자 본인만(남의 신고는 404)\n" +
            "- ACTIVE → CLOSED(종료), CLOSED → ACTIVE(재개). 종료 사유·시각 필드는 없다.\n" +
            "- 현재와 같은 상태로 바꾸면 400.\n" +
            "- 다른 진행 중(ACTIVE) 신고가 있어도 재개할 수 있다. 재개하면 AI 매칭을 다시 요청한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "변경 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = MissingStatusSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "변경 완료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"missingReportSeq":101,"status":"CLOSED"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "status 누락, 현재와 같은 상태, 경로 타입 오류, 본문을 읽을 수 없음(허용 밖 status 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "status 누락", value = STATUS_REQUIRED),
                        ExampleObject(name = "같은 상태", value = SAME_STATUS),
                        ExampleObject(name = "경로 타입 오류", value = SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "본문 형식 오류", value = FailExamples.COMMON_INVALID_JSON),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 토큰 갱신 후 재시도",
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
            description = "신고가 없음·삭제됨·남의 신고",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "MISSING_REPORT_NOT_FOUND", value = MISSING_REPORT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @PatchMapping("/{missingReportSeq}/status")
    fun changeStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "실종 신고 번호", example = "101") @PathVariable missingReportSeq: Long,
        @Valid @RequestBody request: ChangeMissingStatusRequest,
    ) = MissingStatusResponse.from(missingReportUseCase.changeStatus(user.userSeq, missingReportSeq, request.status!!))

    /** 7.7 */
    @Operation(
        summary = "실종 신고 삭제",
        description = "- 로그인 필요. 작성자 본인만(남의 신고는 404)\n" +
            "- 논리 삭제. ACTIVE·CLOSED 모두 삭제할 수 있다. 삭제 후 목록·상세에 나오지 않는다.\n" +
            "- 사진 연결을 풀고, 다른 곳에서 쓰지 않는 사진만 원본을 지운다.\n" +
            "- 이미 삭제된 신고를 다시 삭제하면 404.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "missingReportSeq가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "경로 타입 오류", value = SEQ_TYPE_MISMATCH)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "401",
            description = "access 토큰 없음·만료·무효, 또는 탈퇴한 계정. 만료면 토큰 갱신 후 재시도",
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
            description = "신고가 없음·이미 삭제됨·남의 신고",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "MISSING_REPORT_NOT_FOUND", value = MISSING_REPORT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{missingReportSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "실종 신고 번호", example = "101") @PathVariable missingReportSeq: Long,
    ) {
        missingReportUseCase.delete(user.userSeq, missingReportSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val SUMMARY_JSON =
            """{"missingReportSeq":101,"classification":{"combinationSeq":88,"speciesSeq":1,"speciesCode":"DOG","speciesName":"개","breedSeq":12,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"},"gender":"MALE","age":"3살","isNeutered":true,"weight":"4.5kg","status":"ACTIVE","address":"대구광역시 중구 동인동1가","districtCode":"27110","occurredDttm":"2026-10-28T18:00:00+09:00","representativeImageUrl":"https://storage.example.com/images/1001.jpg","regDttm":"2026-10-29T12:00:00+09:00"}"""
        const val PAGE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[""" + SUMMARY_JSON +
                """],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[""" + SUMMARY_JSON +
                """],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"missingReportSeq":101,"classification":{"combinationSeq":88,"speciesSeq":1,"speciesCode":"DOG","speciesName":"개","breedSeq":12,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"},"gender":"MALE","age":"3살","isNeutered":true,"weight":"4.5kg","status":"ACTIVE","address":"대구광역시 중구 동인동1가","districtCode":"27110","occurredDttm":"2026-10-28T18:00:00+09:00","representativeImageUrl":"https://storage.example.com/images/1001.jpg","regDttm":"2026-10-29T12:00:00+09:00","user":{"userSeq":17,"displayName":"반려가족","role":"USER","profileImage":{"imageSeq":900,"imageUrl":"https://storage.example.com/images/900.jpg"}},"microchipNumber":null,"description":"파란 목줄","latitude":35.87,"longitude":128.6,"images":[{"imageSeq":1001,"imageUrl":"https://storage.example.com/images/1001.jpg","displayOrder":1},{"imageSeq":1002,"imageUrl":"https://storage.example.com/images/1002.jpg","displayOrder":2}],"isPhonePublic":false,"contactPhone":"01012345678","isBookmarked":false,"commentCount":3,"modDttm":"2026-10-29T12:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""

        const val STATUS_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"missingReportSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DATE_REVERSED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"occurredFrom","reason":"시작일이 종료일보다 늦습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SORT_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: occurredDttm (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_INVALID_DISTRICT_CODE =
            """{"status":400,"code":"CATALOG_INVALID_DISTRICT_CODE","message":"존재하지 않는 행정구역 코드입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CREATE_REQUIRED_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"combinationSeq","reason":"널이어서는 안됩니다"},{"field":"address","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val LATITUDE_OUT_OF_RANGE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"latitude","reason":"위도는 -90~90입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val COORDINATE_PAIR_REQUIRED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"latitude","reason":"latitude와 longitude는 함께 보내야 합니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val LENGTH_EXCEEDED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"weight","reason":"30자 이하로 입력해 주세요."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ADDRESS_BLANK =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"address","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_DUPLICATED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"imageSeqs","reason":"같은 사진을 두 번 넣을 수 없습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_INVALID_COUNT =
            """{"status":400,"code":"IMAGE_INVALID_COUNT","message":"사진은 1~3장이어야 합니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_REQUIRED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SAME_STATUS =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"이미 CLOSED 상태입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MISSING_REPORT_NOT_FOUND =
            """{"status":404,"code":"MISSING_REPORT_NOT_FOUND","message":"실종 신고를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_COMBINATION_NOT_FOUND =
            """{"status":404,"code":"CATALOG_COMBINATION_NOT_FOUND","message":"존재하지 않는 종·품종·색상 조합입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val MISSING_REPORT_CLOSED =
            """{"status":409,"code":"MISSING_REPORT_CLOSED","message":"종료된 실종 신고는 수정할 수 없습니다. 먼저 재개해 주세요.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
