package com.ssafy.animal.core.rescue.adapter.out.persistence

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.application.port.`in`.RescueSource
import com.ssafy.animal.core.rescue.application.port.out.AnimalRow
import com.ssafy.animal.core.rescue.application.port.out.FoundReportRow
import com.ssafy.animal.core.rescue.application.port.out.RescueRepository
import com.ssafy.animal.core.rescue.domain.RescueErrorCode
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface RescueRecordJpaRepository : JpaRepository<RescueRecord, Int> {
    fun findByRescueSeqAndIsDeletedFalse(rescueSeq: Int): RescueRecord?

    fun existsByFoundReportSeqAndIsDeletedFalseAndStatusIn(foundReportSeq: Int, statuses: Collection<RescueStatus>): Boolean

    @Query(nativeQuery = true, value = "select exists(select 1 from found_reports where found_report_seq = :seq and is_deleted = false)")
    fun existsActiveFoundReport(@Param("seq") foundReportSeq: Int): Boolean

    @Query(nativeQuery = true, value = "select count(*) from shelter_animals where rescue_seq = :seq and is_deleted = false")
    fun countLinkedAnimals(@Param("seq") rescueSeq: Int): Long

    @Query(
        nativeQuery = true,
        value = """
        select f.found_report_seq as seq, f.combination_seq as combinationSeq, f.gender, f.age, f.is_neutered as neutered, f.weight,
            (select i.image_seq from found_report_images i where i.found_report_seq = f.found_report_seq order by i.display_order limit 1) as imageSeq
        from found_reports f where f.found_report_seq in (:seqs)
        """,
    )
    fun findFoundReportAnimals(@Param("seqs") foundReportSeqs: Collection<Int>): List<AnimalProjection>

    @Query(
        nativeQuery = true,
        value = """
        select distinct on (a.rescue_seq) a.rescue_seq as seq, a.combination_seq as combinationSeq, a.gender, a.age,
            a.is_neutered as neutered, a.weight,
            (select i.image_seq from shelter_animal_images i where i.animal_seq = a.animal_seq order by i.display_order limit 1) as imageSeq
        from shelter_animals a where a.rescue_seq in (:seqs) and a.is_deleted = false
        order by a.rescue_seq, a.reg_dttm, a.animal_seq
        """,
    )
    fun findFirstShelterAnimals(@Param("seqs") rescueSeqs: Collection<Int>): List<AnimalProjection>

    @Query(
        nativeQuery = true,
        value = """
        select f.found_report_seq as seq, f.combination_seq as combinationSeq, f.gender, f.age, f.is_neutered as neutered, f.weight,
            (select i.image_seq from found_report_images i where i.found_report_seq = f.found_report_seq order by i.display_order limit 1) as imageSeq,
            f.address, f.occurred_dttm as occurredDttm, u.name as reporterName, u.is_deleted as reporterDeleted,
            f.reporter_phone as reporterPhone, f.status
        from found_reports f join users u on u.user_seq = f.user_seq
        where f.found_report_seq = :seq
        """,
    )
    fun findFoundReportSummary(@Param("seq") foundReportSeq: Int): FoundReportProjection?
}

interface AnimalProjection {
    val seq: Int
    val combinationSeq: Int
    val gender: String
    val age: String?

    // 프로젝션 getter는 JavaBeans 규칙으로 매핑되므로 is 접두어를 쓰지 않는다
    val neutered: Boolean?
    val weight: String?
    val imageSeq: Long?
}

interface FoundReportProjection : AnimalProjection {
    val address: String
    val occurredDttm: OffsetDateTime
    val reporterName: String?
    val reporterDeleted: Boolean
    val reporterPhone: String
    val status: String
}

