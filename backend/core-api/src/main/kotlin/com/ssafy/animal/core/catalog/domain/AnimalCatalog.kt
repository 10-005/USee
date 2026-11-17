package com.ssafy.animal.core.catalog.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

enum class SpeciesCode { DOG, CAT, ETC }

/** 모의 입양 성격 4종(M7). 품종마다 시드로 배정되어 null이 없다(Q482) */
enum class Personality { ACTIVE, TIMID, AGGRESSIVE, FRIENDLY }

// 08~11번 기준정보는 Flyway 시드로만 넣고 앱은 읽기만 한다(Q412). 그래서 @Immutable, FK는 숫자 컬럼으로 둔다

/** DB 명세 08. animal_species */
@Entity
@Immutable
@Table(name = "animal_species")
class AnimalSpecies(
    speciesSeq: Int,
    speciesName: String,
    speciesCode: SpeciesCode,
) : BaseCreatedEntity() {

    @Id
    var speciesSeq: Int = speciesSeq
        protected set

    @Column(nullable = false, length = 50)
    var speciesName: String = speciesName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    var speciesCode: SpeciesCode = speciesCode
        protected set
}

/** DB 명세 09. animal_breeds */
@Entity
@Immutable
@Table(name = "animal_breeds")
class AnimalBreed(
    breedSeq: Int,
    speciesSeq: Int,
    breedName: String,
    personality: Personality,
) : BaseCreatedEntity() {

    @Id
    var breedSeq: Int = breedSeq
        protected set

    @Column(nullable = false)
    var speciesSeq: Int = speciesSeq
        protected set

    @Column(nullable = false, length = 100)
    var breedName: String = breedName
        protected set

    @Column(length = 60)
    var breedCode: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var personality: Personality = personality
        protected set

    /** 품종 대표 이미지(시드, images.reg_usr_seq NULL). 이미지 참조 정리 대상이 아니다(Q482) */
    var representativeImageSeq: Long? = null
        protected set
}

/** DB 명세 10. animal_colors */
@Entity
@Immutable
@Table(name = "animal_colors")
class AnimalColor(
    colorSeq: Int,
    colorName: String,
) : BaseCreatedEntity() {

    @Id
    var colorSeq: Int = colorSeq
        protected set

    @Column(nullable = false, length = 100)
    var colorName: String = colorName
        protected set
}

/** DB 명세 11. animal_combinations. 네 동물 엔터티(반려·실종·발견·보호)는 이 PK 하나만 저장한다(Q334) */
@Entity
@Immutable
@Table(name = "animal_combinations")
class AnimalCombination(
    combinationSeq: Int,
    breedSeq: Int,
    colorSeq: Int,
) : BaseCreatedEntity() {

    @Id
    var combinationSeq: Int = combinationSeq
        protected set

    @Column(nullable = false)
    var breedSeq: Int = breedSeq
        protected set

    @Column(nullable = false)
    var colorSeq: Int = colorSeq
        protected set
}
