package com.ssafy.animal.core.missing.adapter.out.persistence

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.application.port.out.AuthorRow
import com.ssafy.animal.core.missing.application.port.out.MissingReportRepository
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingReport
import com.ssafy.animal.core.missing.domain.MissingReportImage
import com.ssafy.animal.core.missing.domain.QMissingReport.Companion.missingReport
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.ZoneId

// 작성자 탈퇴 비노출(1.3 공통 필터). User 엔티티는 user 패키지 것이라 import하지 않고 엔티티 이름으로만 참조한다
private const val AUTHOR_ACTIVE = "exists (select 1 from User u where u.userSeq = m.userSeq and u.isDeleted = false)"

interface MissingReportJpaRepository : JpaRepository<MissingReport, Int> {
    @Query("select m from MissingReport m where m.missingReportSeq = :seq and m.isDeleted = false and $AUTHOR_ACTIVE")
    fun findVisible(@Param("seq") missingReportSeq: Int): MissingReport?

    fun findByMissingReportSeqAndUserSeqAndIsDeletedFalse(missingReportSeq: Int, userSeq: Int): MissingReport?

    fun findByUserSeqAndIsDeletedFalseOrderByRegDttmDescMissingReportSeqDesc(userSeq: Int): List<MissingReport>

    @Query(
        nativeQuery = true,
        value = "select image_seq as imageSeq, image_key as imageKey from images where image_seq in (:imageSeqs)",
    )
    fun findImageKeys(@Param("imageSeqs") imageSeqs: Collection<Long>): List<ImageKeyRow>

