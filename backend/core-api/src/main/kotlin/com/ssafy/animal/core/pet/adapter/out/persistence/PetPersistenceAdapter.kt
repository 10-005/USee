package com.ssafy.animal.core.pet.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.core.pet.application.port.out.PetRepository
import com.ssafy.animal.core.pet.domain.Pet
import com.ssafy.animal.core.pet.domain.PetImage
import com.ssafy.animal.core.pet.domain.QPet.Companion.pet
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface PetJpaRepository : JpaRepository<Pet, Int> {
    fun findByPetSeqAndUserSeqAndIsDeletedFalse(petSeq: Int, userSeq: Int): Pet?
}

interface PetImageJpaRepository : JpaRepository<PetImage, Long> {
    @Query("select i.imageSeq from PetImage i where i.petSeq = :petSeq order by i.displayOrder")
    fun findImageSeqs(@Param("petSeq") petSeq: Int): List<Long>

    @Query("select i from PetImage i where i.petSeq in :petSeqs and i.displayOrder = 1")
    fun findRepresentatives(@Param("petSeqs") petSeqs: Collection<Int>): List<PetImage>

    // 벌크 삭제라 바로 DB에 나간다. 순서를 바꿔 다시 넣어도 UNIQUE(pet_seq, display_order)에 걸리지 않는다
    @Modifying
    @Query("delete from PetImage i where i.petSeq = :petSeq")
    fun deleteByPetSeq(@Param("petSeq") petSeq: Int)
}

@Repository
class PetPersistenceAdapter(
    private val petJpaRepository: PetJpaRepository,
    private val petImageJpaRepository: PetImageJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : PetRepository {

    override fun saveAndFlush(pet: Pet): Pet = petJpaRepository.saveAndFlush(pet)

    override fun findOwned(userSeq: Int, petSeq: Int) = petJpaRepository.findByPetSeqAndUserSeqAndIsDeletedFalse(petSeq, userSeq)

    override fun findPage(userSeq: Int, pageable: Pageable): Page<Pet> {
        val condition = pet.userSeq.eq(userSeq).and(pet.isDeleted.isFalse)
        val content = queryFactory.selectFrom(pet)
            .where(condition)
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(pet.count()).from(pet).where(condition).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun findImageSeqs(petSeq: Int) = petImageJpaRepository.findImageSeqs(petSeq)

    override fun findRepresentativeImageSeqs(petSeqs: Collection<Int>): Map<Int, Long> =
        if (petSeqs.isEmpty()) emptyMap()
        else petImageJpaRepository.findRepresentatives(petSeqs).associate { it.petSeq to it.imageSeq }

    override fun replaceImages(petSeq: Int, imageSeqs: List<Long>) {
        petImageJpaRepository.deleteByPetSeq(petSeq)
        petImageJpaRepository.saveAllAndFlush(imageSeqs.mapIndexed { i, imageSeq -> PetImage(petSeq, imageSeq, (i + 1).toShort()) })
    }

    companion object {
        /** 6.1 정렬 화이트리스트 regDttm·name, 기본 regDttm desc, 동률 PK desc */
        val SORT_POLICY = SortPolicy(
            fields = mapOf("regDttm" to pet.regDttm, "name" to pet.name),
            default = listOf(pet.regDttm.desc()),
            tieBreaker = pet.petSeq.desc(),
        )
    }
}
