package com.ssafy.animal.simulation.adapter.out.persistence

import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 세션 상세 파생 정보(기구·진행 중 산책·리포트 유무)를 읽는다.
 * 인벤토리·펫샵·산책·리포트 엔티티는 각 기능 작업에서 만들므로 그 전까지 테이블을 직접 조회한다.
 */
@Repository
class SessionDetailJdbcReader(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SessionDetailReader {

    override fun findEquipment(sessionSeq: Long): Equipment {
        val owned = jdbcTemplate.queryForList(OWNED_EQUIPMENT, mapOf("sessionSeq" to sessionSeq), String::class.java)
            .mapTo(mutableSetOf()) { ItemCategory.valueOf(it!!) }
        return Equipment.of(owned)
    }

    override fun findActiveWalkSeq(sessionSeq: Long): Long? =
        jdbcTemplate.queryForList(ACTIVE_WALK, mapOf("sessionSeq" to sessionSeq), Long::class.javaObjectType).firstOrNull()

    override fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>): Set<Long> {
        if (sessionSeqs.isEmpty()) return emptySet()
        return jdbcTemplate.queryForList(REPORTED_SESSIONS, mapOf("sessionSeqs" to sessionSeqs.toSet()), Long::class.javaObjectType)
            .mapTo(mutableSetOf()) { it!! }
    }

    companion object {
        private val OWNED_EQUIPMENT = """
            SELECT DISTINCT s.category
            FROM simulation.simulation_inventory_items i
            JOIN simulation.simulation_shop_items s ON s.item_seq = i.item_seq
            WHERE i.session_seq = :sessionSeq AND i.quantity > 0 AND s.is_consumable = FALSE
        """.trimIndent()

        private val ACTIVE_WALK = """
            SELECT walk_seq FROM simulation.simulation_walks
            WHERE session_seq = :sessionSeq AND status = 'IN_PROGRESS'
        """.trimIndent()

        private val REPORTED_SESSIONS = """
            SELECT session_seq FROM simulation.simulation_reports WHERE session_seq IN (:sessionSeqs)
        """.trimIndent()
    }
}
