package com.ssafy.animal.simulation.domain

import java.time.Duration
import java.time.OffsetDateTime

/**
 * 일차별 돌봄 집계 키(명세 18.18 Q517). 키마다 세는 행동 코드가 정해져 있고 목욕·약·병원·산책 배변 치우기는 세지 않는다.
 * 선언 순서가 응답·표의 행 순서다.
 */
enum class CareKey(val actionCodes: Set<ActionCode>) {
    FEED(setOf(ActionCode.FEED)),
    WATER(setOf(ActionCode.WATER, ActionCode.GIVE_WATER)),
    CLEAN_LITTER(setOf(ActionCode.CLEAN_LITTER)),
    PLAY(setOf(ActionCode.PLAY, ActionCode.SNACK, ActionCode.GIVE_SNACK, ActionCode.GIVE_CATNIP)),
    WALK(setOf(ActionCode.WALK_START)),
    ;

    companion object {
        private val BY_CODE: Map<ActionCode, CareKey> = entries.flatMap { key -> key.actionCodes.map { it to key } }.toMap()

        fun of(code: ActionCode): CareKey? = BY_CODE[code]

        /** 고양이 세션은 산책이 없어 WALK 키 자체가 없다(Q488) */
        fun keysFor(species: SpeciesType): List<CareKey> = if (species == SpeciesType.CAT) entries - WALK else entries
    }
}

/** 집계에 쓰는 돌발 이벤트 한 건(발생 + 시나리오) */
data class CareEventRecord(
    val title: String,
    val resolveActionCode: ActionCode,
    val status: OccurrenceStatus,
    val occurredAt: OffsetDateTime,
    val resolvedAt: OffsetDateTime?,
)

/** 일차별 집계 재료. logs는 세션의 CARE·WALK 행동 이력 */
data class CareSource(
    val startedAt: OffsetDateTime,
    val speciesType: SpeciesType,
    val evaluatedDays: Int,
    val sessionDays: Int,
    val logs: List<SimulationActionLog>,
    val events: List<CareEventRecord>,
    /** 리포트의 일차별 지출(키 "1"~"7") */
    val dailySpent: Map<String, Int>,
)

/** 그날 발생한 돌발 이벤트. minutesToResolve는 해결했을 때만 */
data class DayEvent(
    val title: String,
    val resolveActionCode: ActionCode,
    val status: OccurrenceStatus,
    val minutesToResolve: Long?,
)

data class DayCare(
    val day: Int,
    /** 그 세션 종의 키를 모두 채운다(없으면 0) */
    val counts: Map<CareKey, Int>,
    val spent: Int,
    /** 하루 최저 스탯(키는 스탯 컬럼명). 행동이 없는 날은 null */
    val lowestStats: Map<String, Int>?,
    val events: List<DayEvent>,
)

/**
 * 일차별 돌봄 집계 결과. days는 1~sessionDays 전부이고, evaluatedDays 뒤 일차는 0으로 채워져 이탈 판정에서 빠진다.
 * 이탈 표시: belowGuidelineDays(키별 권장 미달 일차), dangerZoneDays(스탯별 위험 구간 일차, 해당 없으면 키 없음)
 */
data class CareReport(
    val evaluatedDays: Int,
    val guideline: Map<CareKey, Int>,
    val days: List<DayCare>,
    val belowGuidelineDays: Map<CareKey, List<Int>>,
    val dangerZoneDays: Map<String, List<Int>>,
    val unresolvedEventCount: Int,
)

/** 일차별 돌봄 집계(Q517). 순수 계산이며 일차는 ReportAggregator.dayOf와 같은 규칙 */
object CareAggregator {

    fun aggregate(source: CareSource, guideline: Map<CareKey, Int>, penalty: HealthPenalty): CareReport {
        val keys = CareKey.keysFor(source.speciesType)
        val logsByDay = source.logs.groupBy { dayOf(source, checkNotNull(it.regDttm) { "저장 전 로그입니다." }) }
        val eventsByDay = source.events.groupBy { dayOf(source, it.occurredAt) }

        val days = (1..source.sessionDays).map { day ->
            val logs = if (day <= source.evaluatedDays) logsByDay[day].orEmpty() else emptyList()
            DayCare(
                day = day,
                counts = keys.associateWith { key -> logs.count { CareKey.of(it.actionCode) == key } },
                spent = source.dailySpent[day.toString()] ?: 0,
                lowestStats = lowestStats(logs),
                events = eventsByDay[day].orEmpty().map { it.toDayEvent() },
            )
        }
        val evaluated = days.take(source.evaluatedDays)
        return CareReport(
            evaluatedDays = source.evaluatedDays,
            guideline = keys.associateWith { guideline[it] ?: 0 },
            days = days,
            belowGuidelineDays = keys.associateWith { key -> evaluated.filter { it.counts.getValue(key) < (guideline[key] ?: 0) }.map { it.day } },
            dangerZoneDays = dangerZones(penalty)
                .mapValues { (stat, inDanger) -> evaluated.filter { day -> day.lowestStats?.get(stat.key)?.let(inDanger) == true }.map { it.day } }
                .filterValues { it.isNotEmpty() }
                .mapKeys { it.key.key },
            unresolvedEventCount = source.events.count { it.status == OccurrenceStatus.EXPIRED },
        )
    }

    /** 행동 직전(stats_after - stat_delta)과 직후 값 중 하루 최솟값 */
    private fun lowestStats(logs: List<SimulationActionLog>): Map<String, Int>? {
        if (logs.isEmpty()) return null
        return Stat.entries.associate { stat ->
            stat.key to logs.minOf { log ->
                val after = log.statsAfterJsonb[stat.key] ?: Stats.MAX
                minOf(after, after - (log.statDeltaJsonb?.get(stat.key) ?: 0))
            }
        }
    }

    /** 건강이 깎이는 구간(18.0 health-penalty 설정 그대로): 포만감·수분·청결도 미만, 안정감 이하 */
    private fun dangerZones(penalty: HealthPenalty): Map<Stat, (Int) -> Boolean> = mapOf(
        Stat.SATIETY to { v: Int -> v < penalty.basicNeedBelow },
        Stat.HYDRATION to { v: Int -> v < penalty.basicNeedBelow },
        Stat.CLEANLINESS to { v: Int -> v < penalty.basicNeedBelow },
        Stat.CALMNESS to { v: Int -> v <= penalty.calmnessAtOrBelow },
    )

    private fun CareEventRecord.toDayEvent() = DayEvent(
        title = title,
        resolveActionCode = resolveActionCode,
        status = status,
        minutesToResolve = resolvedAt?.takeIf { status == OccurrenceStatus.RESOLVED }?.let { Duration.between(occurredAt, it).toMinutes() },
    )

    private fun dayOf(source: CareSource, at: OffsetDateTime) = ReportAggregator.dayOf(source.startedAt, at, source.sessionDays)
}
