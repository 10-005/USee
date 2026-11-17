package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_EQUIPMENT_MISSING
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_ITEM_NOT_IN_INVENTORY
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_IN_PROGRESS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_POOP_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_WALK_ALREADY_IN_PROGRESS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_WALK_NOT_FOR_SPECIES
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_WALK_NOT_FOUND
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_WALK_NOT_IN_PROGRESS
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.WALK_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.application.port.`in`.WalkUseCase
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 18.14~18.17 산책. 본인만 하며 그 외(보호소 포함)는 404로 감추므로 역할 제한을 두지 않는다(Q483) */
@Tag(
    name = "모의 입양 산책",
    description = "강아지 세션 전용 산책(Q488). 하네스와 리드줄이 있어야 시작하고(Q495), 앱은 60초마다 하트비트를 보낸다. " +
        "산책 중 돌발 이벤트는 하트비트 응답으로 오고(푸시 없음), 물·간식·배변 치우기로 해결한다. 효과는 종료 때 산책 길이로 한 번에 들어간다",
)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}/walks")
class WalkController(
    private val walkUseCase: WalkUseCase,
) {

    @Operation(
        summary = "산책 시작",
        description = "- 본인 진행 중 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`\n" +
            "- 강아지 세션만(고양이 409 `SIMULATION_WALK_NOT_FOR_SPECIES`, 앱은 버튼을 끈다). 하네스와 리드줄을 보유해야 한다" +
            "(없으면 409 `SIMULATION_EQUIPMENT_MISSING`, 세션 상세 `equipment.leash`로 버튼 활성 여부를 정한다). 이미 산책 중이면 409.\n" +
            "- 본문은 없다. 행동 이력에 WALK_START가 남고 마지막 행동 시각이 갱신된다.\n" +
            "- 산책하는 동안에는 집 돌발 이벤트가 생기지 않는다(산책 이벤트만).\n" +
            "- 앱은 `heartbeatIntervalSeconds`(60초, 가안)마다 하트비트를 보낸다. 10분 넘게 끊기면 마지막 하트비트 시각으로 자동 종료된다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "시작 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = WalkStartSuccessSchema::class),
                    examples = [ExampleObject(name = "시작 성공", value = START_SUCCESS)],
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
            description = "진행 중이 아닌 세션, 고양이 세션, 하네스와 리드줄 없음, 이미 산책 중",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS),
                        ExampleObject(name = "SIMULATION_WALK_NOT_FOR_SPECIES", value = SIMULATION_WALK_NOT_FOR_SPECIES),
                        ExampleObject(name = "SIMULATION_EQUIPMENT_MISSING", value = SIMULATION_EQUIPMENT_MISSING),
                        ExampleObject(name = "SIMULATION_WALK_ALREADY_IN_PROGRESS", value = SIMULATION_WALK_ALREADY_IN_PROGRESS),
                    ],
                ),
            ],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun start(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
    ) = WalkStartResponse.from(walkUseCase.start(user.userSeq, sessionSeq))

    @Operation(
        summary = "산책 하트비트",
        description = "- 산책 중 `heartbeatIntervalSeconds`마다 보낸다. 본문 없음.\n" +
            "- 세션을 지금 시각까지 진행시키고(시한이 지난 산책 이벤트는 방치 효과 후 만료) 산책 이벤트를 확률로 만든다" +
            "(하트비트마다 8%, 산책당 최대 3회, 해결 시한 5분, 가안).\n" +
            "- 새 이벤트는 `newEvent`, 이미 대기 중인 이벤트는 `pendingEvent`로 준다(둘 중 하나만). 푸시는 보내지 않는다.\n" +
            "- 정답 행동은 알려 주지 않는다(`scenario.description`이 힌트). 산책 중 행동으로 해결한다.\n" +
            "- 하트비트가 10분 넘게 끊긴 뒤 보내면 마지막 하트비트 시각으로 산책을 끝내고 409 `SIMULATION_WALK_NOT_IN_PROGRESS`.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "하트비트 처리",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = WalkHeartbeatSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "새 산책 이벤트", value = HEARTBEAT_NEW_EVENT),
                        ExampleObject(name = "이벤트 없음", value = HEARTBEAT_QUIET),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "세션·산책 번호가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH),
                        ExampleObject(name = "산책 번호 타입 오류", value = WALK_SEQ_TYPE_MISMATCH),
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
            responseCode = "404",
            description = "없는 세션·남의 세션, 없는 산책·다른 세션의 산책",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND),
                        ExampleObject(name = "SIMULATION_WALK_NOT_FOUND", value = SIMULATION_WALK_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "진행 중이 아닌 세션, 끝났거나 하트비트가 끊겨 자동 종료된 산책",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS),
                        ExampleObject(name = "SIMULATION_WALK_NOT_IN_PROGRESS", value = SIMULATION_WALK_NOT_IN_PROGRESS),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/{walkSeq}/heartbeat")
    fun heartbeat(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Parameter(description = "산책 번호", example = "8001") @PathVariable walkSeq: Long,
    ) = WalkHeartbeatResponse.from(walkUseCase.heartbeat(user.userSeq, sessionSeq, walkSeq))

    @Operation(
        summary = "산책 중 행동",
        description = "- 지원 행동:\n" +
            "  - `GIVE_WATER`: 물 주기. 물은 무료, 수분 +20(가안), `itemSeq` 생략\n" +
            "  - `GIVE_SNACK`: 간식 주기. 간식(SNACK) 1개, `itemSeq` 필수, 효과는 간식 효과\n" +
            "  - `CLEAN_UP_POOP`: 배변 치우기. **이 산책에 배변 이벤트가 있을 때만** 배변봉투 1개를 쓴다(없으면 409 `SIMULATION_POOP_NOT_FOUND`, " +
            "봉투는 쓰지 않음). `itemSeq` 생략하면 보유한 첫 배변봉투, 배변봉투가 없으면 409 `SIMULATION_ITEM_NOT_IN_INVENTORY`\n" +
            "- 그 밖의 코드는 400.\n" +
            "- 이 산책의 대기 이벤트 정답이면 해결 효과를 더하고 `resolvedEvent`에 결과를 준다(18.6과 같은 형식). 오답은 패널티 없음.\n" +
            "- 돈은 쓰지 않는다. 행동 이력(WALK)이 1건 남고 마지막 행동 시각이 갱신된다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "행동 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = WalkActionSuccessSchema::class),
                    examples = [
                        ExampleObject(name = "물 주기(헥헥 이벤트 해결)", value = WATER_RESOLVED),
                        ExampleObject(name = "배변 치우기", value = POOP_RESOLVED),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "필수값 누락·산책 행동이 아닌 코드·간식 미선택·분류가 맞지 않는 물품, 본문 형식 오류, 번호 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "산책 행동이 아님", value = NOT_WALK_ACTION),
                        ExampleObject(name = "COMMON_INVALID_JSON", value = FailExamples.COMMON_INVALID_JSON),
                        ExampleObject(name = "산책 번호 타입 오류", value = WALK_SEQ_TYPE_MISMATCH),
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
            responseCode = "404",
            description = "없는 세션·남의 세션, 없는 산책, 없는 물품",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND),
                        ExampleObject(name = "SIMULATION_WALK_NOT_FOUND", value = SIMULATION_WALK_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "진행 중이 아닌 세션·산책, 치울 배변 없음, 소모품 없음",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_WALK_NOT_IN_PROGRESS", value = SIMULATION_WALK_NOT_IN_PROGRESS),
                        ExampleObject(name = "SIMULATION_POOP_NOT_FOUND", value = SIMULATION_POOP_NOT_FOUND),
                        ExampleObject(name = "SIMULATION_ITEM_NOT_IN_INVENTORY", value = SIMULATION_ITEM_NOT_IN_INVENTORY),
                        ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/{walkSeq}/actions")
    fun act(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Parameter(description = "산책 번호", example = "8001") @PathVariable walkSeq: Long,
        @Valid @RequestBody request: WalkActionRequest,
    ) = WalkActionResponse.from(walkUseCase.act(user.userSeq, sessionSeq, walkSeq, request.toCommand()))

    @Operation(
        summary = "산책 종료",
        description = "- 본문 없음. 이 산책에 대기 중인 이벤트는 방치 효과를 넣고 만료한다(그 결과 건강 0이면 세션이 FAILED로 끝난다).\n" +
            "- 산책 길이로 효과를 한 번에 반영한다: 10분마다 활력 +7·안정감 +4·친밀도 +1·포만감 -2·수분 -3·청결도 -2(가안), " +
            "60분까지만 인정하고 10분 미만이면 효과 없음. 성격 배율은 없다(Q490).\n" +
            "- 하트비트가 10분 넘게 끊긴 뒤 누르면 마지막 하트비트 시각으로 끝낸다(길이도 거기까지).\n" +
            "- 행동 이력에 WALK_END가 남는다.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "종료 성공",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = WalkEndSuccessSchema::class),
                    examples = [ExampleObject(name = "35분 산책", value = END_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "세션·산책 번호가 숫자가 아님",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [ExampleObject(name = "산책 번호 타입 오류", value = WALK_SEQ_TYPE_MISMATCH)],
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
            description = "없는 세션·남의 세션, 없는 산책",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_FOUND", value = SIMULATION_NOT_FOUND),
                        ExampleObject(name = "SIMULATION_WALK_NOT_FOUND", value = SIMULATION_WALK_NOT_FOUND),
                    ],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "409",
            description = "진행 중이 아닌 세션, 이미 끝난 산책",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "SIMULATION_NOT_IN_PROGRESS", value = SIMULATION_NOT_IN_PROGRESS),
                        ExampleObject(name = "SIMULATION_WALK_NOT_IN_PROGRESS", value = SIMULATION_WALK_NOT_IN_PROGRESS),
                    ],
                ),
            ],
        ),
    )
    @PostMapping("/{walkSeq}/end")
    fun end(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Parameter(description = "산책 번호", example = "8001") @PathVariable walkSeq: Long,
    ) = WalkEndResponse.from(walkUseCase.end(user.userSeq, sessionSeq, walkSeq))

    private companion object {
        // 문서 예시 전용
        const val STATS =
            """{"satiety":64,"hydration":58,"cleanliness":77,"bond":31,"calmness":46,"health":90,"energy":55,"calculatedDttm":"2026-11-01T17:00:00+09:00"}"""
        const val START_SUCCESS =
            """{"status":201,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"walkSeq":8001,"status":"IN_PROGRESS",""" +
                """"startedDttm":"2026-11-01T17:00:00+09:00","heartbeatIntervalSeconds":60,"stats":""" + STATS + """},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val HEARTBEAT_NEW_EVENT =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"walkSeq":8001,"elapsedMinutes":12,"stats":""" + STATS + ""","newEvent":""" +
                """{"occurrenceSeq":90010,"scenario":{"scenarioSeq":16,"title":"길에서 똥을 쌌어요","description":"산책하다 길 한가운데서 볼일을 봤어요. 그냥 두고 갈 수는 없죠.",""" +
                """"triggerContext":"WALK","triggerType":"RANDOM"},"vetVisit":null,"walkSeq":8001,"status":"PENDING","occurredDttm":"2026-11-01T17:12:00+09:00",""" +
                """"expiresDttm":"2026-11-01T17:17:00+09:00","resolvedDttm":null,"resolvedActionCode":null,"resultText":null},"pendingEvent":null},""" +
                """"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val HEARTBEAT_QUIET =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"walkSeq":8001,"elapsedMinutes":3,"stats":""" + STATS +
                ""","newEvent":null,"pendingEvent":null},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val WATER_RESOLVED =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50010,"actionType":"WALK","actionCode":"GIVE_WATER","walkSeq":8001,""" +
                """"consumedItem":null,"statDelta":{"hydration":30},"stats":""" + STATS + ""","balance":118000,""" +
                """"resolvedEvent":{"occurrenceSeq":90011,"scenarioTitle":"헥헥거려요","resultText":"물을 마시고 다시 기운을 차렸어요.",""" +
                """"eventStatDelta":{"hydration":10},"eventCost":0}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val POOP_RESOLVED =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"actionLogSeq":50011,"actionType":"WALK","actionCode":"CLEAN_UP_POOP","walkSeq":8001,""" +
                """"consumedItem":{"itemSeq":32,"itemName":"배변봉투","remainingQuantity":9},"statDelta":{"bond":2},"stats":""" + STATS + ""","balance":118000,""" +
                """"resolvedEvent":{"occurrenceSeq":90010,"scenarioTitle":"길에서 똥을 쌌어요","resultText":"봉투로 깨끗이 치웠어요. 매너 있는 산책이에요.",""" +
                """"eventStatDelta":{"bond":2},"eventCost":0}},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val END_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"walkSeq":8001,"status":"ENDED","startedDttm":"2026-11-01T17:00:00+09:00",""" +
                """"endedDttm":"2026-11-01T17:35:00+09:00","durationMinutes":35,""" +
                """"statDelta":{"energy":21,"calmness":12,"bond":3,"satiety":-6,"hydration":-9,"cleanliness":-6},"stats":""" + STATS + ""","eventCount":2},""" +
                """"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val NOT_WALK_ACTION =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"actionCode","reason":"산책 중에는 GIVE_WATER·GIVE_SNACK·CLEAN_UP_POOP만 할 수 있습니다: FEED"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
