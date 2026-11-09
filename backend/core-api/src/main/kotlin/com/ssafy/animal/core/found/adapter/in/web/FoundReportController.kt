package com.ssafy.animal.core.found.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.common.response.SuccessMessage
import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.FoundReportUseCase
import com.ssafy.animal.core.found.application.port.`in`.FoundSearchFilter
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.found.domain.Gender
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

@Tag(name = "발견 신고", description = "발견 신고 검색·상세·내 신고, 등록(담당 보호소 자동 배정)·수정·상태 변경·삭제, 보호소 담당 신고 목록")
@RestController
@RequestMapping("/api/core/v1/found-reports")
class FoundReportController(
    private val foundReportUseCase: FoundReportUseCase,
) {

    /** 8.1 발견 신고 검색(모든 필터 선택, 생략 시 조건 없음) */
    @Operation(
        summary = "발견 신고 검색",
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
                    schema = Schema(implementation = FoundSummaryPageSuccessSchema::class),
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
        @Parameter(description = "신고 상태. ACTIVE: 진행 중, CLOSED: 종료. 생략하면 전체", example = "ACTIVE")
        @RequestParam(required = false) status: FoundStatus?,
        @Parameter(description = "발견일 시작(YYYY-MM-DD, KST, 포함)", example = "2026-10-01")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredFrom: LocalDate?,
        @Parameter(description = "발견일 끝(YYYY-MM-DD, KST, 포함)", example = "2026-10-31")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) occurredTo: LocalDate?,
        @Parameter(description = "시·군·구 코드 5자리(5.5 값). 없는 코드면 400", example = "27110")
        @RequestParam(required = false) districtCode: String?,
        @Parameter(description = "종 번호(5.1 값)", example = "1")
        @RequestParam(required = false) speciesSeq: Int?,
        @Parameter(description = "성별. MALE·FEMALE은 UNKNOWN도 포함, UNKNOWN은 UNKNOWN만", example = "UNKNOWN")
        @RequestParam(required = false) gender: Gender?,
        @Parameter(description = "중성화 여부. true·false 모두 모름(null)도 포함", example = "false")
        @RequestParam(required = false) isNeutered: Boolean?,
        @Parameter(description = "특징 설명(description) 검색어", example = "목줄")
        @RequestParam(required = false) keyword: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(
        foundReportUseCase.search(
            FoundSearchFilter(status, occurredFrom, occurredTo, districtCode, speciesSeq, gender, isNeutered, keyword),
            pageable,
        ).map(FoundReportSummaryResponse::from),
    )

    /** 8.3 내 발견 신고 전량(배열) */
    @Operation(
        summary = "내 발견 신고 목록",
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
                    schema = Schema(implementation = FoundSummaryListSuccessSchema::class),
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
            description = "SHELTER 계정. 보호소는 담당 신고 목록(`/assigned`)을 쓴다",
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
        foundReportUseCase.getMine(user.userSeq).map(FoundReportSummaryResponse::from)

    /** 8.8 내 보호소 담당 발견 신고(size 기본 8) */
    @Operation(
        summary = "보호소 담당 발견 신고 목록",
        description = "- SHELTER만\n" +
            "- 토큰의 보호소가 담당인 삭제되지 않은 신고. 모든 필터는 선택, 생략하면 담당 신고 전체.\n" +
            "- 탈퇴한 회원의 신고도 나온다. 이때 `reporter.name`은 `(탈퇴한 사용자)`.\n" +
            "- `period`: 요청 시각 기준 최근 N일 안에 발견(`occurredDttm`)된 신고.\n" +
            "- `rescueStatus`: 신고마다 가장 최근의 삭제되지 않은 구조 기록 기준.\n" +
            "- `reporter`는 실명과 연락처(공개 여부와 관계없이)를 준다.\n" +
            "- size 기본 8. sort 허용 필드는 `regDttm`만(기본 `regDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 결과가 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AssignedFoundPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = ASSIGNED_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "쿼리 타입·허용값 오류(status·period·rescueStatus·speciesSeq), 없는 districtCode, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "쿼리 타입·허용값 오류", value = PERIOD_TYPE_MISMATCH),
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
        ApiResponse(
            responseCode = "403",
            description = "USER 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "AUTH_FORBIDDEN", value = FailExamples.AUTH_FORBIDDEN)],
                ),
            ],
        ),
    )
    @GetMapping("/assigned")
    @PreAuthorize("hasRole('SHELTER')")
    fun getAssigned(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "신고 상태. ACTIVE: 진행 중, CLOSED: 종료. 생략하면 전체", example = "ACTIVE")
        @RequestParam(required = false) status: FoundStatus?,
        @Parameter(description = "발견 기간. RECENT_7D·RECENT_30D·RECENT_90D: 요청 시각 기준 최근 7·30·90일. 생략하면 전체", example = "RECENT_30D")
        @RequestParam(required = false) period: RecentPeriod?,
        @Parameter(description = "시·군·구 코드 5자리(5.5 값). 없는 코드면 400", example = "27110")
        @RequestParam(required = false) districtCode: String?,
        @Parameter(description = "종 번호(5.1 값)", example = "1")
        @RequestParam(required = false) speciesSeq: Int?,
        @Parameter(
            description = "구조 상태. NONE: 구조 기록 없음, PROCESSING: 구조 진행 중, DONE: 구조 완료, ABORTED: 실패·취소. 생략하면 전체",
            example = "NONE",
        )
        @RequestParam(required = false) rescueStatus: RescueProgress?,
        @PageableDefault(size = 8) pageable: Pageable,
    ) = PageResponse.of(
        foundReportUseCase.getAssigned(user.shelterSeq, AssignedFilter(status, period, districtCode, speciesSeq, rescueStatus), pageable)
            .map(AssignedFoundReportResponse::from),
    )

    /** 8.2 상세 */
    @Operation(
        summary = "발견 신고 상세",
        description = "- 로그인 필요(USER·SHELTER 모두)\n" +
            "- 없음·삭제·탈퇴한 회원의 신고는 404.\n" +
            "- `reporterPhone`: 공개(`isPhonePublic=true`)면 모든 로그인 사용자, 비공개면 작성자와 이 신고의 담당 보호소에만 값을 준다. 그 외에는 null.\n" +
            "- `isBookmarked`는 조회자 기준. SHELTER는 항상 false.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FoundDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = DETAIL_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "foundReportSeq가 숫자가 아님",
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
                    examples = [ExampleObject(name = "FOUND_REPORT_NOT_FOUND", value = FOUND_REPORT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{foundReportSeq}")
    fun get(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "발견 신고 번호", example = "210") @PathVariable foundReportSeq: Long,
    ) = FoundReportResponse.from(foundReportUseCase.get(user, foundReportSeq))

    /** 8.4 등록·담당 보호소 자동 배정 */
    @Operation(
        summary = "발견 신고 등록",
        description = "- USER만\n" +
            "- 사진은 이미지 업로드 API로 먼저 올리고 받은 `imageSeq` 1~3개를 보낸다. 배열 순서가 표시 순서다.\n" +
            "- 담당 보호소는 서버가 정한다. `shelterSeq`를 보내면 값과 관계없이 400.\n" +
            "- 배정: 좌표를 역지오코딩해 `districtCode`를 구하고, 그 시·군·구의 지정 보호소(DISTRICT). 없거나 역지오코딩이 실패하면 가장 가까운 보호소(NEAREST). 보호소가 하나도 없을 때만 409.\n" +
            "- 역지오코딩이 실패해도 `districtCode=null`로 등록한다.\n" +
            "- 등록되면 담당 보호소 계정에 알림을 보내고 AI 매칭을 요청한다.\n" +
            "- 검사 순서: 필수값·형식 → shelterSeq → 조합 → 사진(개수·중복·존재·소유) → 보호소 배정.\n" +
            "- 응답 message는 `발견 신고가 등록되었습니다.`",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "등록 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = RegisteredFoundSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "등록 완료",
                            value = """{"status":201,"code":"SUCCESS","message":"발견 신고가 등록되었습니다.","data":{"foundReportSeq":210,"status":"ACTIVE","districtCode":"27110","assignedShelterSeq":7,"assignmentMethod":"DISTRICT"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락, 길이·좌표 범위 초과, shelterSeq 전송, 사진 개수(1~3)·중복, 본문을 읽을 수 없음(enum·날짜 형식 오류 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = REGISTER_REQUIRED_MISSING),
                        ExampleObject(name = "길이·범위 오류", value = REGISTER_FORMAT_INVALID),
                        ExampleObject(name = "shelterSeq 전송", value = SHELTER_SEQ_REJECTED),
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
        ApiResponse(
            responseCode = "409",
            description = "배정할 보호소가 하나도 없음(보호소 시드 전 환경). 재시도해도 같다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE", value = FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE)],
                ),
            ],
        ),
    )
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.CREATED)
    @SuccessMessage("발견 신고가 등록되었습니다.")
    fun register(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: RegisterFoundReportRequest) =
        foundReportUseCase.register(user.userSeq, request.toCommand()).let {
            RegisteredFoundReportResponse(it.foundReportSeq, it.status, it.districtCode, it.assignedShelterSeq, it.assignmentMethod.name)
        }

    /** 8.5 작성자 부분 수정 */
    @Operation(
        summary = "발견 신고 수정",
        description = "- USER만. 작성자 본인만(남의 신고는 404)\n" +
            "- 보낸 필드만 바꾼다. `age`·`weight`·`isNeutered`·`microchipNumber`·`description`·`landmarkDescription`은 null을 보내면 지운다. 나머지 필드에 null·빈 값을 보내면 400.\n" +
            "- 좌표나 주소가 바뀌면 `districtCode`를 다시 구한다. 담당 보호소는 다시 배정하지 않는다.\n" +
            "- `imageSeqs`는 최종 배열 전체(1~3개). 빠진 사진은 연결을 풀고, 사진 구성이 바뀌면 AI 재매칭을 요청한다(순서만 바뀌면 안 함).\n" +
            "- `shelterSeq`를 보내면 400.\n" +
            "- 종료(CLOSED)된 신고는 수정할 수 없다(409). 먼저 상태 변경(8.6)으로 재개한다.\n" +
            "- 검사 순서: 신고 확인(404·409) → 값 검사(400) → 조합 → 사진. 응답은 상세(8.2)와 같다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "수정 완료. 수정된 상세를 준다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FoundDetailSuccessSchema::class),
                    examples = [ExampleObject(name = "수정 완료", value = DETAIL_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수 필드에 null·빈 값, 길이·좌표 범위 초과, shelterSeq 전송, 사진 개수(1~3)·중복, 경로 타입 오류, 본문을 읽을 수 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수 필드 null·빈 값", value = UPDATE_REQUIRED_NULL),
                        ExampleObject(name = "길이 초과", value = UPDATE_LENGTH_EXCEEDED),
                        ExampleObject(name = "좌표 범위 오류", value = UPDATE_LATITUDE_OUT_OF_RANGE),
                        ExampleObject(name = "shelterSeq 전송", value = SHELTER_SEQ_REJECTED),
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
            description = "SHELTER 계정, 정지된 계정, 또는 남이 올린 사진",
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
            description = "신고가 없음·삭제됨·남의 신고, 없는 조합 번호, 없는 사진 번호",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "FOUND_REPORT_NOT_FOUND", value = FOUND_REPORT_NOT_FOUND),
                        ExampleObject(name = "CATALOG_COMBINATION_NOT_FOUND", value = CATALOG_COMBINATION_NOT_FOUND),
                        ExampleObject(name = "IMAGE_NOT_FOUND", value = IMAGE_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "종료(CLOSED)된 신고. 상태 변경(8.6)으로 재개한 뒤 수정한다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "FOUND_REPORT_CLOSED", value = FOUND_REPORT_CLOSED)],
                ),
            ],
        ),
    )
    @PatchMapping("/{foundReportSeq}")
    @PreAuthorize("hasRole('USER')")
    fun update(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "발견 신고 번호", example = "210") @PathVariable foundReportSeq: Long,
        @RequestBody request: UpdateFoundReportRequest,
    ) = FoundReportResponse.from(foundReportUseCase.update(user, foundReportSeq, request.toCommand()))

    /** 8.6 ACTIVE↔CLOSED. 작성자 또는 담당 SHELTER(종료만) */
    @Operation(
        summary = "발견 신고 상태 변경",
        description = "- 로그인 필요. 작성자(USER) 또는 이 신고의 담당 보호소(SHELTER)만. 그 외에는 404\n" +
            "- 작성자는 종료(CLOSED)·재개(ACTIVE) 모두, 담당 보호소는 종료만 할 수 있다. 보호소가 재개를 요청하면 403.\n" +
            "- 현재와 같은 상태로 바꾸면 400.\n" +
            "- 종료하면 `closedDttm`은 서버 시각, `closedReason`은 선택(빈 값은 null). 재개하면 둘 다 null로 지운다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "변경 완료",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FoundStatusSuccessSchema::class),
                    examples = [
                        ExampleObject(
                            name = "종료",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"foundReportSeq":210,"status":"CLOSED","closedReason":"보호자가 직접 찾아감","closedDttm":"2026-10-30T09:00:00+09:00"},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
                        ),
                        ExampleObject(
                            name = "재개",
                            value = """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"foundReportSeq":210,"status":"ACTIVE","closedReason":null,"closedDttm":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}""",
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
            description = "담당 보호소가 재개(ACTIVE)를 요청, 또는 정지된 계정. 재시도하지 않는다",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "AUTH_FORBIDDEN(보호소 재개)", value = FailExamples.AUTH_FORBIDDEN),
                        ExampleObject(name = "AUTH_ACCOUNT_UNAVAILABLE", value = FailExamples.AUTH_ACCOUNT_UNAVAILABLE),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "404",
            description = "신고가 없음·삭제됨, 또는 작성자도 담당 보호소도 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "FOUND_REPORT_NOT_FOUND", value = FOUND_REPORT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @PatchMapping("/{foundReportSeq}/status")
    fun changeStatus(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "발견 신고 번호", example = "210") @PathVariable foundReportSeq: Long,
        @Valid @RequestBody request: ChangeFoundStatusRequest,
    ) = FoundStatusResponse.from(foundReportUseCase.changeStatus(user, foundReportSeq, request.status!!, request.closedReason))

    /** 8.7 논리 삭제 */
    @Operation(
        summary = "발견 신고 삭제",
        description = "- USER만. 작성자 본인만(남의 신고는 404)\n" +
            "- 논리 삭제. ACTIVE·CLOSED 모두 삭제할 수 있다. 삭제 후 목록·상세·담당 목록에 나오지 않는다.\n" +
            "- 연결된 구조 기록은 바꾸지 않는다.\n" +
            "- 이미 삭제된 신고를 다시 삭제하면 404.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "삭제 완료. 본문 없음"),
        ApiResponse(
            responseCode = "400",
            description = "foundReportSeq가 숫자가 아님",
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
            description = "SHELTER 계정 또는 정지된 계정. 재시도하지 않는다",
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
            description = "신고가 없음·이미 삭제됨·남의 신고",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "FOUND_REPORT_NOT_FOUND", value = FOUND_REPORT_NOT_FOUND)],
                ),
            ],
        ),
    )
    @DeleteMapping("/{foundReportSeq}")
    @PreAuthorize("hasRole('USER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "발견 신고 번호", example = "210") @PathVariable foundReportSeq: Long,
    ) {
        foundReportUseCase.delete(user.userSeq, foundReportSeq)
    }

    private companion object {
        // 문서 예시 전용
        const val SUMMARY_FIELDS =
            """"foundReportSeq":210,"classification":{"combinationSeq":88,"speciesSeq":1,"speciesCode":"DOG","speciesName":"개","breedSeq":12,"breedName":"말티즈","colorSeq":3,"colorName":"흰색"},"gender":"UNKNOWN","age":null,"isNeutered":null,"weight":"약 3kg","status":"ACTIVE","shelterSeq":7,"address":"대구광역시 중구 동인동1가","districtCode":"27110","occurredDttm":"2026-10-29T10:00:00+09:00","representativeImageUrl":"https://storage.example.com/images/1201.jpg","regDttm":"2026-10-29T12:00:00+09:00""""
        const val PAGE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{""" + SUMMARY_FIELDS +
                """}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val LIST_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":[{""" + SUMMARY_FIELDS +
                """}],"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val ASSIGNED_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{""" + SUMMARY_FIELDS +
                ""","reporter":{"name":"김가람","phone":"01012345678"},"rescueStatus":"PROCESSING","activeRescueSeq":31}],"page":0,"size":8,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{""" + SUMMARY_FIELDS +
                ""","reporter":{"userSeq":17,"displayName":"반려가족","role":"USER","profileImage":null},"shelter":{"shelterSeq":7,"shelterName":"대구 중구 동물보호센터"},"microchipNumber":null,"description":"목에 빨간 목줄","landmarkDescription":"공원 정문 앞","latitude":35.87,"longitude":128.6,"reporterPhone":null,"isPhonePublic":false,"closedReason":null,"closedDttm":null,"images":[{"imageSeq":1201,"imageUrl":"https://storage.example.com/images/1201.jpg","displayOrder":0}],"isBookmarked":false,"commentCount":2},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""

        const val STATUS_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val PERIOD_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"period","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SEQ_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"foundReportSeq","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DATE_REVERSED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"occurredFrom","reason":"시작일이 종료일보다 늦습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SORT_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: occurredDttm (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_INVALID_DISTRICT_CODE =
            """{"status":400,"code":"CATALOG_INVALID_DISTRICT_CODE","message":"존재하지 않는 행정구역 코드입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REGISTER_REQUIRED_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"combinationSeq","reason":"널이어서는 안됩니다"},{"field":"reporterPhone","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val REGISTER_FORMAT_INVALID =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"weight","reason":"크기가 0에서 30 사이여야 합니다"},{"field":"latitude","reason":"다음 값 이하여야 합니다 90"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SHELTER_SEQ_REJECTED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"shelterSeq","reason":"담당 보호소는 지정할 수 없습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATE_REQUIRED_NULL =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"address","reason":"필수 입력값입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATE_LENGTH_EXCEEDED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"weight","reason":"30자 이하입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val UPDATE_LATITUDE_OUT_OF_RANGE =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"latitude","reason":"위도는 -90~90입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_DUPLICATED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"imageSeqs","reason":"같은 사진이 중복되었습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_INVALID_COUNT =
            """{"status":400,"code":"IMAGE_INVALID_COUNT","message":"사진은 1~3장이어야 합니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_REQUIRED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"널이어서는 안됩니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SAME_STATUS =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"이미 CLOSED 상태입니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_OWNED =
            """{"status":403,"code":"IMAGE_NOT_OWNED","message":"본인이 올린 이미지만 사용할 수 있습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val FOUND_REPORT_NOT_FOUND =
            """{"status":404,"code":"FOUND_REPORT_NOT_FOUND","message":"발견 신고를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val CATALOG_COMBINATION_NOT_FOUND =
            """{"status":404,"code":"CATALOG_COMBINATION_NOT_FOUND","message":"존재하지 않는 종·품종·색상 조합입니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val IMAGE_NOT_FOUND =
            """{"status":404,"code":"IMAGE_NOT_FOUND","message":"이미지를 찾을 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val FOUND_REPORT_CLOSED =
            """{"status":409,"code":"FOUND_REPORT_CLOSED","message":"종료된 발견 신고는 수정할 수 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE =
            """{"status":409,"code":"FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE","message":"배정할 수 있는 보호소가 없습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
