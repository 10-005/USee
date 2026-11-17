package com.ssafy.animal.core.animal.adapter.out.persistence

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.core.animal.application.port.out.AnimalFilter
import com.ssafy.animal.core.animal.application.port.out.AnimalReferenceReader
import com.ssafy.animal.core.animal.application.port.out.RescueRow
import com.ssafy.animal.core.animal.application.port.out.ShelterAnimalRepository
import com.ssafy.animal.core.animal.application.port.out.ShelterRow
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalErrorCode
import com.ssafy.animal.core.animal.domain.AnimalReturn
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.Gender
import com.ssafy.animal.core.animal.domain.ProtectionStatus
import com.ssafy.animal.core.animal.domain.QShelterAnimal.Companion.shelterAnimal
import com.ssafy.animal.core.animal.domain.QShelterAnimalImage.Companion.shelterAnimalImage
import com.ssafy.animal.core.animal.domain.ShelterAnimal
import com.ssafy.animal.core.animal.domain.ShelterAnimalImage
import jakarta.persistence.LockModeType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.ZoneId

interface ShelterAnimalJpaRepository : JpaRepository<ShelterAnimal, Int> {
    @Query("select a from ShelterAnimal a where a.animalSeq = :animalSeq and a.isDeleted = false")
    fun findActive(@Param("animalSeq") animalSeq: Int): ShelterAnimal?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ShelterAnimal a where a.animalSeq = :animalSeq and a.isDeleted = false")
    fun findActiveForUpdate(@Param("animalSeq") animalSeq: Int): ShelterAnimal?

    // 아래는 다른 도메인 테이블 읽기. 엔티티를 import하지 않고 native로만 읽는다(분할 원칙 3)

    @Query(
        nativeQuery = true,
        value = """
        select rescue_seq as rescueSeq, status, is_deleted as deleted, rescued_dttm as rescuedDttm,
               rescue_reason_code as rescueReasonCode, rescue_address as rescueAddress,
               rescue_address_detail as rescueAddressDetail, rescue_memo as rescueMemo
        from rescue_records where rescue_seq = :rescueSeq
        """,
    )
    fun findRescue(@Param("rescueSeq") rescueSeq: Int): RescueProjection?

    @Query(
        nativeQuery = true,
        value = """
        select shelter_seq as shelterSeq, shelter_name as shelterName, phone, address, district_code as districtCode
        from shelters where shelter_seq in (:shelterSeqs)
        """,
    )
    fun findShelters(@Param("shelterSeqs") shelterSeqs: Collection<Int>): List<ShelterProjection>

    @Query(nativeQuery = true, value = "select shelter_seq from shelters where district_code = :districtCode and is_deleted = false")
    fun findShelterSeqsInDistrict(@Param("districtCode") districtCode: String): List<Int>

    @Query(
        nativeQuery = true,
        value = """
        select c.combination_seq from animal_combinations c join animal_breeds b on b.breed_seq = c.breed_seq
        where b.species_seq = :speciesSeq
        """,
    )
    fun findCombinationSeqsOfSpecies(@Param("speciesSeq") speciesSeq: Int): List<Int>

    @Query(nativeQuery = true, value = "select image_key from images where image_seq in (:imageSeqs)")
    fun findImageKeys(@Param("imageSeqs") imageSeqs: Collection<Long>): List<String>

    @Query(
        nativeQuery = true,
        value = """
        select exists(select 1 from bookmarks where user_seq = :userSeq and target_type = 'SHELTER_ANIMAL' and target_seq = :animalSeq)
        """,
    )
    fun isBookmarked(@Param("userSeq") userSeq: Int, @Param("animalSeq") animalSeq: Long): Boolean

    @Query(nativeQuery = true, value = "select user_seq from users where role = 'USER' and is_deleted = false and phone = :phone")
    fun findActiveUserSeqsByPhone(@Param("phone") phone: String): List<Int>
}

