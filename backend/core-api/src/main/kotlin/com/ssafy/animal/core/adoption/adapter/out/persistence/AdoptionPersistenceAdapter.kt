package com.ssafy.animal.core.adoption.adapter.out.persistence

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.CaseBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.core.adoption.application.port.out.AdoptionApplicationRepository
import com.ssafy.animal.core.adoption.application.port.out.AdoptionQuestionRepository
import com.ssafy.animal.core.adoption.application.port.out.AdoptionReferenceReader
import com.ssafy.animal.core.adoption.domain.AdoptionApplication
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.adoption.domain.AdoptionFormQuestion
import com.ssafy.animal.core.adoption.domain.Applicant
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.adoption.domain.QAdoptionApplication.Companion.adoptionApplication
import jakarta.persistence.LockModeType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

interface AdoptionApplicationJpaRepository : JpaRepository<AdoptionApplication, Long> {
    @Query("select a from AdoptionApplication a where a.applicationSeq = :seq and a.isDeleted = false")
    fun findActiveRow(@Param("seq") applicationSeq: Long): AdoptionApplication?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdoptionApplication a where a.applicationSeq = :seq and a.isDeleted = false")
    fun findActiveRowForUpdate(@Param("seq") applicationSeq: Long): AdoptionApplication?

    @Query("select a.animalSeq from AdoptionApplication a where a.applicationSeq = :seq and a.isDeleted = false")
    fun findAnimalSeq(@Param("seq") applicationSeq: Long): Int?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select a from AdoptionApplication a
        where a.userSeq = :userSeq and a.animalSeq = :animalSeq and a.isDeleted = false and a.status in :statuses
        """,
    )
    fun findByUserAndAnimalForUpdate(
        @Param("userSeq") userSeq: Int,
        @Param("animalSeq") animalSeq: Int,
        @Param("statuses") statuses: Collection<ApplicationStatus>,
    ): AdoptionApplication?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdoptionApplication a where a.animalSeq = :animalSeq and a.isDeleted = false and a.status in :statuses")
    fun findByAnimalForUpdate(@Param("animalSeq") animalSeq: Int, @Param("statuses") statuses: Collection<ApplicationStatus>): List<AdoptionApplication>

    @Query("select count(a) > 0 from AdoptionApplication a where a.animalSeq = :animalSeq and a.isDeleted = false and a.status in :statuses")
    fun existsByAnimal(@Param("animalSeq") animalSeq: Int, @Param("statuses") statuses: Collection<ApplicationStatus>): Boolean

    // 벌크 UPDATE는 @PreUpdate를 거치지 않으므로 mod_dttm을 직접 넣는다
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update AdoptionApplication a set a.status = :canceled, a.modDttm = :now, a.modUsrSeq = :userSeq
        where a.userSeq = :userSeq and a.isDeleted = false and a.status in :statuses
        """,
    )
    fun cancelByUser(
        @Param("userSeq") userSeq: Int,
        @Param("statuses") statuses: Collection<ApplicationStatus>,
        @Param("canceled") canceled: ApplicationStatus,
        @Param("now") now: OffsetDateTime,
    ): Int

    // 다른 도메인 테이블 읽기(native)

    @Query(nativeQuery = true, value = "select name, birth_date as birthDate, phone from users where user_seq = :userSeq")
    fun findApplicant(@Param("userSeq") userSeq: Int): ApplicantProjection?

    @Query(
        nativeQuery = true,
        value = """
        select m.user_seq from shelter_members m join users u on u.user_seq = m.user_seq
        where m.shelter_seq = :shelterSeq and u.is_deleted = false
        """,
    )
    fun findShelterUserSeqs(@Param("shelterSeq") shelterSeq: Int): List<Int>
}

interface ApplicantProjection {
    val name: String?
    val birthDate: LocalDate?
    val phone: String?
}

interface AdoptionFormQuestionJpaRepository : JpaRepository<AdoptionFormQuestion, Int>

