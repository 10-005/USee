package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationWalk
import com.ssafy.animal.simulation.domain.Stat
import java.time.Duration

/**
 * 18.14~18.17 산책(본인 전용, 개 세션만, S15P31D106-252). 모두 세션 행을 잠그고 지금 시각까지 진행시킨 뒤 처리한다.
 * 하트비트·행동 때 하트비트가 설정 시간(10분 [가안]) 넘게 끊긴 산책이면 마지막 하트비트 시각으로 끝내고 409 SIMULATION_WALK_NOT_IN_PROGRESS.
 */
interface WalkUseCase {
    /** 18.14 시작. 고양이 세션 409, 이미 산책 중 409, 하네스와 리드줄 미보유 409(Q495) */
    fun start(userSeq: Long, sessionSeq: Long): WalkStartResult

    /** 18.15 하트비트. 산책 이벤트를 확률로 만들어 newEvent로, 이미 있던 대기 이벤트는 pendingEvent로 준다 */
    fun heartbeat(userSeq: Long, sessionSeq: Long, walkSeq: Long): WalkHeartbeatResult

    /** 18.16 산책 중 행동(GIVE_WATER·GIVE_SNACK·CLEAN_UP_POOP). 이 산책의 대기 이벤트 정답이면 해결한다 */
    fun act(userSeq: Long, sessionSeq: Long, walkSeq: Long, command: CareActionCommand): WalkActionResult

    /** 18.17 종료. 남은 산책 이벤트는 방치 효과 후 만료, 산책 길이로 효과를 한 번에 반영. 하트비트가 끊긴 뒤면 마지막 하트비트 시각으로 끝낸다 */
    fun end(userSeq: Long, sessionSeq: Long, walkSeq: Long): WalkEndResult
}

data class WalkStartResult(
    val walk: SimulationWalk,
    val session: SimulationSession,
    /** 앱이 보낼 하트비트 간격(설정값) */
    val heartbeatInterval: Duration,
)

data class WalkHeartbeatResult(
    val walk: SimulationWalk,
    val session: SimulationSession,
    val elapsedMinutes: Long,
    /** 이번 하트비트에서 새로 생긴 산책 이벤트 */
    val newEvent: EventView?,
    /** 새 이벤트가 없을 때 이미 대기 중인 이벤트(재전달) */
    val pendingEvent: EventView?,
)

/** 18.16 응답의 원본. 18.6 행동 결과 + 산책 번호 */
data class WalkActionResult(
    val walkSeq: Long,
    val action: CareActionResult,
)

data class WalkEndResult(
    val walk: SimulationWalk,
    val session: SimulationSession,
    val durationMinutes: Long,
    /** 산책 효과로 실제 바뀐 스탯(방치 효과는 빼고) */
    val statDelta: Map<Stat, Int>,
    /** 이 산책에서 생긴 이벤트 수 */
    val eventCount: Int,
)
