package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Duration
import java.time.OffsetDateTime

/**
 * DB 명세 44. simulation_reports. 세션 종료 시 1회 만들어 영구 보존한다(세션당 1건 UNIQUE, Q444).
 * summary는 커밋 후 비동기로 채운다(263). 그 전까지 null.
 */
@Entity
@Table(name = "simulation_reports")
class SimulationReport(
    sessionSeq: Long,
    expectedCost: Int,
    totalSpent: Int,
    spentByCategory: Map<String, Int>,
    dailySpent: Map<String, Int>,
    finalStats: Stats,
    actionCount: Int,
    eventCount: Int,
    eventResolvedCount: Int,
    walkCount: Int,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var reportSeq: Long? = null
        protected set

    @Column(nullable = false)
    var sessionSeq: Long = sessionSeq
        protected set

    @Column(nullable = false)
    var expectedCost: Int = expectedCost
        protected set

    @Column(nullable = false)
    var totalSpent: Int = totalSpent
        protected set

    /** 분류별 지출. PURCHASE는 물품 분류, EVENT(진료비)는 "EVENT" 키. 지출 없는 분류도 0으로 둔다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var spentByCategoryJsonb: Map<String, Int> = spentByCategory
        protected set

    /** 세션 1~7일차별 지출(키 "1"~"7") */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var dailySpentJsonb: Map<String, Int> = dailySpent
        protected set

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    var finalStatsJsonb: Map<String, Int> = finalStats.toMap()
        protected set

    /** 돌봄·산책 행동 수(SYSTEM·EVENT 기록 제외) */
    @Column(nullable = false)
    var actionCount: Int = actionCount
        protected set

    /** 발생한 돌발 이벤트 수(해결 + 만료) */
    @Column(nullable = false)
    var eventCount: Int = eventCount
        protected set

    /** 정답 행동으로 해결한 이벤트 수(Q486) */
    @Column(nullable = false)
    var eventResolvedCount: Int = eventResolvedCount
        protected set

    /** 산책 수. 고양이 세션은 항상 0(Q488) */
    @Column(nullable = false)
    var walkCount: Int = walkCount
        protected set

    /** LLM 요약(263에서 커밋 후 비동기로 채움). 그 전까지 null */
    @Column(columnDefinition = "text")
    var summary: String? = null
        protected set

    /** 예상 비용 - 총지출. 음수면 예상보다 더 썼다 */
    val difference: Int get() = expectedCost - totalSpent
}

/** 집계에 쓰는 지출 한 건(분류 키는 물품 분류 이름 또는 "EVENT") */
data class SpendRecord(
    val categoryKey: String,
    val amount: Int,
    val spentAt: OffsetDateTime,
)

/** 리포트 집계 재료. 지출·카운트는 종료 처리에서 읽어 온다 */
data class ReportSource(
    val session: SimulationSession,
    val spends: List<SpendRecord>,
    val actionCount: Int,
    val eventCount: Int,
    val eventResolvedCount: Int,
    val walkCount: Int,
)

/** 종료 리포트 집계(명세 18.18). 순수 계산이라 저장소를 모른다 */
object ReportAggregator {

    const val EVENT_KEY = "EVENT"

    /** 분류 키 순서: 물품 분류 선언 순서 + EVENT */
    val CATEGORY_KEYS: List<String> = ItemCategory.entries.map { it.name } + EVENT_KEY

    fun aggregate(source: ReportSource, sessionDays: Int): SimulationReport {
        val session = source.session
        val byCategory = CATEGORY_KEYS.associateWith { key -> source.spends.filter { it.categoryKey == key }.sumOf { it.amount } }
        val daily = (1..sessionDays).associate { day -> day.toString() to 0 }.toMutableMap()
        source.spends.forEach { spend ->
            val day = dayOf(session.startedDttm, spend.spentAt, sessionDays)
            daily[day.toString()] = daily.getValue(day.toString()) + spend.amount
        }
        return SimulationReport(
            sessionSeq = checkNotNull(session.sessionSeq),
            expectedCost = session.expectedCost,
            totalSpent = source.spends.sumOf { it.amount },
            spentByCategory = byCategory,
            dailySpent = daily,
            finalStats = session.stats(),
            actionCount = source.actionCount,
            eventCount = source.eventCount,
            eventResolvedCount = source.eventResolvedCount,
            walkCount = source.walkCount,
        )
    }

    /** 시작 시각 기준 n일차(1부터). 범위를 벗어나면 첫날·마지막 날로 붙인다 */
    fun dayOf(startedAt: OffsetDateTime, at: OffsetDateTime, sessionDays: Int): Int =
        (Duration.between(startedAt, at).toDays() + 1).toInt().coerceIn(1, sessionDays)
}
