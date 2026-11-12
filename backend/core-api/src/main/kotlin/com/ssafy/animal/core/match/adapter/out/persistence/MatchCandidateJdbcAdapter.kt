package com.ssafy.animal.core.match.adapter.out.persistence

import com.ssafy.animal.common.event.MatchCandidate
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.core.match.application.port.out.MatchCandidateRepository
import com.ssafy.animal.core.match.application.port.out.MatchCriteria
import com.ssafy.animal.core.match.application.port.out.MatchSource
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

/**
 * 실종·발견·보호동물을 같은 모양(위치·시각·작성자·자격)으로 펴서 후보를 고른다(Q523).
 * 세 도메인 테이블을 가로지르는 읽기라 엔티티 없이 SQL로 읽는다. 자격은 AiMatchPersistenceAdapter와 같다.
 */
@Component
class MatchCandidateJdbcAdapter(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val entityManager: EntityManager,
) : MatchCandidateRepository {

    override fun findCandidates(sourceType: TargetType, sourceSeq: Long, criteria: MatchCriteria): List<MatchCandidate> {
        // JDBC는 Hibernate를 거치지 않으므로 같은 트랜잭션에서 바꾼 조합·사진을 먼저 내보낸다(11.5는 flush 전에 발행한다)
        if (entityManager.isJoinedToTransaction) entityManager.flush()
        val params = mapOf(
            "type" to sourceType.name,
            "seq" to sourceSeq,
            "maxKm" to criteria.maxDistanceKm,
            "graceSeconds" to criteria.dateGrace.seconds.toDouble(),
            "limit" to criteria.limit,
        )
        return jdbcTemplate.query(SELECT_CANDIDATES, params) { rs, _ ->
            MatchCandidate(TargetType.valueOf(rs.getString("type")), rs.getLong("seq"))
        }
    }

    override fun findEligibleSources(type: TargetType): List<MatchSource> {
        val params = mapOf("type" to type.name)
        val keys = jdbcTemplate.query(SELECT_IMAGE_KEYS, params) { rs, _ -> rs.getLong("seq") to rs.getString("image_key") }
            .groupBy({ it.first }, { it.second })
        return jdbcTemplate.query(SELECT_ELIGIBLE, params) { rs, _ ->
            val seq = rs.getLong("seq")
            MatchSource(seq, rs.getString("species_code"), rs.getObject("at", OffsetDateTime::class.java), keys[seq].orEmpty())
        }
    }

    companion object {
        /**
         * 대상 하나 = (type, seq, 종, 위치, 시각, 작성자, 자격).
         * 보호동물: 구조 기록(비삭제)이 가리키는 발견 신고가 있으면 그 좌표, 없으면 보호소 좌표.
         * 시각은 구조 기록의 rescued_dttm, 구조 기록이 없으면 보호동물 등록 시각. 작성자 조건은 보호동물에 걸지 않는다(NULL)
         */
        private val TARGETS = """
            points AS (
                SELECT 'MISSING' AS type, m.missing_report_seq::bigint AS seq, m.combination_seq,
                       m.latitude AS lat, m.longitude AS lng, m.occurred_dttm AS at, m.user_seq,
                       (m.status = 'ACTIVE' AND NOT m.is_deleted AND NOT u.is_deleted) AS eligible
                FROM missing_reports m JOIN users u ON u.user_seq = m.user_seq
                UNION ALL
                SELECT 'FOUND', f.found_report_seq, f.combination_seq, f.latitude, f.longitude, f.occurred_dttm, f.user_seq,
                       (f.status = 'ACTIVE' AND NOT f.is_deleted AND NOT u.is_deleted)
                FROM found_reports f JOIN users u ON u.user_seq = f.user_seq
                UNION ALL
                SELECT 'SHELTER_ANIMAL', a.animal_seq, a.combination_seq,
                       coalesce(rf.latitude, s.latitude), coalesce(rf.longitude, s.longitude),
                       coalesce(r.rescued_dttm, a.reg_dttm), NULL,
                       (a.protection_status = 'PROTECTING' AND NOT a.is_deleted)
                FROM shelter_animals a
                JOIN shelters s ON s.shelter_seq = a.shelter_seq
                LEFT JOIN rescue_records r ON r.rescue_seq = a.rescue_seq AND NOT r.is_deleted
                LEFT JOIN found_reports rf ON rf.found_report_seq = r.found_report_seq
            ),
            targets AS (
                SELECT p.*, b.species_seq, sp.species_code
                FROM points p
                JOIN animal_combinations c ON c.combination_seq = p.combination_seq
                JOIN animal_breeds b ON b.breed_seq = c.breed_seq
                JOIN animal_species sp ON sp.species_seq = b.species_seq
            )
        """.trimIndent()

        // ponytail: 매 요청 전체 대상을 펴서 거리 계산(인덱스 없음). 대상이 수만 건이 되면 위경도 범위로 먼저 거른다
        private val SELECT_CANDIDATES = """
            WITH $TARGETS
            SELECT type, seq FROM (
                SELECT c.type, c.seq,
                       2 * 6371 * asin(least(1, sqrt(
                           power(sin(radians(c.lat - s.lat) / 2), 2)
                           + cos(radians(s.lat)) * cos(radians(c.lat)) * power(sin(radians(c.lng - s.lng) / 2), 2)
                       ))) AS km
                FROM targets s JOIN targets c ON c.species_seq = s.species_seq
                WHERE s.type = :type AND s.seq = :seq
                  AND s.species_code <> 'ETC'
                  AND c.eligible
                  AND CASE WHEN s.type = 'MISSING' THEN c.type <> 'MISSING' ELSE c.type = 'MISSING' END
                  -- 발견·구조 시각 >= 실종 시각 - 여유
                  AND CASE WHEN s.type = 'MISSING' THEN c.at >= s.at - make_interval(secs => :graceSeconds)
                           ELSE s.at >= c.at - make_interval(secs => :graceSeconds) END
                  AND (s.user_seq IS NULL OR c.user_seq IS NULL OR s.user_seq <> c.user_seq)
            ) x
            WHERE km <= :maxKm
            ORDER BY km, type, seq
            LIMIT :limit
        """.trimIndent()

        private val SELECT_ELIGIBLE = """
            WITH $TARGETS
            SELECT seq, species_code, at FROM targets WHERE type = :type AND eligible ORDER BY seq
        """.trimIndent()

        private val SELECT_IMAGE_KEYS = """
            SELECT x.seq, i.image_key FROM (
                SELECT 'MISSING' AS type, missing_report_seq::bigint AS seq, image_seq, display_order FROM missing_report_images
                UNION ALL SELECT 'FOUND', found_report_seq, image_seq, display_order FROM found_report_images
                UNION ALL SELECT 'SHELTER_ANIMAL', animal_seq, image_seq, display_order FROM shelter_animal_images
            ) x JOIN images i ON i.image_seq = x.image_seq
            WHERE x.type = :type
            ORDER BY x.seq, x.display_order
        """.trimIndent()
    }
}
