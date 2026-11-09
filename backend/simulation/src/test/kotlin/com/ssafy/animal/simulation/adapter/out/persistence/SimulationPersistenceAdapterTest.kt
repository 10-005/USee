package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
