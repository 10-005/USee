package com.ssafy.animal.core.pet.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

enum class PetGender { MALE, FEMALE, UNKNOWN }

/** DB 명세 12. pets. mod_usr_seq는 없다(Q396) */
@Entity
@Table(name = "pets")
class Pet(
    userSeq: Int,
    name: String,
    combinationSeq: Int,
    gender: PetGender,
    age: String?,
    weight: String?,
    isNeutered: Boolean?,
    microchipNumber: String?,
    description: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var petSeq: Int? = null
        protected set

    @Column(nullable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false, length = 100)
    var name: String = name

    @Column(nullable = false)
    var combinationSeq: Int = combinationSeq

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var gender: PetGender = gender

    @Column(length = 30)
    var age: String? = age

    @Column(length = 30)
    var weight: String? = weight

    var isNeutered: Boolean? = isNeutered

    @Column(length = 30)
    var microchipNumber: String? = microchipNumber

    @Column(columnDefinition = "text")
    var description: String? = description

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    fun delete() {
        isDeleted = true
    }
}

/** DB 명세 13. pet_images. display_order 1이 대표 */
@Entity
@Table(name = "pet_images")
class PetImage(
    petSeq: Int,
    imageSeq: Long,
    displayOrder: Short,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var petImageSeq: Long? = null
        protected set

    @Column(nullable = false)
    var petSeq: Int = petSeq
        protected set

    @Column(nullable = false)
    var imageSeq: Long = imageSeq
        protected set

    @Column(nullable = false)
    var displayOrder: Short = displayOrder
        protected set
}
