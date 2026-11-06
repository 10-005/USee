package com.ssafy.animal.core.shelter.adapter.out.persistence

import com.ssafy.animal.core.shelter.application.port.out.ShelterMemberRepository
import com.ssafy.animal.core.shelter.domain.ShelterMember
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface ShelterMemberJpaRepository : JpaRepository<ShelterMember, Int> {
    fun findByUserSeq(userSeq: Int): ShelterMember?
}

@Repository
class ShelterMemberPersistenceAdapter(
    private val shelterMemberJpaRepository: ShelterMemberJpaRepository,
) : ShelterMemberRepository {

    override fun findShelterSeq(userSeq: Int): Int? = shelterMemberJpaRepository.findByUserSeq(userSeq)?.shelterSeq
}
