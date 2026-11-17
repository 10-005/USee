package com.ssafy.animal.core.found.adapter.out.persistence

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.FoundSearchFilter
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.application.port.out.FoundReportRepository
import com.ssafy.animal.core.found.application.port.out.ReporterRow
import com.ssafy.animal.core.found.application.port.out.RescueRow
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundReportImage
import com.ssafy.animal.core.found.domain.Gender
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneId

interface FoundReportJpaRepository : JpaRepository<FoundReport, Int> {
    fun findByFoundReportSeqAndIsDeletedFalse(foundReportSeq: Int): FoundReport?

    fun findByUserSeqAndIsDeletedFalseOrderByRegDttmDescFoundReportSeqDesc(userSeq: Int): List<FoundReport>

    @Query(
        nativeQuery = true,
        value = """
        select f.* from found_reports f join users u on u.user_seq = f.user_seq
        where f.found_report_seq = :seq and f.is_deleted = false and u.is_deleted = false
        """,
    )
    fun findVisible(@Param("seq") foundReportSeq: Int): FoundReport?

    @Query(
        nativeQuery = true,
        value = """
        select a.shelter_seq from shelter_district_assignments a join shelters s on s.shelter_seq = a.shelter_seq
        where a.district_code = :districtCode and s.is_deleted = false
        """,
    )
    fun findDistrictShelterSeq(@Param("districtCode") districtCode: String): Int?

    // 하버사인 거리(km). 보호소 수가 적어 전체 정렬로 충분하다
    @Query(
        nativeQuery = true,
        value = """
        select shelter_seq from shelters where is_deleted = false
        order by 2 * 6371 * asin(sqrt(
            power(sin(radians(latitude - :lat) / 2), 2)
            + cos(radians(:lat)) * cos(radians(latitude)) * power(sin(radians(longitude - :lng) / 2), 2)
        )), shelter_seq
        limit 1
        """,
    )
    fun findNearestShelterSeq(@Param("lat") latitude: Double, @Param("lng") longitude: Double): Int?

    @Query(nativeQuery = true, value = "select user_seq from shelter_members where shelter_seq = :shelterSeq")
    fun findShelterUserSeq(@Param("shelterSeq") shelterSeq: Int): Int?

    @Query(nativeQuery = true, value = "select shelter_name from shelters where shelter_seq = :shelterSeq")
    fun findShelterName(@Param("shelterSeq") shelterSeq: Int): String?

