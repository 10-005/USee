package com.ssafy.animal.simulation.adapter.`in`.scheduler

import com.ssafy.animal.simulation.application.port.`in`.ReportSummaryUseCase
import com.ssafy.animal.simulation.application.port.`in`.SessionMaintenanceUseCase
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationSchedulerTest {

    private val noMaintenance = object : SessionMaintenanceUseCase {
        override fun inProgressSessionSeqs() = emptyList<Long>()
        override fun advance(sessionSeq: Long) = error("not used")
        override fun abandonForWithdrawal(userSeq: Long) = error("not used")
    }

    private val noSummary = object : ReportSummaryUseCase {
        override fun summarize(sessionSeq: Long) = error("not used")
        override fun summaryMissingSessionSeqs() = emptyList<Long>()
    }

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

        SimulationScheduler(maintenance, noSummary).advanceSessions()

        assertEquals(listOf(1L, 3L), advanced)
    }

    @Test
    fun `요약 없는 리포트를 한 건씩 다시 만들고 한 건이 실패해도 계속한다`() {
        val summarized = mutableListOf<Long>()
        val summary = object : ReportSummaryUseCase {
            override fun summarize(sessionSeq: Long): Boolean {
                if (sessionSeq == 11L) error("일시 오류")
                summarized += sessionSeq
                return true
            }
            override fun summaryMissingSessionSeqs() = listOf(10L, 11L, 12L)
        }

        SimulationScheduler(noMaintenance, summary).recoverSummaries()

        assertEquals(listOf(10L, 12L), summarized)
    }
}
