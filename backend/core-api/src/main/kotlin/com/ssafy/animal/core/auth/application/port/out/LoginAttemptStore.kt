package com.ssafy.animal.core.auth.application.port.out

import java.time.Duration

/** LOCAL 로그인 시도 카운터(Q445). DB 상태 없이 TTL로만 해제된다. */
interface LoginAttemptStore {
    /**
     * 시도 1회를 원자적으로 기록하고 누적 횟수를 돌려준다.
     * 누적이 maxAttempts 이하일 때만 TTL을 window로 다시 건다(차단 중 시도로 차단이 늘어나지 않게).
     */
    fun record(loginId: String, maxAttempts: Int, window: Duration): Long

    fun clear(loginId: String)
}