    // 아래는 다른 도메인 테이블이라 네이티브로 읽기만 한다(선례: UserJpaRepository.findShelterDisplay)
    @Query(
        nativeQuery = true,
        value = """
        select user_seq as userSeq, nickname, role, profile_image_seq as profileImageSeq
        from users where user_seq = :userSeq
        """,
    )
    fun findAuthor(@Param("userSeq") userSeq: Int): AuthorRow?

    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from bookmarks where user_seq = :userSeq and target_type = 'MISSING' and target_seq = :targetSeq)
        """,
    )
    fun isBookmarked(@Param("userSeq") userSeq: Int, @Param("targetSeq") missingReportSeq: Long): Boolean

    @Query(
        nativeQuery = true,
        value = "select count(*) from missing_report_comments where missing_report_seq = :seq and is_deleted = false",
    )
    fun countComments(@Param("seq") missingReportSeq: Int): Long
}

interface ImageKeyRow {
    val imageSeq: Long
    val imageKey: String
}

interface MissingReportImageJpaRepository : JpaRepository<MissingReportImage, Long> {
    @Query("select i.imageSeq from MissingReportImage i where i.missingReportSeq = :seq order by i.displayOrder")
    fun findImageSeqs(@Param("seq") missingReportSeq: Int): List<Long>

    @Query("select i from MissingReportImage i where i.missingReportSeq in :seqs and i.displayOrder = 1")
    fun findRepresentatives(@Param("seqs") missingReportSeqs: Collection<Int>): List<MissingReportImage>

    // 벌크 삭제라 바로 DB에 나간다. 순서를 바꿔 다시 넣어도 UNIQUE(missing_report_seq, display_order)에 걸리지 않는다
    @Modifying
    @Query("delete from MissingReportImage i where i.missingReportSeq = :seq")
    fun deleteByMissingReportSeq(@Param("seq") missingReportSeq: Int)
}

@Repository
class MissingReportPersistenceAdapter(
    private val reportJpaRepository: MissingReportJpaRepository,
    private val imageJpaRepository: MissingReportImageJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : MissingReportRepository {

    override fun saveAndFlush(report: MissingReport): MissingReport = reportJpaRepository.saveAndFlush(report)

    override fun findVisible(missingReportSeq: Int) = reportJpaRepository.findVisible(missingReportSeq)

    override fun findOwned(userSeq: Int, missingReportSeq: Int) =
        reportJpaRepository.findByMissingReportSeqAndUserSeqAndIsDeletedFalse(missingReportSeq, userSeq)

    override fun search(filter: MissingSearchFilter, pageable: Pageable): Page<MissingReport> {
        val condition = conditions(filter).reduce(BooleanExpression::and)
        val content = queryFactory.selectFrom(missingReport)
            .where(condition)
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(missingReport.count()).from(missingReport).where(condition).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun findMine(userSeq: Int) = reportJpaRepository.findByUserSeqAndIsDeletedFalseOrderByRegDttmDescMissingReportSeqDesc(userSeq)

    override fun findImageSeqs(missingReportSeq: Int) = imageJpaRepository.findImageSeqs(missingReportSeq)

    override fun findRepresentativeImageSeqs(missingReportSeqs: Collection<Int>): Map<Int, Long> =
        if (missingReportSeqs.isEmpty()) emptyMap()
        else imageJpaRepository.findRepresentatives(missingReportSeqs).associate { it.missingReportSeq to it.imageSeq }

    override fun replaceImages(missingReportSeq: Int, imageSeqs: List<Long>) {
        imageJpaRepository.deleteByMissingReportSeq(missingReportSeq)
        imageJpaRepository.saveAllAndFlush(
            imageSeqs.mapIndexed { i, imageSeq -> MissingReportImage(missingReportSeq, imageSeq, (i + 1).toShort()) },
        )
    }

    override fun findImageKeys(imageSeqs: List<Long>): List<String> {
        if (imageSeqs.isEmpty()) return emptyList()
        val keys = reportJpaRepository.findImageKeys(imageSeqs).associate { it.imageSeq to it.imageKey }
        return imageSeqs.mapNotNull(keys::get)
    }

    override fun findAuthor(userSeq: Int) = reportJpaRepository.findAuthor(userSeq)

    override fun isBookmarked(userSeq: Int, missingReportSeq: Int) = reportJpaRepository.isBookmarked(userSeq, missingReportSeq.toLong())

    override fun countComments(missingReportSeq: Int) = reportJpaRepository.countComments(missingReportSeq)

    /** 7.1 필터(Q463). 생략한 조건은 걸지 않는다 */
    private fun conditions(filter: MissingSearchFilter): List<BooleanExpression> {
        val m = missingReport
        return listOfNotNull(
            m.isDeleted.isFalse,
            Expressions.booleanTemplate(AUTHOR_ACTIVE.replace("m.userSeq", "{0}"), m.userSeq),
            filter.status?.let(m.status::eq),
            // occurredDttm 기준 KST 일 단위, 양끝 포함
            filter.occurredFrom?.let { m.occurredDttm.goe(it.atStartOfDay(KST).toOffsetDateTime()) },
            filter.occurredTo?.let { m.occurredDttm.lt(it.plusDays(1).atStartOfDay(KST).toOffsetDateTime()) },
            filter.districtCode?.let(m.districtCode::eq),
            // 종은 조합 → 품종 → 종. 기준정보 엔티티를 import하지 않고 이름으로만 참조
            filter.speciesSeq?.let {
                Expressions.booleanTemplate(
                    "{0} in (select c.combinationSeq from AnimalCombination c, AnimalBreed b " +
                        "where b.breedSeq = c.breedSeq and b.speciesSeq = {1})",
                    m.combinationSeq, it,
                )
            },
            // 포함 매칭: MALE/FEMALE은 UNKNOWN도, UNKNOWN은 UNKNOWN만
            filter.gender?.let { if (it == MissingGender.UNKNOWN) m.gender.eq(it) else m.gender.`in`(it, MissingGender.UNKNOWN) },
            // 포함 매칭: true/false 모두 NULL(모름)도
            filter.isNeutered?.let { m.isNeutered.eq(it).or(m.isNeutered.isNull) },
            filter.keyword?.trim()?.takeIf(String::isNotEmpty)?.let { m.description.containsIgnoreCase(it) },
        )
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")

        /** 7.1 정렬 화이트리스트 regDttm만 */
        val SORT_POLICY = SortPolicy.regDttmOnly(missingReport.regDttm, missingReport.missingReportSeq)
    }
}
