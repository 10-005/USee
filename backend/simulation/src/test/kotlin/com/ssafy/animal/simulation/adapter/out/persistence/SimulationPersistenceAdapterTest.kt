package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.simulation.domain.QEventOccurrence.Companion.eventOccurrence
import com.ssafy.animal.simulation.domain.QSimulationSession.Companion.simulationSession
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 사전 조회를 지난 경쟁 상태에서 DB 부분 UNIQUE 위반이 같은 409로 바뀌는지 */
class SimulationPersistenceAdapterTest {

    private fun violation(index: String) = DataIntegrityViolationException(
        "could not execute statement",
        RuntimeException("""ERROR: duplicate key value violates unique constraint "$index""""),
    )

    @Test
    fun `진행 중 세션 부분 UNIQUE 위반은 SIMULATION_ACTIVE_ALREADY_EXISTS로 바꾼다`() {
        assertEquals(
            SimulationErrorCode.SIMULATION_ACTIVE_ALREADY_EXISTS,
            SimulationPersistenceAdapter.translate(violation("uq_sim_session_user_active"))?.errorCode,
        )
        assertNull(SimulationPersistenceAdapter.translate(violation("ck_sim_session_stats")))
    }

    @Test
    fun `18_3 정렬은 startedDttm·endedDttm만 허용하고 기본은 startedDttm desc, 동률은 PK desc`() {
        val policy = SimulationPersistenceAdapter.SORT_POLICY

        assertEquals(
            listOf(simulationSession.startedDttm.desc(), simulationSession.sessionSeq.desc()),
            policy.toOrderSpecifiers(Sort.unsorted()).toList(),
        )
        assertEquals(
            listOf(simulationSession.endedDttm.asc(), simulationSession.sessionSeq.desc()),
            policy.toOrderSpecifiers(Sort.by(Sort.Order.asc("endedDttm"))).toList(),
        )
        val error = assertFailsWith<BusinessException> { policy.toOrderSpecifiers(Sort.by("regDttm")) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
        assertEquals("sort", error.errors.single().field)
    }

    @Test
    fun `18_19·18_20 정렬은 endedDttm만 허용하고 기본은 endedDttm desc, 동률은 세션 번호 desc`() {
        val policy = ReportPersistenceAdapter.LIST_SORT_POLICY

        assertEquals(
            listOf(simulationSession.endedDttm.desc(), simulationSession.sessionSeq.desc()),
            policy.toOrderSpecifiers(Sort.unsorted()).toList(),
        )
        assertEquals(
            listOf(simulationSession.endedDttm.asc(), simulationSession.sessionSeq.desc()),
            policy.toOrderSpecifiers(Sort.by(Sort.Order.asc("endedDttm"))).toList(),
        )
        val error = assertFailsWith<BusinessException> { policy.toOrderSpecifiers(Sort.by("startedDttm")) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
    }

    @Test
    fun `18_12 정렬은 occurredDttm만 허용하고 기본은 occurredDttm desc, 동률은 PK desc`() {
        val policy = EventPersistenceAdapter.SORT_POLICY

        assertEquals(
            listOf(eventOccurrence.occurredDttm.desc(), eventOccurrence.occurrenceSeq.desc()),
            policy.toOrderSpecifiers(Sort.unsorted()).toList(),
        )
        val error = assertFailsWith<BusinessException> { policy.toOrderSpecifiers(Sort.by("expiresDttm")) }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, error.errorCode)
    }
}
