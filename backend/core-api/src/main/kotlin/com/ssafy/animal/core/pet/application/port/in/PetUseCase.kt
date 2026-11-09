package com.ssafy.animal.core.pet.application.port.`in`

import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.pet.domain.Pet
import com.ssafy.animal.core.pet.domain.PetGender
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.Optional

/** 6장 내 반려동물. 없음·삭제·타인 소유는 모두 404 USER_PET_NOT_FOUND(Q434) */
interface PetUseCase {
    fun getPage(userSeq: Long, pageable: Pageable): Page<PetSummary>
    fun get(userSeq: Long, petSeq: Long): PetDetail
    fun create(userSeq: Long, command: CreatePetCommand): CreatedPet
    fun update(userSeq: Long, petSeq: Long, command: UpdatePetCommand): PetDetail
    fun delete(userSeq: Long, petSeq: Long)
}

data class CreatePetCommand(
    val name: String,
    val combinationSeq: Int,
    val gender: PetGender,
    val age: String? = null,
    val weight: String? = null,
    val isNeutered: Boolean? = null,
    val microchipNumber: String? = null,
    val description: String? = null,
    val imageSeqs: List<Long>,
)

/** null = 미전달(유지). 선택 필드는 Optional.empty()가 명시적 null. imageSeqs는 전체 최종 배열(Q424) */
data class UpdatePetCommand(
    val name: String? = null,
    val combinationSeq: Int? = null,
    val gender: PetGender? = null,
    val age: Optional<String>? = null,
    val weight: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val microchipNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val imageSeqs: List<Long>? = null,
)

data class CreatedPet(val petSeq: Int, val imageCount: Int, val representativeImageSeq: Long)

data class PetSummary(val pet: Pet, val classification: AnimalClassification?, val representativeImageUrl: String?)

data class PetDetail(val pet: Pet, val classification: AnimalClassification?, val images: List<PetImageRef>)

/** 명세 1.4 ImageRef */
data class PetImageRef(
    @field:Schema(description = "이미지 번호", example = "1001")
    val imageSeq: Long,
    @field:Schema(description = "공개 이미지 URL. 이미지 행이 없으면 null", nullable = true, example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val imageUrl: String?,
    @field:Schema(description = "표시 순서(1부터). 1이 대표 사진", example = "1")
    val displayOrder: Int,
)