@Repository
class AdoptionPersistenceAdapter(
    private val applicationJpaRepository: AdoptionApplicationJpaRepository,
    private val questionJpaRepository: AdoptionFormQuestionJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : AdoptionApplicationRepository, AdoptionQuestionRepository, AdoptionReferenceReader {

    override fun saveAndFlush(application: AdoptionApplication): AdoptionApplication = try {
        applicationJpaRepository.saveAndFlush(application)
    } catch (e: DataIntegrityViolationException) {
        // 사전 조회를 지나 부분 UNIQUE에 걸린 경합(중복 탭)은 사전 조회와 같은 409
        if ("uq_app_active_user_animal" in e.mostSpecificCause.message.orEmpty()) {
            throw BusinessException(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS)
        }
        throw e
    }

    override fun find(applicationSeq: Long) = applicationJpaRepository.findActiveRow(applicationSeq)

    override fun findForUpdate(applicationSeq: Long) = applicationJpaRepository.findActiveRowForUpdate(applicationSeq)

    override fun findAnimalSeq(applicationSeq: Long) = applicationJpaRepository.findAnimalSeq(applicationSeq)

    override fun findActiveForUpdate(userSeq: Int, animalSeq: Int) =
        applicationJpaRepository.findByUserAndAnimalForUpdate(userSeq, animalSeq, ApplicationStatus.ACTIVE)

    override fun findActiveByAnimalForUpdate(animalSeq: Int) = applicationJpaRepository.findByAnimalForUpdate(animalSeq, ApplicationStatus.ACTIVE)

    override fun existsActiveByAnimal(animalSeq: Int) = applicationJpaRepository.existsByAnimal(animalSeq, ApplicationStatus.ACTIVE)

    override fun delete(application: AdoptionApplication) = applicationJpaRepository.delete(application)

    override fun findMine(userSeq: Int, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<AdoptionApplication> {
        val where = listOfNotNull(
            adoptionApplication.userSeq.eq(userSeq),
            adoptionApplication.isDeleted.isFalse,
            status?.let(adoptionApplication.status::eq),
            animalSeq?.let(adoptionApplication.animalSeq::eq),
        )
        // Q459: displayDttm desc 고정(sort 파라미터는 받지 않는다)
        return page(where, pageable, DISPLAY_DTTM.desc(), adoptionApplication.applicationSeq.desc())
    }

    override fun findReceived(shelterSeq: Int, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<AdoptionApplication> {
        val where = receivedConditions(shelterSeq, animalSeq) + listOfNotNull(status?.let(adoptionApplication.status::eq))
        return page(where, pageable, *RECEIVED_SORT.toOrderSpecifiers(pageable.sort))
    }

    override fun countReceived(shelterSeq: Int?, animalSeq: Int?): Map<ApplicationStatus, Long> =
        queryFactory.select(adoptionApplication.status, adoptionApplication.count())
            .from(adoptionApplication)
            .where(*receivedConditions(shelterSeq, animalSeq).toTypedArray())
            .groupBy(adoptionApplication.status)
            .fetch()
            .associate { it.get(0, ApplicationStatus::class.java)!! to (it.get(1, Long::class.java) ?: 0L) }

    override fun cancelActiveOfUser(userSeq: Int, now: OffsetDateTime) =
        applicationJpaRepository.cancelByUser(userSeq, ApplicationStatus.ACTIVE, ApplicationStatus.CANCELED, now)

    /** 보호소에 보이는 범위: 제출된 적 있는 SUBMITTED/APPROVED/ADOPTED/REJECTED(Q422·Q460) */
    private fun receivedConditions(shelterSeq: Int?, animalSeq: Int?): List<BooleanExpression> = listOfNotNull(
        shelterSeq?.let(adoptionApplication.shelterSeq::eq),
        animalSeq?.let(adoptionApplication.animalSeq::eq),
        adoptionApplication.isDeleted.isFalse,
        adoptionApplication.status.`in`(ApplicationStatus.RECEIVED),
        adoptionApplication.submittedDttm.isNotNull,
    )

    private fun page(
        where: List<BooleanExpression>,
        pageable: Pageable,
        vararg orders: com.querydsl.core.types.OrderSpecifier<*>,
    ): Page<AdoptionApplication> {
        val content = queryFactory.selectFrom(adoptionApplication)
            .where(*where.toTypedArray())
            .orderBy(*orders)
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(adoptionApplication.count()).from(adoptionApplication).where(*where.toTypedArray()).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    // ===== AdoptionQuestionRepository =====
    override fun findAll(): List<AdoptionFormQuestion> = questionJpaRepository.findAll(Sort.by("displayOrder"))

    // ===== AdoptionReferenceReader =====
    override fun findApplicant(userSeq: Int) = applicationJpaRepository.findApplicant(userSeq)?.let { Applicant(it.name, it.birthDate, it.phone) }

    override fun findShelterUserSeqs(shelterSeq: Int) = applicationJpaRepository.findShelterUserSeqs(shelterSeq)

    companion object {
        /** AdoptionApplication.displayDttm과 같은 식 */
        private val DISPLAY_DTTM = CaseBuilder()
            .`when`(adoptionApplication.status.eq(ApplicationStatus.DRAFT))
            .then(adoptionApplication.modDttm.coalesce(adoptionApplication.regDttm))
            .otherwise(adoptionApplication.submittedDttm.coalesce(adoptionApplication.modDttm, adoptionApplication.regDttm))

        /** 12.9 화이트리스트 submittedDttm(기본)·regDttm */
        private val RECEIVED_SORT = SortPolicy(
            fields = mapOf("submittedDttm" to adoptionApplication.submittedDttm, "regDttm" to adoptionApplication.regDttm),
            default = listOf(adoptionApplication.submittedDttm.desc()),
            tieBreaker = adoptionApplication.applicationSeq.desc(),
        )
    }
}