/** Kotlin의 isXxx 프로퍼티는 JavaBeans 이름이 xxx라 별칭을 deleted로 맞춘다 */
interface RescueProjection {
    val rescueSeq: Int
    val status: String
    val deleted: Boolean
    /** native timestamptz는 Instant로 온다 */
    val rescuedDttm: Instant?
    val rescueReasonCode: String?
    val rescueAddress: String?
    val rescueAddressDetail: String?
    val rescueMemo: String?
}

interface ShelterProjection {
    val shelterSeq: Int
    val shelterName: String
    val phone: String
    val address: String
    val districtCode: String?
}

interface ShelterAnimalImageJpaRepository : JpaRepository<ShelterAnimalImage, Long> {
    fun findByAnimalSeqOrderByDisplayOrder(animalSeq: Int): List<ShelterAnimalImage>

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ShelterAnimalImage i where i.animalSeq = :animalSeq")
    fun deleteByAnimalSeq(@Param("animalSeq") animalSeq: Int)
}

interface AnimalReturnJpaRepository : JpaRepository<AnimalReturn, Long> {
    fun findByAnimalSeq(animalSeq: Int): AnimalReturn?
}

@Repository
class ShelterAnimalPersistenceAdapter(
    private val animalJpaRepository: ShelterAnimalJpaRepository,
    private val imageJpaRepository: ShelterAnimalImageJpaRepository,
    private val returnJpaRepository: AnimalReturnJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ShelterAnimalRepository, AnimalReferenceReader {

    override fun save(animal: ShelterAnimal): ShelterAnimal = animalJpaRepository.save(animal)

    override fun flush() = try {
        animalJpaRepository.flush()
    } catch (e: DataIntegrityViolationException) {
        // 서버가 네 상태 컬럼을 함께 계산하므로 여기 걸리는 것은 경합뿐이다(11.6)
        if ("ck_shelter_" in e.mostSpecificCause.message.orEmpty()) throw BusinessException(AnimalErrorCode.ANIMAL_INVALID_STATUS_COMBINATION)
        throw e
    }

    override fun findActive(animalSeq: Int) = animalJpaRepository.findActive(animalSeq)

    override fun findActiveForUpdate(animalSeq: Int) = animalJpaRepository.findActiveForUpdate(animalSeq)

    override fun findAllById(animalSeqs: Collection<Int>): List<ShelterAnimal> = animalJpaRepository.findAllById(animalSeqs)

    override fun search(filter: AnimalFilter, pageable: Pageable): Page<ShelterAnimal> {
        val where = conditions(filter) + listOfNotNull(filter.status?.let(::statusIs))
        val content = queryFactory.selectFrom(shelterAnimal)
            .where(*where.toTypedArray())
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(shelterAnimal.count()).from(shelterAnimal).where(*where.toTypedArray()).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun countByStatus(filter: AnimalFilter): Map<AnimalStatus, Long> =
        queryFactory.select(shelterAnimal.protectionStatus, shelterAnimal.adoptionStatus, shelterAnimal.count())
            .from(shelterAnimal)
            .where(*conditions(filter).toTypedArray())
            .groupBy(shelterAnimal.protectionStatus, shelterAnimal.adoptionStatus)
            .fetch()
            .associate { AnimalStatus.of(it.get(0, ProtectionStatus::class.java)!!, it.get(1, AdoptionStatus::class.java)!!) to (it.get(2, Long::class.java) ?: 0L) }

    /** status를 뺀 공통 조건. 비삭제만 */
    private fun conditions(f: AnimalFilter): List<BooleanExpression> = listOfNotNull(
        shelterAnimal.isDeleted.isFalse,
        f.shelterSeqs?.let(shelterAnimal.shelterSeq::`in`),
        f.regFrom?.let(shelterAnimal.regDttm::goe),
        f.regBefore?.let(shelterAnimal.regDttm::lt),
        f.combinationSeqs?.let(shelterAnimal.combinationSeq::`in`),
        // 포함 매칭(Q463): 모름(UNKNOWN·NULL)도 함께 보여 준다
        f.gender?.let { if (it == Gender.UNKNOWN) shelterAnimal.gender.eq(it) else shelterAnimal.gender.`in`(it, Gender.UNKNOWN) },
        f.isNeutered?.let { shelterAnimal.isNeutered.eq(it).or(shelterAnimal.isNeutered.isNull) },
        f.keyword?.let { shelterAnimal.description.containsIgnoreCase(it).or(shelterAnimal.healthCheck.containsIgnoreCase(it)) },
    )

    private fun statusIs(status: AnimalStatus) =
        shelterAnimal.protectionStatus.eq(status.protectionStatus).and(shelterAnimal.adoptionStatus.eq(status.adoptionStatus))

    override fun findImages(animalSeq: Int) = imageJpaRepository.findByAnimalSeqOrderByDisplayOrder(animalSeq)

    override fun findRepresentativeImageSeqs(animalSeqs: Collection<Int>): Map<Int, Long> {
        if (animalSeqs.isEmpty()) return emptyMap()
        return queryFactory.selectFrom(shelterAnimalImage)
            .where(shelterAnimalImage.animalSeq.`in`(animalSeqs))
            .orderBy(shelterAnimalImage.displayOrder.asc())
            .fetch()
            .groupBy { it.animalSeq }
            .mapValues { (_, images) -> images.first().imageSeq }
    }

    override fun replaceImages(animalSeq: Int, imageSeqs: List<Long>) {
        // UNIQUE(animal_seq, display_order) 때문에 순서만 바꾸는 UPDATE는 중간에 충돌한다. 지우고 다시 만든다
        imageJpaRepository.deleteByAnimalSeq(animalSeq)
        imageJpaRepository.saveAllAndFlush(imageSeqs.mapIndexed { i, imageSeq -> ShelterAnimalImage(animalSeq, imageSeq, (i + 1).toShort()) })
    }

    override fun findReturn(animalSeq: Int) = returnJpaRepository.findByAnimalSeq(animalSeq)

    override fun saveReturn(animalReturn: AnimalReturn): AnimalReturn = returnJpaRepository.saveAndFlush(animalReturn)

    // ===== AnimalReferenceReader =====

    override fun findRescue(rescueSeq: Int) = animalJpaRepository.findRescue(rescueSeq)?.let {
        RescueRow(it.rescueSeq, it.status, it.deleted, it.rescuedDttm?.atZone(KST)?.toOffsetDateTime(), it.rescueReasonCode, it.rescueAddress, it.rescueAddressDetail, it.rescueMemo)
    }

    override fun findShelters(shelterSeqs: Collection<Int>): Map<Int, ShelterRow> {
        if (shelterSeqs.isEmpty()) return emptyMap()
        return animalJpaRepository.findShelters(shelterSeqs)
            .associate { it.shelterSeq to ShelterRow(it.shelterSeq, it.shelterName, it.phone, it.address, it.districtCode) }
    }

    override fun findShelterSeqsInDistrict(districtCode: String) = animalJpaRepository.findShelterSeqsInDistrict(districtCode)

    override fun findCombinationSeqsOfSpecies(speciesSeq: Int) = animalJpaRepository.findCombinationSeqsOfSpecies(speciesSeq)

    override fun findImageKeys(imageSeqs: Collection<Long>): List<String> =
        if (imageSeqs.isEmpty()) emptyList() else animalJpaRepository.findImageKeys(imageSeqs)

    override fun isBookmarked(userSeq: Int, animalSeq: Int) = animalJpaRepository.isBookmarked(userSeq, animalSeq.toLong())

    override fun findActiveUserSeqsByPhone(phone: String) = animalJpaRepository.findActiveUserSeqsByPhone(phone)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")

        /** 11.1·11.3 화이트리스트는 regDttm만 */
        private val SORT_POLICY = SortPolicy.regDttmOnly(shelterAnimal.regDttm, shelterAnimal.animalSeq)
    }
}
