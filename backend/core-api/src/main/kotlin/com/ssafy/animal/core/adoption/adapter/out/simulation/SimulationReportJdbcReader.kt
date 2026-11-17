package com.ssafy.animal.core.adoption.adapter.out.simulation

import com.ssafy.animal.core.adoption.application.port.out.SimulationReportReader
import com.ssafy.animal.core.adoption.application.port.out.SimulationReportRow
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * simulation 스키마의 세션·리포트를 읽기 전용 JOIN으로 조회한다(12.15, Q420 D·Q483).
 * 소유하지 않는 테이블이라 엔티티로 매핑하지 않고(ddl validate 대상에서도 제외) 잠그지도 않는다.
 * 권한은 simulation Flyway가 core_app에 SELECT로 준다(V20261110_0900). 스키마를 붙여 쓴다.
 */
@Component
class SimulationReportJdbcReader(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SimulationReportReader {

    private val rowMapper = RowMapper { rs, _ ->
        SimulationReportRow(
            sessionSeq = rs.getLong("session_seq"),
            animalName = rs.getString("animal_name"),
            breedSeq = rs.getInt("breed_seq"),
            breedName = rs.getString("breed_name"),
            speciesCode = rs.getString("species_code"),
            representativeImageSeq = rs.getObject("representative_image_seq", Long::class.javaObjectType),
            startedDttm = rs.getObject("started_dttm", OffsetDateTime::class.java),
            endedDttm = rs.getObject("ended_dttm", OffsetDateTime::class.java),
            totalSpent = rs.getInt("total_spent"),
        )
    }

    override fun findEndedReports(userSeq: Int): List<SimulationReportRow> =
        jdbcTemplate.query(SELECT_ENDED_REPORTS, mapOf("userSeq" to userSeq), rowMapper)

    companion object {
        // 리포트가 있는 세션 = 끝난 세션(세션당 1건). 세션 번호로 동률을 정한다
        private val SELECT_ENDED_REPORTS = """
            SELECT s.session_seq, s.animal_name, s.started_dttm, s.ended_dttm, r.total_spent,
                   b.breed_seq, b.breed_name, b.representative_image_seq, sp.species_code
            FROM simulation.simulation_reports r
            JOIN simulation.simulation_sessions s ON s.session_seq = r.session_seq
            JOIN core.animal_breeds b ON b.breed_seq = s.breed_seq
            JOIN core.animal_species sp ON sp.species_seq = b.species_seq
            WHERE s.user_seq = :userSeq
            ORDER BY s.ended_dttm DESC, s.session_seq DESC
        """.trimIndent()
    }
}
