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
import java.time.Duration
import java.time.OffsetDateTime

/** 산책 상태(DB 40). 세션당 진행 중 1건(부분 UNIQUE) */
enum class WalkStatus { IN_PROGRESS, ENDED }

/** DB 명세 40. simulation_walks. 개 세션 전용(Q488), 하네스와 리드줄 보유 필요(Q495) */
@Entity
@Table(name = "simulation_walks")
class SimulationWalk(
    sessionSeq: Long,
    startedDttm: OffsetDateTime,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var walkSeq: Long? = null
        protected set

    @Column(nullable = false)
    var sessionSeq: Long = sessionSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: WalkStatus = WalkStatus.IN_PROGRESS
        protected set

    @Column(nullable = false)
    var startedDttm: OffsetDateTime = startedDttm
        protected set

    var lastHeartbeatDttm: OffsetDateTime? = null
        protected set

    var endedDttm: OffsetDateTime? = null
        protected set

    val isInProgress: Boolean get() = status == WalkStatus.IN_PROGRESS

    /** 마지막으로 살아 있던 시각(하트비트가 없으면 시작 시각) */
    val lastActiveAt: OffsetDateTime get() = lastHeartbeatDttm ?: startedDttm

    /** 하트비트가 이 시간 넘게 끊겼는지(자동 종료 대상) */
    fun isStaleAt(at: OffsetDateTime, timeout: Duration): Boolean = isInProgress && at.isAfter(lastActiveAt.plus(timeout))

    fun heartbeat(at: OffsetDateTime) {
        check(isInProgress) { "진행 중인 산책만 하트비트를 받습니다: $status" }
        lastHeartbeatDttm = at
    }

    fun end(at: OffsetDateTime) {
        check(isInProgress) { "이미 끝난 산책입니다: $status" }
        require(!at.isBefore(startedDttm)) { "종료 시각이 시작보다 빠릅니다." }
        status = WalkStatus.ENDED
        endedDttm = at
    }

    /** 시작부터 until(보통 종료 시각)까지 분 */
    fun durationMinutes(until: OffsetDateTime): Long = Duration.between(startedDttm, until).toMinutes()

    companion object {
        fun start(session: SimulationSession, at: OffsetDateTime) =
            SimulationWalk(checkNotNull(session.sessionSeq) { "저장 전 세션입니다." }, at)
    }
}
