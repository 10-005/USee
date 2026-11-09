package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.simulation.application.port.out.SessionClosingRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

/**
 * 세션 종료 시 산책 정리와 리포트 산책 수. 산책 엔티티(252)가 생기면 그쪽 어댑터로 옮긴다.
 * JPA와 같은 트랜잭션·커넥션에서 실행된다. 이벤트 쪽은 EventPersistenceAdapter로 옮겼다(255).
 */
@Repository
class SessionClosingJdbcAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SessionClosingRepository {

    override fun endActiveWalks(sessionSeq: Long, endedAt: OffsetDateTime): Int =
        jdbcTemplate.update(END_ACTIVE_WALKS, mapOf("sessionSeq" to sessionSeq, "endedAt" to endedAt))

    override fun countWalks(sessionSeq: Long): Int =
        jdbcTemplate.queryForObject(COUNT_WALKS, mapOf("sessionSeq" to sessionSeq), Int::class.javaObjectType) ?: 0

    companion object {
        private val END_ACTIVE_WALKS = """
            UPDATE simulation.simulation_walks SET status = 'ENDED', ended_dttm = :endedAt
            WHERE session_seq = :sessionSeq AND status = 'IN_PROGRESS'
        """.trimIndent()

        private val COUNT_WALKS = """
            SELECT COUNT(*) FROM simulation.simulation_walks WHERE session_seq = :sessionSeq
        """.trimIndent()
    }
}
