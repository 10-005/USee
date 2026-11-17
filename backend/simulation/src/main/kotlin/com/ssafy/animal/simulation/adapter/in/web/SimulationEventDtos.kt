package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.application.port.`in`.EventView
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

internal const val OCCURRENCE_STATUS_DESC = "이벤트 상태. PENDING: 해결 대기, RESOLVED: 정답 행동으로 해결, EXPIRED: 시한 경과(또는 세션 종료)"

/**
 * 18.12 SimulationEvent(18.2·18.4 pendingEvent도 같은 형식). 정답 행동은 해결 전에 내보내지 않는다(Q486).
 * 예외로 정답이 병원이면 vetVisit에 진료비를 미리 준다.
 */
@Schema(name = "SimulationEvent", description = "돌발 이벤트")
data class SimulationEventResponse(
    @field:Schema(description = "발생 이벤트 번호", example = "90001")
    val occurrenceSeq: Long,
    @field:Schema(description = "시나리오")
    val scenario: ScenarioResponse,
    @field:Schema(description = "정답이 병원(VET_VISIT)인 이벤트만 진료비를 담는다. 그 외 null", nullable = true)
    val vetVisit: VetVisitResponse?,
    @field:Schema(description = "산책 중 이벤트면 산책 번호, 집 이벤트면 null", nullable = true, example = "null")
    val walkSeq: Long?,
    @field:Schema(description = OCCURRENCE_STATUS_DESC, example = "PENDING")
    val status: OccurrenceStatus,
    @field:Schema(description = "발생 시각", example = "2026-11-02T09:00:00+09:00")
    val occurredDttm: OffsetDateTime,
    @field:Schema(description = "해결 시한. 집 6시간(가안)", example = "2026-11-02T15:00:00+09:00")
    val expiresDttm: OffsetDateTime,
    @field:Schema(description = "해결 시각. RESOLVED일 때만", nullable = true, example = "null")
    val resolvedDttm: OffsetDateTime?,
    @field:Schema(description = "해결한 행동 코드. RESOLVED일 때만(해결 전에는 정답을 숨긴다)", nullable = true, example = "null")
    val resolvedActionCode: ActionCode?,
    @field:Schema(
        description = "결과 문구. RESOLVED면 해결 결과(병원이면 진단서), EXPIRED면 정답을 알려 주는 방치 문구, PENDING이면 null",
        nullable = true,
        example = "null",
    )
    val resultText: String?,
) {
    @Schema(name = "SimulationEventScenario", description = "시나리오. description이 정답 행동의 힌트")
    data class ScenarioResponse(
        @field:Schema(description = "시나리오 번호", example = "1")
        val scenarioSeq: Long,
        @field:Schema(description = "제목", example = "물그릇을 엎었어요")
        val title: String,
        @field:Schema(description = "상황 설명(힌트)", example = "물그릇이 뒤집혀 바닥이 흥건해요. 목이 마를 텐데 어떡하죠?")
        val description: String,
        @field:Schema(description = "발생 장소. HOME: 집, WALK: 산책 중", example = "HOME")
        val triggerContext: TriggerContext,
        @field:Schema(description = "발생 유형. RANDOM: 무작위, CONDITION: 스탯 조건", example = "RANDOM")
        val triggerType: TriggerType,
    )

    @Schema(name = "SimulationVetVisit", description = "병원 진료 안내")
    data class VetVisitResponse(
        @field:Schema(description = "진료비(원). 병원 행동 시 잔액에서 빠진다", example = "30000")
        val cost: Int,
    )

    companion object {
        fun from(view: EventView) = with(view.occurrence) {
            val scenario = view.scenario
            SimulationEventResponse(
                occurrenceSeq = checkNotNull(occurrenceSeq),
                scenario = ScenarioResponse(scenario.scenarioSeq, scenario.title, scenario.description, scenario.triggerContext, scenario.triggerType),
                vetVisit = if (scenario.isVetVisit) VetVisitResponse(scenario.resolveCost) else null,
                walkSeq = walkSeq,
                status = status,
                occurredDttm = occurredDttm,
                expiresDttm = expiresDttm,
                resolvedDttm = resolvedDttm,
                resolvedActionCode = scenario.resolveActionCode.takeIf { status == OccurrenceStatus.RESOLVED },
                resultText = when (status) {
                    OccurrenceStatus.PENDING -> null
                    OccurrenceStatus.RESOLVED -> scenario.resultText
                    OccurrenceStatus.EXPIRED -> scenario.neglectText
                },
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class SimulationEventPageSuccessSchema : SuccessResponseSchema<PageResponse<SimulationEventResponse>>()
