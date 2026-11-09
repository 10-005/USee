package com.ssafy.animal.core.catalog.adapter.out.persistence

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.out.CatalogRepository
import com.ssafy.animal.core.catalog.domain.AnimalBreed
import com.ssafy.animal.core.catalog.domain.AnimalColor
import com.ssafy.animal.core.catalog.domain.AnimalSpecies
import com.ssafy.animal.core.catalog.domain.District
import com.ssafy.animal.core.catalog.domain.QAnimalBreed.Companion.animalBreed
import com.ssafy.animal.core.catalog.domain.QAnimalColor.Companion.animalColor
import com.ssafy.animal.core.catalog.domain.QAnimalCombination.Companion.animalCombination
import com.ssafy.animal.core.catalog.domain.QAnimalSpecies.Companion.animalSpecies
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface DistrictJpaRepository : JpaRepository<District, String> {
    fun findBySidoNameOrderByDistrictCode(sidoName: String): List<District>
}

interface AnimalSpeciesJpaRepository : JpaRepository<AnimalSpecies, Int>

interface AnimalBreedJpaRepository : JpaRepository<AnimalBreed, Int> {
    fun findBySpeciesSeqOrderByBreedSeq(speciesSeq: Int): List<AnimalBreed>
}

interface AnimalColorJpaRepository : JpaRepository<AnimalColor, Int>

@Repository
class CatalogPersistenceAdapter(
    private val queryFactory: JPAQueryFactory,
    private val districtJpaRepository: DistrictJpaRepository,
    private val speciesJpaRepository: AnimalSpeciesJpaRepository,
    private val breedJpaRepository: AnimalBreedJpaRepository,
    private val colorJpaRepository: AnimalColorJpaRepository,
) : CatalogRepository {

    override fun findClassifications(combinationSeqs: Collection<Int>): List<AnimalClassification> =
        classifications(animalCombination.combinationSeq.`in`(combinationSeqs))

    override fun findCombinations(breedSeq: Int?, colorSeq: Int?): List<AnimalClassification> =
        classifications(breedSeq?.let(animalCombination.breedSeq::eq), colorSeq?.let(animalCombination.colorSeq::eq))

    private fun classifications(vararg conditions: BooleanExpression?): List<AnimalClassification> =
        queryFactory
            .select(
                animalCombination.combinationSeq, animalSpecies.speciesSeq, animalSpecies.speciesCode, animalSpecies.speciesName,
                animalBreed.breedSeq, animalBreed.breedName, animalColor.colorSeq, animalColor.colorName,
            )
            .from(animalCombination)
            .join(animalBreed).on(animalBreed.breedSeq.eq(animalCombination.breedSeq))
            .join(animalSpecies).on(animalSpecies.speciesSeq.eq(animalBreed.speciesSeq))
            .join(animalColor).on(animalColor.colorSeq.eq(animalCombination.colorSeq))
            .where(*conditions)
            .orderBy(animalCombination.combinationSeq.asc())
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

    override fun findAllSpecies(): List<AnimalSpecies> = speciesJpaRepository.findAll(Sort.by("speciesSeq"))

    override fun findBreeds(speciesSeq: Int) = breedJpaRepository.findBySpeciesSeqOrderByBreedSeq(speciesSeq)

    override fun findAllColors(): List<AnimalColor> = colorJpaRepository.findAll(Sort.by("colorSeq"))

    override fun findDistricts(sidoName: String?): List<District> =
        if (sidoName == null) districtJpaRepository.findAll(Sort.by("districtCode"))
        else districtJpaRepository.findBySidoNameOrderByDistrictCode(sidoName)
}
