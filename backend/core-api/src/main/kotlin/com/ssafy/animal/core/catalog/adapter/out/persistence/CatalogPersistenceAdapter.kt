package com.ssafy.animal.core.catalog.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.out.CatalogRepository
import com.ssafy.animal.core.catalog.domain.District
import com.ssafy.animal.core.catalog.domain.QAnimalBreed.Companion.animalBreed
import com.ssafy.animal.core.catalog.domain.QAnimalColor.Companion.animalColor
import com.ssafy.animal.core.catalog.domain.QAnimalCombination.Companion.animalCombination
import com.ssafy.animal.core.catalog.domain.QAnimalSpecies.Companion.animalSpecies
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface DistrictJpaRepository : JpaRepository<District, String>

@Repository
class CatalogPersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val districtJpaRepository: DistrictJpaRepository,
) : CatalogRepository {

    override fun findClassifications(combinationSeqs: Collection<Int>): List<AnimalClassification> =
        queryFactory
            .select(
                animalCombination.combinationSeq, animalSpecies.speciesSeq, animalSpecies.speciesCode, animalSpecies.speciesName,
                animalBreed.breedSeq, animalBreed.breedName, animalColor.colorSeq, animalColor.colorName,
            )
            .from(animalCombination)
            .join(animalBreed).on(animalBreed.breedSeq.eq(animalCombination.breedSeq))
            .join(animalSpecies).on(animalSpecies.speciesSeq.eq(animalBreed.speciesSeq))
            .join(animalColor).on(animalColor.colorSeq.eq(animalCombination.colorSeq))
            .where(animalCombination.combinationSeq.`in`(combinationSeqs))
            .fetch()
            .map {
                AnimalClassification(
                    combinationSeq = it.get(animalCombination.combinationSeq)!!,
                    speciesSeq = it.get(animalSpecies.speciesSeq)!!,
                    speciesCode = it.get(animalSpecies.speciesCode)!!,
                    speciesName = it.get(animalSpecies.speciesName)!!,
                    breedSeq = it.get(animalBreed.breedSeq)!!,
                    breedName = it.get(animalBreed.breedName)!!,
                    colorSeq = it.get(animalColor.colorSeq)!!,
                    colorName = it.get(animalColor.colorName)!!,
                )
            }

    override fun existsDistrict(districtCode: String) = districtJpaRepository.existsById(districtCode)
}
