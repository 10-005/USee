package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.application.port.`in`.ActionLogQueryUseCase
import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@Tag(name = CARE_TAG, description = CARE_TAG_DESC)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class ActionLogController(
    private val actionLogQueryUseCase: ActionLogQueryUseCase,
) {

    /** 18.7 행동 이력. 본인만 보며 그 외(보호소 포함)는 404로 감추므로 역할 제한을 두지 않는다(Q483). 정렬 regDttm만 */
    @Operation(
        summary = "행동 이력 조회",
        description = "- 본인 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`\n" +
            "- 돌봄·산책·이벤트 만료·세션 시작/종료 기록을 준다. `actionType`으로 거를 수 있다(생략 시 전체).\n" +
            "- `item`은 쓴 물품(없으면 null), `statDelta`는 실제로 바뀐 스탯(세션 시작 등 SYSTEM 기록은 null), `statsAfter`는 행동 직후 스탯 7종.\n" +
            "- `walkSeq`는 산책 중 행동, `occurrenceSeq`는 이 행동이 해결했거나(CARE·WALK) 만료된(EVENT) 돌발 이벤트. 해당 없으면 null.\n" +
            "- 종료된 세션도 조회할 수 있다. size 기본 50. sort 허용 필드는 `regDttm`만(기본 `regDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 기록이 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ActionLogPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = PAGE_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "없는 분류 값, 허용 밖 정렬 필드, 세션 번호 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "분류 값 오류", value = TYPE_MISMATCH),
                        ExampleObject(name = "정렬 필드 오류", value = SORT_NOT_ALLOWED),
                        ExampleObject(name = "세션 번호 타입 오류", value = SESSION_SEQ_TYPE_MISMATCH),
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
    @GetMapping("/action-logs")
    fun getActionLogs(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Parameter(description = "$ACTION_TYPE_DESC. 생략하면 전체", example = "CARE")
        @RequestParam(required = false) actionType: ActionType?,
        @PageableDefault(size = 50) pageable: Pageable,
    ) = PageResponse.of(actionLogQueryUseCase.getActionLogs(user.userSeq, sessionSeq, actionType, pageable).map(ActionLogResponse::from))

    private companion object {
        // 문서 예시 전용
        const val PAGE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[""" +
                """{"actionLogSeq":50001,"actionType":"CARE","actionCode":"FEED","item":{"itemSeq":22,"itemName":"균형 잡힌 사료"},"walkSeq":null,"occurrenceSeq":null,""" +
                """"statDelta":{"satiety":30,"bond":1},"statsAfter":{"satiety":94,"hydration":58,"cleanliness":77,"bond":32,"calmness":46,"health":90,"energy":55},"regDttm":"2026-11-01T17:10:00+09:00"},""" +
                """{"actionLogSeq":50000,"actionType":"SYSTEM","actionCode":"SESSION_START","item":null,"walkSeq":null,"occurrenceSeq":null,""" +
                """"statDelta":null,"statsAfter":{"satiety":70,"hydration":70,"cleanliness":80,"bond":30,"calmness":50,"health":90,"energy":60},"regDttm":"2026-11-01T14:00:00+09:00"}""" +
                """],"page":0,"size":50,"totalElements":2,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"actionType","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SORT_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: actionCode (허용: regDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}

data class ActionLogResponse(
    @field:Schema(description = "행동 이력 번호", example = "50001")
    val actionLogSeq: Long,
    @field:Schema(description = ACTION_TYPE_DESC, example = "CARE")
    val actionType: ActionType,
    @field:Schema(description = ACTION_CODE_DESC, example = "FEED")
    val actionCode: ActionCode,
    @field:Schema(description = "쓴 물품(소모품, 놀아 주기면 고른 장난감). 물품을 쓰지 않은 행동이면 null", nullable = true)
    val item: LoggedItem?,
    @field:Schema(description = "산책 중 행동이면 산책 번호, 아니면 null", nullable = true, example = "null")
    val walkSeq: Long?,
    @field:Schema(description = "CARE·WALK는 이 행동이 해결한 이벤트, EVENT(EVENT_EXPIRE)는 만료된 이벤트. 해당 없으면 null", nullable = true, example = "null")
    val occurrenceSeq: Long?,
    @field:Schema(description = "실제로 바뀐 스탯(키는 스탯 이름). SYSTEM 기록은 null", nullable = true, example = """{"satiety":30,"bond":1}""")
    val statDelta: Map<String, Int>?,
    @field:Schema(description = "행동 직후 스탯 7종", example = """{"satiety":94,"hydration":58,"cleanliness":77,"bond":32,"calmness":46,"health":90,"energy":55}""")
    val statsAfter: Map<String, Int>,
    @field:Schema(description = "행동 시각", example = "2026-11-01T17:10:00+09:00")
    val regDttm: OffsetDateTime?,
) {
    data class LoggedItem(
        @field:Schema(description = "물품 번호", example = "22")
        val itemSeq: Long,
        @field:Schema(description = "물품 이름", example = "균형 잡힌 사료")
        val itemName: String,
    )

    companion object {
        fun from(entry: ActionLogEntry) = with(entry.log) {
            ActionLogResponse(
                actionLogSeq = checkNotNull(actionLogSeq),
                actionType = actionType,
                actionCode = actionCode,
                item = entry.item?.let { LoggedItem(it.itemSeq, it.itemName) },
                walkSeq = walkSeq,
                occurrenceSeq = occurrenceSeq,
                statDelta = statDeltaJsonb,
                statsAfter = statsAfterJsonb,
                regDttm = regDttm,
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ActionLogPageSuccessSchema : SuccessResponseSchema<PageResponse<ActionLogResponse>>()
