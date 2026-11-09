package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_DETAIL_JSON
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_ACTIVE_ALREADY_EXISTS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_BREED_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_IN_PROGRESS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_SPECIES_NOT_SUPPORTED
import com.ssafy.animal.simulation.application.port.`in`.AbandonSimulationUseCase
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import com.ssafy.animal.simulation.domain.SessionStatus
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 18.1~18.5 모의 입양 세션. 시작·목록·진행 중은 USER, 상세·포기는 본인만(그 외 404, Q483) */
@Tag(
    name = "모의 입양 세션",
    description = "품종을 골라 7일 동안 돌보는 모의 입양 세션의 시작·진행 중 조회·내 목록·상세·포기. 회원당 진행 중 세션은 1개. " +
        "스탯 7종(satiety·hydration·cleanliness·bond·calmness·health·energy)은 모두 0~100, 높을수록 좋고 시간이 지나면 떨어진다",
)
@RestController
@RequestMapping("/api/simulation/v1/simulations")
class SimulationSessionController(
    private val startSimulationUseCase: StartSimulationUseCase,
    private val sessionQueryUseCase: SimulationSessionQueryUseCase,
    private val abandonUseCase: AbandonSimulationUseCase,
) {

    /** 18.1 세션 시작. 품종·가상 이름·예상 비용으로 시작하고 실제 보호동물과는 연결하지 않는다(Q482) */
    @Operation(
        summary = "모의 입양 시작",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 품종(`breedSeq`, 품종 목록 API 값)·가상 이름·일주일 예상 비용으로 시작한다. 실제 보호동물·보호소와는 연결하지 않는다.\n" +
            "- 종(DOG·CAT)과 성격은 품종에서 읽어 세션에 고정한다. 이후 품종 값이 바뀌어도 진행 중 세션은 그대로다.\n" +
            "- 종이 기타(ETC)인 품종은 시작할 수 없다(409).\n" +
            "- 잔액 = 예상 비용, 예정 종료 = 시작 + 7일. 스탯은 초기값(포만감 70·수분 70·청결도 80·친밀도 30·안정감 50·건강 90·활력 60, 가안).\n" +
            "- 인벤토리는 빈 채로 시작해 `equipment`는 모두 false, `pendingEvent`·`activeWalkSeq`는 null.\n" +
            "- `animalName`은 1~6자(한글만 쓰게 하는 것은 앱에서 검사). `expectedCost`는 0 이상 정수(원).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "시작 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SimulationSessionSuccessSchema::class),
                    examples = [ExampleObject(name = "시작 성공", value = START_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락·이름 길이 초과·음수 비용, 본문 형식 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "필수값 누락", value = START_REQUIRED_MISSING),
                        ExampleObject(name = "이름 6자 초과", value = NAME_TOO_LONG),
                        ExampleObject(name = "COMMON_INVALID_JSON", value = FailExamples.COMMON_INVALID_JSON),
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
            description = "보호소(SHELTER) 계정. 재시도하지 않는다",
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
            description = "없는 품종",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SIMULATION_BREED_NOT_FOUND", value = SIMULATION_BREED_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 진행 중인 세션이 있음(앱은 진행 중 조회로 이어가기), 기타(ETC) 종 품종",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_ACTIVE_ALREADY_EXISTS", value = SIMULATION_ACTIVE_ALREADY_EXISTS),
                        ExampleObject(name = "SIMULATION_SPECIES_NOT_SUPPORTED", value = SIMULATION_SPECIES_NOT_SUPPORTED),
                    ],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    fun start(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Valid @RequestBody request: StartSimulationRequest,
    ) = SimulationSessionResponse.from(startSimulationUseCase.start(user.userSeq, request.toCommand()))

    /** 18.2 진행 중 세션(스탯 지연 계산 반영). 없으면 data:null */
    @Operation(
        summary = "진행 중 모의 입양 조회",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 진행 중 세션이 없으면 404가 아니라 200 `data: null`. 앱은 리포트 목록으로 이동한다.\n" +
            "- 조회 시점까지 지난 시간(1시간 단위)만큼 스탯을 계산해 저장한 뒤 돌려준다. `stats.calculatedDttm`은 마지막으로 반영한 시각.\n" +
            "- 같은 1시간 안에서 여러 번 불러도 스탯이 더 떨어지지 않는다.\n" +
            "- `equipment`는 수량 1 이상 보유한 기구(밥그릇·물그릇·화장실·장난감), `activeWalkSeq`는 진행 중 산책 번호(없으면 null).\n" +
            "- `pendingEvent`는 해결 대기 중인 돌발 이벤트(없으면 null, 형식은 돌발 이벤트 목록과 같음). " +
            "시한이 지났으면 이 조회에서 방치 효과를 반영하고 만료돼 null이 된다.\n" +
            "- 건강이 0이 됐으면 FAILED(돌봄 부족, 종료 시각 = 건강이 0이 된 시각), 예정 종료가 지났으면 COMPLETED(종료 시각 = 예정 종료)로 " +
            "그 자리에서 끝내고 리포트를 만든다. 이렇게 끝난 세션은 **이번 응답에서 한 번만** 바뀐 `status`로 돌려주고, 다음 조회부터는 `data: null`. " +
            "앱은 `status`가 IN_PROGRESS가 아니면 리포트 화면으로 이동한다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 진행 중 세션이 없으면 data가 null",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SimulationSessionSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "진행 중 세션 있음", value = DETAIL_SUCCESS),
                        ExampleObject(name = "진행 중 세션 없음", value = NO_ACTIVE_SUCCESS),
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
    @GetMapping("/me/active")
    @PreAuthorize("hasRole('USER')")
    fun getActive(@Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser): SimulationSessionResponse? =
        sessionQueryUseCase.getActive(user.userSeq)?.let(SimulationSessionResponse::from)

    /** 18.3 내 세션 목록(종료 포함). 필터 status, 정렬 startedDttm(기본 desc)·endedDttm */
    @Operation(
        summary = "내 모의 입양 목록",
        description = "- USER만(보호소 계정은 403 `AUTH_FORBIDDEN`)\n" +
            "- 진행 중·종료 세션을 모두 준다. `status`로 거를 수 있다(생략 시 전체).\n" +
            "- 목록에는 스탯이 없어 시간 경과 계산을 하지 않는다. 스탯은 상세·진행 중 조회로 본다.\n" +
            "- `hasReport`: 종료 리포트가 있는지.\n" +
            "- size 기본 20. sort 허용 필드는 `startedDttm`(기본 `startedDttm,desc`)·`endedDttm`. " +
            "`endedDttm,desc`면 진행 중 세션(null)이 앞에 온다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 세션이 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SimulationSessionSummaryPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = PAGE_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "없는 상태 값, 허용 밖 정렬 필드",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "상태 값 오류", value = STATUS_TYPE_MISMATCH),
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
    fun getMySessions(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 상태. IN_PROGRESS: 진행 중, COMPLETED: 7일 완료, ABANDONED: 포기, FAILED: 돌봄 부족. 생략하면 전체", example = "COMPLETED")
        @RequestParam(required = false) status: SessionStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(sessionQueryUseCase.getMySessions(user.userSeq, status, pageable).map(SimulationSessionSummaryResponse::from))

    /** 18.4 세션 상세. 본인만 보며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @Operation(
        summary = "모의 입양 상세",
        description = "- 본인 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`(존재를 감춤)\n" +
            "- 응답은 진행 중 조회와 같은 형식이다.\n" +
            "- 진행 중이면 조회 시점까지 스탯을 계산해 저장하고, 건강 0·예정 종료 경과면 그 자리에서 FAILED·COMPLETED로 끝낸다. " +
            "종료된 세션은 종료 시점 값 그대로.\n" +
            "- 보호소는 세션 상세가 아니라 종료 리포트만 본다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SimulationSessionSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = DETAIL_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "세션 번호가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH)],
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
            description = "없는 세션·남의 세션(보호소 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND)],
                ),
            ],
        ),
    )
    @GetMapping("/{sessionSeq}")
    fun getDetail(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
    ) = SimulationSessionResponse.from(sessionQueryUseCase.getDetail(user.userSeq, sessionSeq))

    /** 18.5 중도 포기. 본인만 하며 그 외(보호소 포함)는 404로 존재를 감추므로 역할 제한을 두지 않는다(Q483) */
    @Operation(
        summary = "모의 입양 포기",
        description = "- 본인 진행 중 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`, 진행 중이 아니면 409\n" +
            "- 지금까지 지난 시간만큼 스탯을 반영한 뒤 ABANDONED로 끝내고 종료 리포트를 만든다. 본문은 없다.\n" +
            "- 그 사이 건강 0·예정 종료 경과로 이미 끝날 상태였다면 그 상태(FAILED·COMPLETED)로 끝내고 409를 준다(앱은 진행 중 조회로 리포트 이동).\n" +
            "- 진행 중 산책은 끝나고, 미해결 돌발 이벤트는 방치 효과 없이 만료된다.\n" +
            "- 응답 리포트의 `summary`는 null(비동기로 생성). 포기 뒤 새 세션을 시작할 수 있다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "포기 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AbandonSuccessSchema::class),
                    examples = [ExampleObject(name = "포기 성공", value = ABANDON_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "세션 번호가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH)],
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
            description = "없는 세션·남의 세션(보호소 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "이미 끝난 세션(이번에 예정 종료·건강 0으로 끝난 경우 포함)",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS)],
                ),
            ],
        ),
    )
    @PostMapping("/{sessionSeq}/abandon")
    fun abandon(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
    ) = AbandonResponse.from(abandonUseCase.abandon(user.userSeq, sessionSeq))

    private companion object {
        // 문서 예시 전용
        const val ABANDON_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"sessionSeq":3001,"status":"ABANDONED","endedDttm":"2026-11-03T10:20:00+09:00",""" +
                """"report":""" + SimulationDocExamples.ABANDONED_REPORT_JSON + """},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val START_SUCCESS =
            """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"sessionSeq":3001,"userSeq":17,"animalName":"초코","breed":""" +
                SimulationDocExamples.BREED_JSON +
                ""","speciesType":"DOG","personality":"FRIENDLY","status":"IN_PROGRESS","expectedCost":150000,"balance":150000,""" +
                """"startedDttm":"2026-11-01T14:00:00+09:00","endsDttm":"2026-11-08T14:00:00+09:00","endedDttm":null,""" +
                """"stats":{"satiety":70,"hydration":70,"cleanliness":80,"bond":30,"calmness":50,"health":90,"energy":60,"calculatedDttm":"2026-11-01T14:00:00+09:00"},""" +
                """"lastActionDttm":null,"equipment":{"foodBowl":false,"waterBowl":false,"litterBox":false,"toy":false},"pendingEvent":null,"activeWalkSeq":null},""" +
                """"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val DETAIL_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":""" + SESSION_DETAIL_JSON +
                ""","errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val NO_ACTIVE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":null,"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val PAGE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[{"sessionSeq":3001,"animalName":"초코","breed":""" +
                SimulationDocExamples.BREED_JSON +
                ""","speciesType":"DOG","personality":"FRIENDLY","status":"COMPLETED","expectedCost":150000,"balance":-32000,""" +
                """"startedDttm":"2026-11-01T14:00:00+09:00","endsDttm":"2026-11-08T14:00:00+09:00","endedDttm":"2026-11-08T14:00:00+09:00","hasReport":true}],""" +
                """"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""

        const val START_REQUIRED_MISSING =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"breedSeq","reason":"널이어서는 안됩니다"},{"field":"animalName","reason":"공백일 수 없습니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val NAME_TOO_LONG =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"animalName","reason":"크기가 0에서 6 사이여야 합니다"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SORT_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: regDttm (허용: startedDttm, endedDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
