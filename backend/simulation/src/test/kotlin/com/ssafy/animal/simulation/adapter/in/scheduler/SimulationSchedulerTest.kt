package com.ssafy.animal.simulation.adapter.`in`.scheduler

import com.ssafy.animal.simulation.application.port.`in`.SessionMaintenanceUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationSchedulerTest {

    @Test
    fun `세션 한 건이 실패해도 나머지 세션은 계속 진행한다`() {
        val advanced = mutableListOf<Long>()
        val maintenance = object : SessionMaintenanceUseCase {
            override fun inProgressSessionSeqs() = listOf(1L, 2L, 3L)
            override fun advance(sessionSeq: Long) {
                if (sessionSeq == 2L) error("일시 오류")
                advanced += sessionSeq
            }
            override fun abandonForWithdrawal(userSeq: Long) = error("not used")
        }

        SimulationScheduler(maintenance).advanceSessions()

        assertEquals(listOf(1L, 3L), advanced)
    }
}
