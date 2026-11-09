package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.simulation.application.port.out.EventCounts
import com.ssafy.animal.simulation.application.port.out.SessionClosingRepository
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

/**
 * 세션 종료 시 산책·이벤트 정리와 리포트 카운트. 산책(252)·이벤트(256) 엔티티가 생기면 그쪽 어댑터로 옮긴다.
 * JPA와 같은 트랜잭션·커넥션에서 실행된다.
 */
@Repository
class SessionClosingJdbcAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SessionClosingRepository {

    override fun endActiveWalks(sessionSeq: Long, endedAt: OffsetDateTime): Int =
        jdbcTemplate.update(END_ACTIVE_WALKS, mapOf("sessionSeq" to sessionSeq, "endedAt" to endedAt))

    override fun expirePendingEvents(sessionSeq: Long): Int =
        jdbcTemplate.update(EXPIRE_PENDING_EVENTS, mapOf("sessionSeq" to sessionSeq))

    override fun countEvents(sessionSeq: Long): EventCounts =
        jdbcTemplate.queryForObject(COUNT_EVENTS, mapOf("sessionSeq" to sessionSeq)) { rs, _ ->
            EventCounts(total = rs.getInt("total"), resolved = rs.getInt("resolved"))
        } ?: EventCounts(0, 0)

    override fun countWalks(sessionSeq: Long): Int =
        jdbcTemplate.queryForObject(COUNT_WALKS, mapOf("sessionSeq" to sessionSeq), Int::class.javaObjectType) ?: 0

    companion object {
        private val END_ACTIVE_WALKS = """
            UPDATE simulation.simulation_walks SET status = 'ENDED', ended_dttm = :endedAt
            WHERE session_seq = :sessionSeq AND status = 'IN_PROGRESS'
        """.trimIndent()

        private val EXPIRE_PENDING_EVENTS = """
            UPDATE simulation.simulation_event_occurrences SET status = 'EXPIRED'
            WHERE session_seq = :sessionSeq AND status = 'PENDING'
        """.trimIndent()

        private val COUNT_EVENTS = """
            SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE status = 'RESOLVED') AS resolved
            FROM simulation.simulation_event_occurrences WHERE session_seq = :sessionSeq
        """.trimIndent()

        private val COUNT_WALKS = """
            SELECT COUNT(*) FROM simulation.simulation_walks WHERE session_seq = :sessionSeq
        """.trimIndent()
    }
}
