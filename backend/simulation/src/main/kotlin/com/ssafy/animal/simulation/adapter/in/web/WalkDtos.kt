package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.WalkActionResult
import com.ssafy.animal.simulation.application.port.`in`.WalkEndResult
import com.ssafy.animal.simulation.application.port.`in`.WalkHeartbeatResult
import com.ssafy.animal.simulation.application.port.`in`.WalkStartResult
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.WalkStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

internal const val WALK_STATUS_DESC = "산책 상태. IN_PROGRESS: 산책 중, ENDED: 끝남"

/** 18.14 시작 응답 */
data class WalkStartResponse(
    @field:Schema(description = "산책 번호", example = "8001")
    val walkSeq: Long,
    @field:Schema(description = WALK_STATUS_DESC, example = "IN_PROGRESS")
    val status: WalkStatus,
    @field:Schema(description = "시작 시각", example = "2026-11-01T17:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "하트비트 간격(초). 앱은 이 간격으로 18.15를 보낸다", example = "60")
    val heartbeatIntervalSeconds: Long,
    @field:Schema(description = "시작 시점 스탯")
    val stats: StatsResponse,
) {
    companion object {
        fun from(result: WalkStartResult) = WalkStartResponse(
            walkSeq = checkNotNull(result.walk.walkSeq),
            status = result.walk.status,
            startedDttm = result.walk.startedDttm,
            heartbeatIntervalSeconds = result.heartbeatInterval.seconds,
            stats = StatsResponse.of(result.session.stats(), result.session.statsCalculatedDttm),
        )
    }
}

/** 18.15 하트비트 응답 */
data class WalkHeartbeatResponse(
    @field:Schema(description = "산책 번호", example = "8001")
    val walkSeq: Long,
    @field:Schema(description = "시작 후 지난 분", example = "12")
    val elapsedMinutes: Long,
    @field:Schema(description = "지금 스탯")
    val stats: StatsResponse,
    @field:Schema(description = "이번 하트비트에서 새로 생긴 산책 이벤트(없으면 null). 정답 행동은 18.16으로 한다", nullable = true)
    val newEvent: SimulationEventResponse?,
    @field:Schema(description = "새 이벤트가 없을 때 이미 대기 중인 이벤트(없으면 null)", nullable = true)
    val pendingEvent: SimulationEventResponse?,
) {
    companion object {
        fun from(result: WalkHeartbeatResult) = WalkHeartbeatResponse(
            walkSeq = checkNotNull(result.walk.walkSeq),
            elapsedMinutes = result.elapsedMinutes,
            stats = StatsResponse.of(result.session.stats(), result.session.statsCalculatedDttm),
            newEvent = result.newEvent?.let(SimulationEventResponse::from),
            pendingEvent = result.pendingEvent?.let(SimulationEventResponse::from),
        )
    }
}

/** 18.16 요청. 산책 중 행동 3종 */
data class WalkActionRequest(
    @field:Schema(description = "산책 중 행동 코드. GIVE_WATER(물 주기), GIVE_SNACK(간식 주기), CLEAN_UP_POOP(배변 치우기)", example = "GIVE_WATER")
    @field:NotNull
    val actionCode: ActionCode?,
    @field:Schema(
        description = "쓸 물품 번호. GIVE_SNACK은 간식 필수, CLEAN_UP_POOP은 배변봉투(생략하면 보유한 첫 배변봉투), GIVE_WATER는 생략",
        nullable = true,
        example = "null",
    )
    val itemSeq: Long?,
) {
    fun toCommand() = CareActionCommand(actionCode!!, itemSeq)
}

/** 18.16 응답 = 18.6 행동 결과 + 산책 번호 */
data class WalkActionResponse(
    @field:Schema(description = "행동 이력 번호", example = "50010")
    val actionLogSeq: Long,
    @field:Schema(description = ACTION_TYPE_DESC, example = "WALK")
    val actionType: ActionType,
    @field:Schema(description = ACTION_CODE_DESC, example = "GIVE_WATER")
    val actionCode: ActionCode,
    @field:Schema(description = "산책 번호", example = "8001")
    val walkSeq: Long,
    @field:Schema(description = "쓴 소모품. GIVE_WATER면 null", nullable = true)
    val consumedItem: CareActionResponse.ConsumedItemResponse?,
    @field:Schema(description = "실제로 바뀐 스탯(행동 효과 + 이벤트 해결 효과 합산)", example = """{"hydration":30}""")
    val statDelta: Map<String, Int>,
    @field:Schema(description = "행동 직후 스탯")
    val stats: StatsResponse,
    @field:Schema(description = "현재 잔액(원). 산책 중 행동은 돈을 쓰지 않는다", example = "118000")
    val balance: Int,
    @field:Schema(description = "이 산책의 대기 이벤트 정답이었으면 해결 결과, 아니면 null", nullable = true)
    val resolvedEvent: CareActionResponse.ResolvedEventResponse?,
) {
    companion object {
        fun from(result: WalkActionResult) = CareActionResponse.from(result.action).let {
            WalkActionResponse(
                actionLogSeq = it.actionLogSeq,
                actionType = it.actionType,
                actionCode = it.actionCode,
                walkSeq = result.walkSeq,
                consumedItem = it.consumedItem,
                statDelta = it.statDelta,
                stats = it.stats,
                balance = it.balance,
                resolvedEvent = it.resolvedEvent,
            )
        }
    }
}

/** 18.17 종료 응답 */
data class WalkEndResponse(
    @field:Schema(description = "산책 번호", example = "8001")
    val walkSeq: Long,
    @field:Schema(description = WALK_STATUS_DESC, example = "ENDED")
    val status: WalkStatus,
    @field:Schema(description = "시작 시각", example = "2026-11-01T17:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "종료 시각. 하트비트가 끊긴 뒤면 마지막 하트비트 시각", nullable = true, example = "2026-11-01T17:35:00+09:00")
    val endedDttm: OffsetDateTime?,
    @field:Schema(description = "산책 길이(분)", example = "35")
    val durationMinutes: Long,
    @field:Schema(
        description = "산책 효과로 실제 바뀐 스탯(10분 단위, 60분까지, 10분 미만이면 빈 객체). 남은 이벤트의 방치 효과는 빠진다",
        example = """{"energy":21,"calmness":12,"bond":3,"satiety":-6,"hydration":-9,"cleanliness":-6}""",
    )
    val statDelta: Map<String, Int>,
    @field:Schema(description = "종료 후 스탯")
    val stats: StatsResponse,
    @field:Schema(description = "이 산책에서 생긴 이벤트 수", example = "2")
    val eventCount: Int,
) {
    companion object {
        fun from(result: WalkEndResult) = WalkEndResponse(
            walkSeq = checkNotNull(result.walk.walkSeq),
            status = result.walk.status,
            startedDttm = result.walk.startedDttm,
            endedDttm = result.walk.endedDttm,
            durationMinutes = result.durationMinutes,
            statDelta = result.statDelta.entries.associate { (stat, value) -> stat.key to value },
            stats = StatsResponse.of(result.session.stats(), result.session.statsCalculatedDttm),
            eventCount = result.eventCount,
        )
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class WalkStartSuccessSchema : SuccessResponseSchema<WalkStartResponse>()

abstract class WalkHeartbeatSuccessSchema : SuccessResponseSchema<WalkHeartbeatResponse>()

abstract class WalkActionSuccessSchema : SuccessResponseSchema<WalkActionResponse>()

abstract class WalkEndSuccessSchema : SuccessResponseSchema<WalkEndResponse>()
