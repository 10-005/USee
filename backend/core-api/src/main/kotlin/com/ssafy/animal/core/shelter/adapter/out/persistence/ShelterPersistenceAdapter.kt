package com.ssafy.animal.core.shelter.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.common.persistence.SortPolicy
import com.ssafy.animal.core.shelter.application.port.out.ShelterRepository
import com.ssafy.animal.core.shelter.domain.QShelter.Companion.shelter
import com.ssafy.animal.core.shelter.domain.Shelter
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ShelterJpaRepository : JpaRepository<Shelter, Int> {
    fun findByShelterSeqAndIsDeletedFalse(shelterSeq: Int): Shelter?
}

@Repository
class ShelterPersistenceAdapter(
    private val shelterJpaRepository: ShelterJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ShelterRepository {

    override fun findActive(shelterSeq: Int) = shelterJpaRepository.findByShelterSeqAndIsDeletedFalse(shelterSeq)

    override fun findPage(keyword: String?, districtCode: String?, pageable: Pageable): Page<Shelter> {
        val conditions = listOfNotNull(
            shelter.isDeleted.isFalse,
            keyword?.let { shelter.shelterName.containsIgnoreCase(it).or(shelter.address.containsIgnoreCase(it)) },
            districtCode?.let(shelter.districtCode::eq),
        ).toTypedArray()
        val content = queryFactory.selectFrom(shelter)
            .where(*conditions)
            .orderBy(*SORT_POLICY.toOrderSpecifiers(pageable.sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()
        val total = queryFactory.select(shelter.count()).from(shelter).where(*conditions).fetchOne() ?: 0L
        return PageImpl(content, pageable, total)
    }

    override fun saveAndFlush(shelter: Shelter): Shelter = shelterJpaRepository.saveAndFlush(shelter)

    private companion object {
        // 9.1 화이트리스트 shelterName·regDttm, 기본 shelterName asc
        val SORT_POLICY = SortPolicy(
            fields = mapOf("shelterName" to shelter.shelterName, "regDttm" to shelter.regDttm),
            default = listOf(shelter.shelterName.asc()),
            tieBreaker = shelter.shelterSeq.desc(),
        )
    }
}
