package com.ssafy.animal.core.pet.adapter.`in`.web

import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.pet.application.port.`in`.CreatePetCommand
import com.ssafy.animal.core.pet.application.port.`in`.CreatedPet
import com.ssafy.animal.core.pet.application.port.`in`.PetDetail
import com.ssafy.animal.core.pet.application.port.`in`.PetImageRef
import com.ssafy.animal.core.pet.application.port.`in`.PetSummary
import com.ssafy.animal.core.pet.application.port.`in`.UpdatePetCommand
import com.ssafy.animal.core.pet.domain.PetGender
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional

/** 6.3. 길이 검증은 수정과 같은 규칙이라 서비스가 한다 */
data class CreatePetRequest(
    @field:NotBlank
    val name: String?,
    @field:NotNull
    val combinationSeq: Int?,
    @field:NotNull
    val gender: PetGender?,
    val age: String? = null,
    val weight: String? = null,
    val isNeutered: Boolean? = null,
    val microchipNumber: String? = null,
    val description: String? = null,
    @field:NotNull
    val imageSeqs: List<Long>?,
) {
    fun toCommand() =
        CreatePetCommand(name!!, combinationSeq!!, gender!!, age, weight, isNeutered, microchipNumber, description, imageSeqs!!)
}

/** 6.4 부분 수정. 선택 필드는 미전달 null, 명시적 null은 Optional.empty() */
data class UpdatePetRequest(
    val name: String? = null,
    val combinationSeq: Int? = null,
    val gender: PetGender? = null,
    val age: Optional<String>? = null,
    val weight: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val microchipNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = UpdatePetCommand(name, combinationSeq, gender, age, weight, isNeutered, microchipNumber, description, imageSeqs)
}

data class CreatedPetResponse(val petSeq: Int, val imageCount: Int, val representativeImageSeq: Long) {
    companion object {
        fun from(created: CreatedPet) = CreatedPetResponse(created.petSeq, created.imageCount, created.representativeImageSeq)
    }
}

data class PetSummaryResponse(
    val petSeq: Int,
    val name: String,
    val classification: AnimalClassification?,
    val gender: PetGender,
    val representativeImageUrl: String?,
    val regDttm: OffsetDateTime?,
) {
    companion object {
        fun from(summary: PetSummary) = with(summary.pet) {
            PetSummaryResponse(petSeq!!, name, summary.classification, gender, summary.representativeImageUrl, regDttm)
        }
    }
}

data class PetDetailResponse(
    val petSeq: Int,
    val name: String,
    val classification: AnimalClassification?,
    val gender: PetGender,
    val representativeImageUrl: String?,
    val age: String?,
    val weight: String?,
    val isNeutered: Boolean?,
    val microchipNumber: String?,
    val description: String?,
    val images: List<PetImageRef>,
    val regDttm: OffsetDateTime?,
    val modDttm: OffsetDateTime?,
) {
    companion object {
        fun from(detail: PetDetail) = with(detail.pet) {
            PetDetailResponse(
                petSeq!!, name, detail.classification, gender, detail.images.firstOrNull()?.imageUrl,
                age, weight, isNeutered, microchipNumber, description, detail.images, regDttm, modDttm,
            )
        }
    }
}
