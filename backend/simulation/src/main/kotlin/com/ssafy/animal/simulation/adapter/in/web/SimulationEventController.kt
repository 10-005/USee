package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.docs.FailExamples
import com.ssafy.animal.common.docs.FailResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SESSION_SEQ_TYPE_MISMATCH
import com.ssafy.animal.simulation.adapter.`in`.web.SimulationDocExamples.SIMULATION_NOT_FOUND
import com.ssafy.animal.simulation.application.port.`in`.EventQueryUseCase
import com.ssafy.animal.simulation.domain.OccurrenceStatus
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

/** 18.12 돌발 이벤트 목록. 본인만 보며 그 외(보호소 포함)는 404로 감추므로 역할 제한을 두지 않는다(Q483) */
@Tag(
    name = "모의 입양 돌발 이벤트",
    description = "집·산책 중에 생기는 돌발 이벤트. 시나리오마다 정답 행동이 하나 있고, 시한 안에 그 돌봄 행동을 하면 해결(행동 응답 resolvedEvent), " +
        "시한이 지나면 방치 효과가 들어가고 만료된다. 별도 응답 API는 없다",
)
@RestController
@RequestMapping("/api/simulation/v1/simulations/{sessionSeq}")
class SimulationEventController(
    private val eventQueryUseCase: EventQueryUseCase,
) {

    @Operation(
        summary = "돌발 이벤트 목록",
        description = "- 본인 세션만. 남의 세션·없는 세션은 보호소를 포함해 404 `SIMULATION_NOT_FOUND`\n" +
            "- 조회 전에 세션을 지금 시각까지 진행시킨다. 그 사이 시한이 지난 대기 이벤트는 방치 효과를 반영하고 EXPIRED로 바뀐다" +
            "(그 결과 건강 0이면 세션이 FAILED로 끝난다).\n" +
            "- 정답 행동은 해결 전에 알려 주지 않는다. `scenario.description`이 힌트이고, 정답이 병원이면 `vetVisit.cost`(진료비)만 미리 준다.\n" +
            "- `resultText`: RESOLVED면 해결 결과, EXPIRED면 정답을 알려 주는 방치 문구, PENDING이면 null. 세션 종료로 만료된 이벤트도 방치 문구를 준다(효과는 없음).\n" +
            "- 세션당 대기 이벤트는 최대 1개이고, 같은 대기 이벤트는 진행 중 조회·상세의 `pendingEvent`로도 온다.\n" +
            "- `status`로 거를 수 있다(생략 시 전체). size 기본 20. sort 허용 필드는 `occurredDttm`뿐(기본 `occurredDttm,desc`).",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "조회 성공. 이벤트가 없으면 content가 빈 배열",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = SimulationEventPageSuccessSchema::class),
                    examples = [ExampleObject(name = "조회 성공", value = PAGE_SUCCESS)],
                ),
            ],
        ),
        ApiResponse(
            responseCode = "400",
            description = "없는 상태 값, 허용 밖 정렬 필드, 세션 번호 타입 오류",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = FailResponseSchema::class),
                    examples = [
                        ExampleObject(name = "상태 값 오류", value = STATUS_TYPE_MISMATCH),
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
    @GetMapping("/events")
    fun getEvents(
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthUser,
        @Parameter(description = "세션 번호", example = "3001") @PathVariable sessionSeq: Long,
        @Parameter(description = "$OCCURRENCE_STATUS_DESC. 생략하면 전체", example = "PENDING")
        @RequestParam(required = false) status: OccurrenceStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ) = PageResponse.of(eventQueryUseCase.getEvents(user.userSeq, sessionSeq, status, pageable).map(SimulationEventResponse::from))

    private companion object {
        // 문서 예시 전용
        const val PAGE_SUCCESS =
            """{"status":200,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"content":[""" +
                """{"occurrenceSeq":90003,"scenario":{"scenarioSeq":8,"title":"축 처져 있어요","description":"밥을 잘 안 먹고 구석에서 움직이지 않아요. 평소와 달라 걱정돼요.",""" +
                """"triggerContext":"HOME","triggerType":"CONDITION"},"vetVisit":{"cost":30000},"walkSeq":null,"status":"PENDING",""" +
                """"occurredDttm":"2026-11-03T09:00:00+09:00","expiresDttm":"2026-11-03T15:00:00+09:00","resolvedDttm":null,"resolvedActionCode":null,"resultText":null},""" +
                """{"occurrenceSeq":90002,"scenario":{"scenarioSeq":3,"title":"화장실 밖에 실수했어요","description":"화장실이 마음에 안 들었는지 다른 곳에 실수를 했어요.",""" +
                """"triggerContext":"HOME","triggerType":"RANDOM"},"vetVisit":null,"walkSeq":null,"status":"EXPIRED",""" +
                """"occurredDttm":"2026-11-02T10:00:00+09:00","expiresDttm":"2026-11-02T16:00:00+09:00","resolvedDttm":null,"resolvedActionCode":null,""" +
                """"resultText":"화장실을 치워 줬어야 해요. 더러운 화장실을 피해 계속 실수했어요."},""" +
                """{"occurrenceSeq":90001,"scenario":{"scenarioSeq":1,"title":"물그릇을 엎었어요","description":"물그릇이 뒤집혀 바닥이 흥건해요. 목이 마를 텐데 어떡하죠?",""" +
                """"triggerContext":"HOME","triggerType":"RANDOM"},"vetVisit":null,"walkSeq":null,"status":"RESOLVED",""" +
                """"occurredDttm":"2026-11-01T16:00:00+09:00","expiresDttm":"2026-11-01T22:00:00+09:00","resolvedDttm":"2026-11-01T16:40:00+09:00",""" +
                """"resolvedActionCode":"WATER","resultText":"새 물을 받아 주니 꿀꺽꿀꺽 시원하게 마셨어요."}""" +
                """],"page":0,"size":20,"totalElements":3,"totalPages":1,"hasNext":false},"errors":[],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val STATUS_TYPE_MISMATCH =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"status","reason":"타입이 올바르지 않습니다."}],"traceId":"4f2c9e1a7b3d4c5e"}"""
        const val SORT_NOT_ALLOWED =
            """{"status":400,"code":"COMMON_INVALID_INPUT","message":"입력값이 올바르지 않습니다.","data":null,"errors":[{"field":"sort","reason":"정렬할 수 없는 필드입니다: expiresDttm (허용: occurredDttm)"}],"traceId":"4f2c9e1a7b3d4c5e"}"""
    }
}