/** 목록은 발견 신고·보호동물·카탈로그 조건이 섞여 엔티티 import 없이 쓰려고 네이티브 SQL로 만든다 */
@Repository
class RescuePersistenceAdapter(
    private val rescueRecordJpaRepository: RescueRecordJpaRepository,
    private val entityManager: EntityManager,
) : RescueRepository {

    override fun saveAndFlush(rescue: RescueRecord): RescueRecord = try {
        rescueRecordJpaRepository.saveAndFlush(rescue)
    } catch (e: DataIntegrityViolationException) {
        // 사전 조회를 지나 동시에 접수된 경우(Q294 부분 UNIQUE)
        if ("uq_rescue_found_active_done" in e.mostSpecificCause.message.orEmpty()) {
            throw BusinessException(RescueErrorCode.RESCUE_ACTIVE_ALREADY_EXISTS)
        }
        throw e
    }

    override fun findActive(rescueSeq: Int) = rescueRecordJpaRepository.findByRescueSeqAndIsDeletedFalse(rescueSeq)

    override fun findPage(shelterSeq: Int, filter: RescueFilter, pageable: Pageable): Page<RescueRecord> {
        val (where, params) = where(shelterSeq, filter, withStatus = true)
        val content = entityManager.createNativeQuery("select r.* $FROM where $where ${orderBy(pageable.sort)}", RescueRecord::class.java)
            .bind(params).setFirstResult(pageable.offset.toInt()).setMaxResults(pageable.pageSize).resultList
        val total = entityManager.createNativeQuery("select count(*) $FROM where $where").bind(params).singleResult as Number
        @Suppress("UNCHECKED_CAST")
        return PageImpl(content as List<RescueRecord>, pageable, total.toLong())
    }

    override fun countByStatus(shelterSeq: Int, filter: RescueFilter): Map<RescueStatus, Long> {
        val (where, params) = where(shelterSeq, filter, withStatus = false)
        @Suppress("UNCHECKED_CAST")
        val rows = entityManager.createNativeQuery("select r.status, count(*) $FROM where $where group by r.status")
            .bind(params).resultList as List<Array<Any>>
        return rows.associate { RescueStatus.valueOf(it[0] as String) to (it[1] as Number).toLong() }
    }

    override fun existsActiveFoundReport(foundReportSeq: Int) = rescueRecordJpaRepository.existsActiveFoundReport(foundReportSeq)

    override fun existsOngoingRescue(foundReportSeq: Int) =
        rescueRecordJpaRepository.existsByFoundReportSeqAndIsDeletedFalseAndStatusIn(foundReportSeq, ONGOING)

    override fun countLinkedAnimals(rescueSeq: Int) = rescueRecordJpaRepository.countLinkedAnimals(rescueSeq)

    override fun findFoundReportAnimals(foundReportSeqs: Collection<Int>): Map<Int, AnimalRow> =
        if (foundReportSeqs.isEmpty()) emptyMap()
        else rescueRecordJpaRepository.findFoundReportAnimals(foundReportSeqs.toSet()).associate { it.seq to it.toRow() }

    override fun findFirstShelterAnimals(rescueSeqs: Collection<Int>): Map<Int, AnimalRow> =
        if (rescueSeqs.isEmpty()) emptyMap()
        else rescueRecordJpaRepository.findFirstShelterAnimals(rescueSeqs.toSet()).associate { it.seq to it.toRow() }

    override fun findFoundReportSummary(foundReportSeq: Int) = rescueRecordJpaRepository.findFoundReportSummary(foundReportSeq)?.let {
        FoundReportRow(it.seq, it.toRow(), it.address, it.occurredDttm, it.reporterName, it.reporterDeleted, it.reporterPhone, it.status)
    }

    private fun AnimalProjection.toRow() = AnimalRow(combinationSeq, gender, age, neutered, weight, imageSeq)

    private fun jakarta.persistence.Query.bind(params: Map<String, Any>) = apply { params.forEach { (k, v) -> setParameter(k, v) } }

    private fun where(shelterSeq: Int, filter: RescueFilter, withStatus: Boolean): Pair<String, Map<String, Any>> {
        val clauses = mutableListOf("r.is_deleted = false", "r.shelter_seq = :shelterSeq")
        val params = mutableMapOf<String, Any>("shelterSeq" to shelterSeq)
        fun add(clause: String, name: String, value: Any?) {
            if (value == null) return
            clauses += clause
            params[name] = value
        }
        if (withStatus) add("r.status = :status", "status", filter.status?.name)
        add("r.rescued_dttm >= :since", "since", filter.period?.let { OffsetDateTime.now().minusDays(it.days) })
        add("r.found_report_seq = :foundReportSeq", "foundReportSeq", filter.foundReportSeq)
        when (filter.source) {
            RescueSource.REPORT -> clauses += "r.found_report_seq is not null"
            RescueSource.DIRECT -> clauses += "r.found_report_seq is null"
            null -> Unit
        }
        // 축종은 목록의 동물 정보 출처와 같은 기준(발견 신고 또는 첫 보호동물)으로 본다
        add(
            """coalesce(f.combination_seq, fa.combination_seq) in (
                select c.combination_seq from animal_combinations c join animal_breeds b on b.breed_seq = c.breed_seq
                where b.species_seq = :speciesSeq)""",
            "speciesSeq",
            filter.speciesSeq,
        )
        return clauses.joinToString(" and ") to params
    }

    private companion object {
        val ONGOING = listOf(RescueStatus.PROCESSING, RescueStatus.DONE)

        const val FROM = """
            from rescue_records r
            left join found_reports f on f.found_report_seq = r.found_report_seq
            left join lateral (
                select a.combination_seq from shelter_animals a
                where a.rescue_seq = r.rescue_seq and a.is_deleted = false
                order by a.reg_dttm, a.animal_seq limit 1
            ) fa on true
        """

        // 10.1 화이트리스트 regDttm·completedDttm(기본 regDttm desc), 동률은 PK desc. 진행 중(완료 시각 NULL)은 뒤로
        val SORT_COLUMNS = mapOf("regDttm" to "r.reg_dttm", "completedDttm" to "r.completed_dttm")

        fun orderBy(sort: Sort): String {
            val orders = sort.map { order ->
                val column = SORT_COLUMNS[order.property] ?: throw BusinessException(
                    CommonErrorCode.COMMON_INVALID_INPUT,
                    errors = listOf(ErrorDetail("sort", "정렬할 수 없는 필드입니다: ${order.property} (허용: ${SORT_COLUMNS.keys.joinToString()})")),
                )
                "$column ${if (order.isAscending) "asc" else "desc"} nulls last"
            }.toList().ifEmpty { listOf("r.reg_dttm desc") }
            return "order by ${orders.joinToString()}, r.rescue_seq desc"
        }
    }
}
