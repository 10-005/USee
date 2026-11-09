package com.ssafy.animal.core.bookmark.adapter.out.persistence

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.bookmark.application.port.out.BookmarkRepository
import com.ssafy.animal.core.bookmark.application.port.out.BookmarkRow
import com.ssafy.animal.core.bookmark.domain.Bookmark
import com.ssafy.animal.core.bookmark.domain.BookmarkErrorCode
import com.ssafy.animal.core.bookmark.domain.BookmarkTargetType
import jakarta.persistence.EntityManager
import org.hibernate.query.NativeQuery
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

interface BookmarkJpaRepository : JpaRepository<Bookmark, Long> {
    fun findByUserSeqAndTargetTypeAndTargetSeq(userSeq: Int, targetType: BookmarkTargetType, targetSeq: Long): Bookmark?

    // 대상 테이블은 각 도메인 몫이라 네이티브로 존재만 확인한다
    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from missing_reports r join users u on u.user_seq = r.user_seq
                      where r.missing_report_seq = :seq and r.is_deleted = false and u.is_deleted = false)
        """,
    )
    fun existsMissing(@Param("seq") seq: Long): Boolean

    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from found_reports r join users u on u.user_seq = r.user_seq
                      where r.found_report_seq = :seq and r.is_deleted = false and u.is_deleted = false)
        """,
    )
    fun existsFound(@Param("seq") seq: Long): Boolean

    @Query(nativeQuery = true, value = "select exists(select 1 from shelter_animals where animal_seq = :seq and is_deleted = false)")
    fun existsShelterAnimal(@Param("seq") seq: Long): Boolean
}

