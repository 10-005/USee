package com.ssafy.animal.simulation.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/** 발생 이벤트 상태(Q486). PENDING → RESOLVED(정답 행동) / EXPIRED(시한 경과·세션 종료) */
enum class OccurrenceStatus { PENDING, RESOLVED, EXPIRED }

/**
 * DB 명세 43. simulation_event_occurrences. 세션당 PENDING은 1개(부분 UNIQUE).
 * 같은 스키마 FK(session·scenario·walk·action_log)는 숫자 컬럼으로 둔다.
 */
@Entity
@Table(name = "simulation_event_occurrences")
class EventOccurrence(
    sessionSeq: Long,
    scenarioSeq: Long,
    occurredDttm: OffsetDateTime,
    expiresDttm: OffsetDateTime,
    walkSeq: Long? = null,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var occurrenceSeq: Long? = null
        protected set

    @Column(nullable = false)
    var sessionSeq: Long = sessionSeq
        protected set

    @Column(nullable = false)
    var scenarioSeq: Long = scenarioSeq
        protected set

    var walkSeq: Long? = walkSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OccurrenceStatus = OccurrenceStatus.PENDING
        protected set

    @Column(nullable = false)
    var occurredDttm: OffsetDateTime = occurredDttm
        protected set

    @Column(nullable = false)
    var expiresDttm: OffsetDateTime = expiresDttm
        protected set

    var resolvedDttm: OffsetDateTime? = null
        protected set

    /** 해결한 CARE·WALK 로그(로그를 먼저 저장한 뒤 채운다) */
    var resolvedActionLogSeq: Long? = null
        protected set

    val isPending: Boolean get() = status == OccurrenceStatus.PENDING

    /** at 시점에 해결 시한이 지났는지(시한 시각 포함) */
    fun isDueAt(at: OffsetDateTime): Boolean = isPending && !at.isBefore(expiresDttm)

    fun resolve(at: OffsetDateTime, actionLogSeq: Long) {
        check(isPending) { "대기 중인 이벤트만 해결합니다: $status" }
        status = OccurrenceStatus.RESOLVED
        resolvedDttm = at
        resolvedActionLogSeq = actionLogSeq
    }

    /** 시한 경과(방치 효과는 부르는 쪽이 세션에 반영) 또는 세션 종료(효과 없음, Q486) */
    fun expire() {
        check(isPending) { "대기 중인 이벤트만 만료합니다: $status" }
        status = OccurrenceStatus.EXPIRED
    }

    companion object {
        fun occur(session: SimulationSession, scenario: EventScenario, at: OffsetDateTime, expiresAt: OffsetDateTime, walkSeq: Long? = null) =
            EventOccurrence(
                sessionSeq = checkNotNull(session.sessionSeq) { "저장 전 세션입니다." },
                scenarioSeq = scenario.scenarioSeq,
                occurredDttm = at,
                expiresDttm = expiresAt,
                walkSeq = walkSeq,
            )
    }
}