    @Query(
        nativeQuery = true,
        value = """
        select user_seq as userSeq, nickname, name, profile_image_seq as profileImageSeq, is_deleted as deleted
        from users where user_seq in (:userSeqs)
        """,
    )
    fun findReporters(@Param("userSeqs") userSeqs: Collection<Int>): List<ReporterProjection>

    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from bookmarks where user_seq = :userSeq and target_type = 'FOUND' and target_seq = :seq)
        """,
    )
    fun isBookmarked(@Param("userSeq") userSeq: Int, @Param("seq") foundReportSeq: Int): Boolean

    @Query(nativeQuery = true, value = "select count(*) from found_report_comments where found_report_seq = :seq and is_deleted = false")
    fun countComments(@Param("seq") foundReportSeq: Int): Long

    @Query(
        nativeQuery = true,
        value = """
        select distinct on (found_report_seq) found_report_seq as foundReportSeq, rescue_seq as rescueSeq, status
        from rescue_records where found_report_seq in (:seqs) and is_deleted = false
        order by found_report_seq, reg_dttm desc, rescue_seq desc
        """,
    )
    fun findLatestRescues(@Param("seqs") foundReportSeqs: Collection<Int>): List<RescueProjection>

    @Query(nativeQuery = true, value = "select image_seq as imageSeq, image_key as imageKey from images where image_seq in (:seqs)")
    fun findImageKeys(@Param("seqs") imageSeqs: Collection<Long>): List<ImageKeyProjection>
}

interface FoundReportImageJpaRepository : JpaRepository<FoundReportImage, Long> {
    fun findByFoundReportSeqInOrderByFoundReportSeqAscDisplayOrderAsc(foundReportSeqs: Collection<Int>): List<FoundReportImage>

    @Modifying
    @Query("delete from FoundReportImage i where i.foundReportSeq = :seq")
    fun deleteByFoundReportSeq(@Param("seq") foundReportSeq: Int)
}

interface ReporterProjection {
    val userSeq: Int
    val nickname: String?
    val name: String?
    val profileImageSeq: Long?

    // 프로젝션 getter는 JavaBeans 규칙으로 매핑되므로 is 접두어를 쓰지 않는다
    val deleted: Boolean
}

interface RescueProjection {
    val foundReportSeq: Int
    val rescueSeq: Int
    val status: String
}

interface ImageKeyProjection {
    val imageSeq: Long
    val imageKey: String
}

/**
 * 목록 검색은 회원·카탈로그·구조 테이블 조건이 섞여 엔티티 import 없이 쓰려고 네이티브 SQL로 만든다.
 * 결과는 FoundReport 엔티티로 받는다.
 */
@Repository
class FoundReportPersistenceAdapter(
    private val foundReportJpaRepository: FoundReportJpaRepository,
    private val foundReportImageJpaRepository: FoundReportImageJpaRepository,
    private val entityManager: EntityManager,
) : FoundReportRepository {

    override fun saveAndFlush(report: FoundReport): FoundReport = foundReportJpaRepository.saveAndFlush(report)

    override fun findActive(foundReportSeq: Int) = foundReportJpaRepository.findByFoundReportSeqAndIsDeletedFalse(foundReportSeq)

    override fun findVisible(foundReportSeq: Int) = foundReportJpaRepository.findVisible(foundReportSeq)

    override fun search(filter: FoundSearchFilter, pageable: Pageable): Page<FoundReport> {
        val where = NativeWhere("f.is_deleted = false and u.is_deleted = false")
        where.add("f.status = :status", "status", filter.status?.name)
        where.add("f.occurred_dttm >= :occurredFrom", "occurredFrom", filter.occurredFrom?.atStartOfDay(KST)?.toOffsetDateTime())
        where.add("f.occurred_dttm < :occurredTo", "occurredTo", filter.occurredTo?.plusDays(1)?.atStartOfDay(KST)?.toOffsetDateTime())
        where.add("f.district_code = :districtCode", "districtCode", filter.districtCode)
        where.add(SPECIES_CONDITION, "speciesSeq", filter.speciesSeq)
        // 포함 매칭(Q463): MALE·FEMALE은 UNKNOWN 포함, 중성화 true·false는 NULL(모름) 포함
        where.add("f.gender in (:genders)", "genders", filter.gender?.let { if (it == Gender.UNKNOWN) listOf(it.name) else listOf(it.name, Gender.UNKNOWN.name) })
        where.add("(f.is_neutered is null or f.is_neutered = :isNeutered)", "isNeutered", filter.isNeutered)
        where.add("f.description ilike :keyword", "keyword", filter.keyword?.let { "%${escapeLike(it)}%" })
        return page("from found_reports f join users u on u.user_seq = f.user_seq", where, pageable)
    }

    override fun findMine(userSeq: Int) = foundReportJpaRepository.findByUserSeqAndIsDeletedFalseOrderByRegDttmDescFoundReportSeqDesc(userSeq)

    override fun findAssigned(shelterSeq: Int, filter: AssignedFilter, pageable: Pageable): Page<FoundReport> {
        val where = NativeWhere("f.is_deleted = false")
        where.add("f.shelter_seq = :shelterSeq", "shelterSeq", shelterSeq)
        where.add("f.status = :status", "status", filter.status?.name)
        where.add("f.occurred_dttm >= :since", "since", filter.period?.let { OffsetDateTime.now().minusDays(it.days) })
        where.add("f.district_code = :districtCode", "districtCode", filter.districtCode)
        where.add(SPECIES_CONDITION, "speciesSeq", filter.speciesSeq)
        when (filter.rescueStatus) {
            null -> Unit
            RescueProgress.NONE -> where.add("lr.status is null")
            RescueProgress.PROCESSING -> where.add("lr.status = 'PROCESSING'")
            RescueProgress.DONE -> where.add("lr.status = 'DONE'")
            RescueProgress.ABORTED -> where.add("lr.status in ('FAILED', 'CANCELED')")
        }
        // 8.8 구조 상태는 신고별 최신 비삭제 구조 기록(ix_rescue_found_latest)
        val from = """
            from found_reports f
            left join lateral (
                select r.status from rescue_records r
                where r.found_report_seq = f.found_report_seq and r.is_deleted = false
                order by r.reg_dttm desc, r.rescue_seq desc limit 1
            ) lr on true
        """
        return page(from, where, pageable)
    }

    override fun findImages(foundReportSeqs: Collection<Int>): Map<Int, List<FoundReportImage>> =
        if (foundReportSeqs.isEmpty()) emptyMap()
        else foundReportImageJpaRepository.findByFoundReportSeqInOrderByFoundReportSeqAscDisplayOrderAsc(foundReportSeqs.toSet())
            .groupBy { it.foundReportSeq }

    override fun replaceImages(foundReportSeq: Int, imageSeqs: List<Long>) {
        foundReportImageJpaRepository.deleteByFoundReportSeq(foundReportSeq)
        foundReportImageJpaRepository.saveAllAndFlush(
            imageSeqs.mapIndexed { order, imageSeq -> FoundReportImage(foundReportSeq, imageSeq, order.toShort()) },
        )
    }

    override fun findImageKeys(imageSeqs: List<Long>): List<String> {
        if (imageSeqs.isEmpty()) return emptyList()
        val keys = foundReportJpaRepository.findImageKeys(imageSeqs).associate { it.imageSeq to it.imageKey }
        return imageSeqs.mapNotNull(keys::get)
    }

    override fun findDistrictShelterSeq(districtCode: String) = foundReportJpaRepository.findDistrictShelterSeq(districtCode)

    override fun findNearestShelterSeq(latitude: Double, longitude: Double) = foundReportJpaRepository.findNearestShelterSeq(latitude, longitude)

    override fun findShelterUserSeq(shelterSeq: Int) = foundReportJpaRepository.findShelterUserSeq(shelterSeq)

    override fun findShelterName(shelterSeq: Int) = foundReportJpaRepository.findShelterName(shelterSeq)

    override fun findReporters(userSeqs: Collection<Int>): Map<Int, ReporterRow> =
        if (userSeqs.isEmpty()) emptyMap()
        else foundReportJpaRepository.findReporters(userSeqs.toSet())
            .associate { it.userSeq to ReporterRow(it.userSeq, it.nickname, it.name, it.profileImageSeq, it.deleted) }

    override fun isBookmarked(userSeq: Int, foundReportSeq: Int) = foundReportJpaRepository.isBookmarked(userSeq, foundReportSeq)

    override fun countComments(foundReportSeq: Int) = foundReportJpaRepository.countComments(foundReportSeq)

    override fun findLatestRescues(foundReportSeqs: Collection<Int>): Map<Int, RescueRow> =
        if (foundReportSeqs.isEmpty()) emptyMap()
        else foundReportJpaRepository.findLatestRescues(foundReportSeqs.toSet()).associate { it.foundReportSeq to RescueRow(it.rescueSeq, it.status) }

    private fun page(from: String, where: NativeWhere, pageable: Pageable): Page<FoundReport> {
        val contentQuery = entityManager.createNativeQuery(
            "select f.* $from where ${where.sql} ${orderBy(pageable.sort)}",
            FoundReport::class.java,
        )
        val countQuery = entityManager.createNativeQuery("select count(*) $from where ${where.sql}")
        where.params.forEach { (name, value) ->
            contentQuery.setParameter(name, value)
            countQuery.setParameter(name, value)
        }
        @Suppress("UNCHECKED_CAST")
        val content = contentQuery.setFirstResult(pageable.offset.toInt()).setMaxResults(pageable.pageSize).resultList as List<FoundReport>
        return PageImpl(content, pageable, (countQuery.singleResult as Number).toLong())
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")

        // 종은 조합 → 품종에서 정해진다(7.1)
        const val SPECIES_CONDITION = """f.combination_seq in (
            select c.combination_seq from animal_combinations c join animal_breeds b on b.breed_seq = c.breed_seq
            where b.species_seq = :speciesSeq)"""

        /** 8.1·8.8 정렬 화이트리스트는 regDttm만(기본 desc), 동률은 PK desc */
        fun orderBy(sort: Sort): String {
            val orders = sort.map { order ->
                if (order.property != "regDttm") {
                    throw BusinessException(
                        CommonErrorCode.COMMON_INVALID_INPUT,
                        errors = listOf(ErrorDetail("sort", "정렬할 수 없는 필드입니다: ${order.property} (허용: regDttm)")),
                    )
                }
                "f.reg_dttm ${if (order.isAscending) "asc" else "desc"}"
            }.toList().ifEmpty { listOf("f.reg_dttm desc") }
            return "order by ${orders.joinToString()}, f.found_report_seq desc"
        }

        fun escapeLike(keyword: String) = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

/** 값이 있는 조건만 AND로 잇는다. 값은 항상 바인딩 파라미터로 넘긴다 */
internal class NativeWhere(base: String) {
    private val clauses = mutableListOf(base)
    val params = mutableMapOf<String, Any>()
    val sql: String get() = clauses.joinToString(" and ")

    fun add(clause: String) {
        clauses += clause
    }

    fun add(clause: String, name: String, value: Any?) {
        if (value == null) return
        clauses += clause
        params[name] = value
    }
}
