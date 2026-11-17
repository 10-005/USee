package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.`in`.WalkEndResult
import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationWalk
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime

/**
 * 산책 종료(18.17·하트비트 끊김 자동 종료). 산책 API와 스케줄러가 같이 쓴다.
 * 부르는 쪽이 세션 행을 잠그고 지금 시각까지 진행(catchUp)시킨 상태여야 한다.
 */
@Component
class WalkLifecycle(
    private val actionLogRepository: SimulationActionLogRepository,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val events: SessionEvents,
    private val terminator: SessionTerminator,
    private val properties: SimulationProperties,
) {

    /**
     * 이 산책의 대기 이벤트는 방치 효과를 넣고 만료(Q486) → 산책 길이(endedAt까지)로 효과 반영(10분 단위, 60분까지) →
     * ENDED·WALK_END 로그. 방치 효과로 건강이 0이 되면 산책 효과 없이 FAILED로 세션을 끝낸다(산책도 함께 닫힌다).
     */
    fun end(session: SimulationSession, walk: SimulationWalk, endedAt: OffsetDateTime): WalkEndResult {
        val walkSeq = checkNotNull(walk.walkSeq) { "저장 전 산책입니다." }
        val duration = Duration.between(walk.startedDttm, endedAt)
        events.pending(session)?.takeIf { it.occurrence.walkSeq == walkSeq }?.let { events.expireWithNeglect(session, it) }
        if (session.stats().health == 0) {
            terminator.end(session, SessionStatus.FAILED, endedAt)
            return WalkEndResult(walk, session, duration.toMinutes(), emptyMap(), occurrenceRepository.countByWalk(walkSeq))
        }

        val result = session.applyEffect(properties.walk.effectFor(duration))
        walk.end(endedAt)
        session.recordAction(endedAt)
        actionLogRepository.save(SimulationActionLog.walk(session, ActionCode.WALK_END, walkSeq, result))
        return WalkEndResult(walk, session, duration.toMinutes(), result.delta, occurrenceRepository.countByWalk(walkSeq))
    }

    /** 하트비트가 설정 시간 넘게 끊긴 산책이면 마지막 하트비트 시각으로 끝낸다. 끝냈으면 true */
    fun endIfStale(session: SimulationSession, walk: SimulationWalk, now: OffsetDateTime): Boolean {
        if (!walk.isStaleAt(now, properties.walk.autoEndAfter)) return false
        end(session, walk, walk.lastActiveAt)
        return true
    }
}