@Repository
class BookmarkPersistenceAdapter(
    private val bookmarkJpaRepository: BookmarkJpaRepository,
    private val entityManager: EntityManager,
) : BookmarkRepository {

    override fun find(userSeq: Int, targetType: BookmarkTargetType, targetSeq: Long) =
        bookmarkJpaRepository.findByUserSeqAndTargetTypeAndTargetSeq(userSeq, targetType, targetSeq)

    override fun saveAndFlush(bookmark: Bookmark): Bookmark = try {
        bookmarkJpaRepository.saveAndFlush(bookmark)
    } catch (e: DataIntegrityViolationException) {
        if ("uq_bookmark_user_target" in e.mostSpecificCause.message.orEmpty()) {
            throw BusinessException(BookmarkErrorCode.USER_BOOKMARK_ALREADY_EXISTS)
        }
        throw e
    }

    override fun delete(bookmark: Bookmark) = bookmarkJpaRepository.delete(bookmark)

    override fun existsVisibleTarget(targetType: BookmarkTargetType, targetSeq: Long) = when (targetType) {
        BookmarkTargetType.MISSING -> bookmarkJpaRepository.existsMissing(targetSeq)
        BookmarkTargetType.FOUND -> bookmarkJpaRepository.existsFound(targetSeq)
        BookmarkTargetType.SHELTER_ANIMAL -> bookmarkJpaRepository.existsShelterAnimal(targetSeq)
    }

    override fun findVisible(userSeq: Int, targetType: BookmarkTargetType?, pageable: Pageable): Page<BookmarkRow> {
        val where = "where b.user_seq = :userSeq" + (if (targetType != null) " and b.target_type = :targetType" else "") +
            " and coalesce(mr.missing_report_seq, fr.found_report_seq, sa.animal_seq) is not null"
        val total = (entityManager.createNativeQuery("select count(*) $FROM $where").bind(userSeq, targetType).singleResult as Number).toLong()
        if (total == 0L) return PageImpl(emptyList(), pageable, 0)

        // 방향은 enum에서만 골라 SQL에 넣으므로 주입 위험이 없다
        val sql = "$SELECT $FROM $where order by b.reg_dttm ${direction(pageable)}, b.bookmark_seq desc limit :limit offset :offset"
        val query = entityManager.createNativeQuery(sql).bind(userSeq, targetType)
            .setParameter("limit", pageable.pageSize)
            .setParameter("offset", pageable.offset)
        @Suppress("UNCHECKED_CAST")
        val rows = (query.unwrap(NativeQuery::class.java) as NativeQuery<Array<Any?>>)
            .addScalar("bookmark_seq", Long::class.javaObjectType)
            .addScalar("target_type", String::class.java)
            .addScalar("target_seq", Long::class.javaObjectType)
            .addScalar("reg_dttm", OffsetDateTime::class.java)
            .addScalar("combination_seq", Int::class.javaObjectType)
            .addScalar("gender", String::class.java)
            .addScalar("age", String::class.java)
            .addScalar("status", String::class.java)
            .addScalar("address", String::class.java)
            .addScalar("occurred_dttm", OffsetDateTime::class.java)
            .addScalar("image_seq", Long::class.javaObjectType)
            .resultList
            .map {
                BookmarkRow(
                    bookmarkSeq = it[0] as Long,
                    targetType = BookmarkTargetType.valueOf(it[1] as String),
                    targetSeq = it[2] as Long,
                    regDttm = it[3] as OffsetDateTime,
                    combinationSeq = it[4] as Int,
                    gender = it[5] as String,
                    age = it[6] as String?,
                    status = it[7] as String,
                    address = it[8] as String,
                    occurredDttm = it[9] as OffsetDateTime?,
                    representativeImageSeq = it[10] as Long?,
                )
            }
        return PageImpl(rows, pageable, total)
    }

    private fun jakarta.persistence.Query.bind(userSeq: Int, targetType: BookmarkTargetType?) = apply {
        setParameter("userSeq", userSeq)
        if (targetType != null) setParameter("targetType", targetType.name)
    }

    companion object {
        /** 16.1 정렬 화이트리스트는 regDttm 하나. 기본 desc */
        fun direction(pageable: Pageable): String {
            val orders = pageable.sort.toList()
            val invalid = orders.firstOrNull { it.property != "regDttm" }
            if (invalid != null || orders.size > 1) {
                throw BusinessException(
                    CommonErrorCode.COMMON_INVALID_INPUT,
                    errors = listOf(ErrorDetail("sort", "정렬할 수 없는 필드입니다: ${invalid?.property ?: orders} (허용: regDttm)")),
                )
            }
            return if (orders.firstOrNull()?.isAscending == true) "asc" else "desc"
        }

        // 신고는 비삭제·작성자 비탈퇴(공통 필터), 보호동물은 비삭제만 노출. 신고가 CLOSED거나 보호동물이 ADOPTED여도 남긴다
        private const val FROM = """
            from bookmarks b
            left join missing_reports mr on b.target_type = 'MISSING' and mr.missing_report_seq = b.target_seq
                and mr.is_deleted = false and exists(select 1 from users u where u.user_seq = mr.user_seq and u.is_deleted = false)
            left join found_reports fr on b.target_type = 'FOUND' and fr.found_report_seq = b.target_seq
                and fr.is_deleted = false and exists(select 1 from users u where u.user_seq = fr.user_seq and u.is_deleted = false)
            left join shelter_animals sa on b.target_type = 'SHELTER_ANIMAL' and sa.animal_seq = b.target_seq and sa.is_deleted = false
            left join shelters sh on sh.shelter_seq = sa.shelter_seq
        """

        private const val SELECT = """
            select b.bookmark_seq, b.target_type, b.target_seq, b.reg_dttm,
                coalesce(mr.combination_seq, fr.combination_seq, sa.combination_seq) as combination_seq,
                coalesce(mr.gender, fr.gender, sa.gender) as gender,
                coalesce(mr.age, fr.age, sa.age) as age,
                coalesce(mr.status, fr.status, sa.protection_status) as status,
                coalesce(mr.address, fr.address, sh.address) as address,
                coalesce(mr.occurred_dttm, fr.occurred_dttm) as occurred_dttm,
                case b.target_type
                    when 'MISSING' then (select i.image_seq from missing_report_images i
                        where i.missing_report_seq = mr.missing_report_seq order by i.display_order limit 1)
                    when 'FOUND' then (select i.image_seq from found_report_images i
                        where i.found_report_seq = fr.found_report_seq order by i.display_order limit 1)
                    else (select i.image_seq from shelter_animal_images i
                        where i.animal_seq = sa.animal_seq order by i.display_order limit 1)
                end as image_seq
        """
    }
}
