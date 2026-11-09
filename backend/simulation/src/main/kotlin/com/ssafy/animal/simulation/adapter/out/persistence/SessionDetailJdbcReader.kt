package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 세션 목록의 리포트 유무(18.3 hasReport)를 읽는다. 진행 중 산책은 WalkRepository(252)로 옮겼다.
 * 목록 한 페이지를 한 번에 보려고 테이블을 직접 조회한다.
 */
@Repository
class SessionDetailJdbcReader(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SessionDetailReader {

    override fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>): Set<Long> {
        if (sessionSeqs.isEmpty()) return emptySet()
        return jdbcTemplate.queryForList(REPORTED_SESSIONS, mapOf("sessionSeqs" to sessionSeqs.toSet()), Long::class.javaObjectType)
            .mapTo(mutableSetOf()) { it!! }
    }

    companion object {
        private val REPORTED_SESSIONS = """
            SELECT session_seq FROM simulation.simulation_reports WHERE session_seq IN (:sessionSeqs)
        """.trimIndent()
    }
}
