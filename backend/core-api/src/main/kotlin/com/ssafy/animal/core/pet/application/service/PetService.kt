package com.ssafy.animal.core.pet.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.pet.application.port.`in`.CreatePetCommand
import com.ssafy.animal.core.pet.application.port.`in`.CreatedPet
import com.ssafy.animal.core.pet.application.port.`in`.PetDetail
import com.ssafy.animal.core.pet.application.port.`in`.PetImageRef
import com.ssafy.animal.core.pet.application.port.`in`.PetSummary
import com.ssafy.animal.core.pet.application.port.`in`.PetUseCase
import com.ssafy.animal.core.pet.application.port.`in`.UpdatePetCommand
import com.ssafy.animal.core.pet.application.port.out.PetRepository
import com.ssafy.animal.core.pet.domain.Pet
import com.ssafy.animal.core.pet.domain.PetErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
@Transactional
class PetService(
    private val petRepository: PetRepository,
    private val catalogUseCase: CatalogUseCase,
    private val imageUseCase: ImageUseCase,
) : PetUseCase {

    @Transactional(readOnly = true)
    override fun getPage(userSeq: Long, pageable: Pageable): Page<PetSummary> {
        val page = petRepository.findPage(Math.toIntExact(userSeq), pageable)
        val classifications = catalogUseCase.findClassifications(page.content.map { it.combinationSeq })
        val representatives = petRepository.findRepresentativeImageSeqs(page.content.map { it.petSeq!! })
        val images = imageUseCase.findSummaries(representatives.values)
        return page.map { pet ->
            val imageUrl = representatives[pet.petSeq]?.let { images[it] }?.imageUrl
            PetSummary(pet, classifications[pet.combinationSeq], imageUrl)
        }
    }

    @Transactional(readOnly = true)
    override fun get(userSeq: Long, petSeq: Long) = detail(load(userSeq, petSeq))

    override fun create(userSeq: Long, command: CreatePetCommand): CreatedPet {
        validateText(command.name, command.age, command.weight, command.microchipNumber)
        catalogUseCase.requireClassification(command.combinationSeq)
        requireImages(userSeq, command.imageSeqs)
        val pet = with(command) {
            petRepository.saveAndFlush(
                Pet(Math.toIntExact(userSeq), name.trim(), combinationSeq, gender, age, weight, isNeutered, microchipNumber, description),
            )
        }
        petRepository.replaceImages(pet.petSeq!!, command.imageSeqs)
        return CreatedPet(pet.petSeq!!, command.imageSeqs.size, command.imageSeqs.first())
    }

    override fun update(userSeq: Long, petSeq: Long, command: UpdatePetCommand): PetDetail {
        val pet = load(userSeq, petSeq)
        command.combinationSeq?.let {
            catalogUseCase.requireClassification(it)
            pet.combinationSeq = it
        }
        command.name?.let { pet.name = it.trim() }
        command.gender?.let { pet.gender = it }
        pet.age = command.age.orKeep(pet.age)
        pet.weight = command.weight.orKeep(pet.weight)
        pet.isNeutered = command.isNeutered.orKeep(pet.isNeutered)
        pet.microchipNumber = command.microchipNumber.orKeep(pet.microchipNumber)
        pet.description = command.description.orKeep(pet.description)
        validateText(pet.name, pet.age, pet.weight, pet.microchipNumber)

        command.imageSeqs?.let { newSeqs ->
            requireImages(userSeq, newSeqs)
            val oldSeqs = petRepository.findImageSeqs(pet.petSeq!!)
            if (oldSeqs != newSeqs) {
                petRepository.replaceImages(pet.petSeq!!, newSeqs)
                pet.markModified() // 사진만 바뀌어도 6.4 성공이면 mod_dttm을 갱신한다
                // 연결 해제는 이미 DB에 반영됐다. 다른 참조가 없을 때만 원본 삭제(Q383)
                (oldSeqs - newSeqs.toSet()).forEach(imageUseCase::deleteIfUnreferenced)
            }
        }
        petRepository.saveAndFlush(pet)
        return detail(pet)
    }

    override fun delete(userSeq: Long, petSeq: Long) {
        val pet = load(userSeq, petSeq)
        pet.delete()
        petRepository.saveAndFlush(pet)
        // 6.5: 소프트 삭제여도 사진 연결은 풀고, 다른 도메인이 참조 중이 아닐 때만 원본을 지운다
        val oldSeqs = petRepository.findImageSeqs(pet.petSeq!!)
        petRepository.replaceImages(pet.petSeq!!, emptyList())
        oldSeqs.forEach(imageUseCase::deleteIfUnreferenced)
    }

    private fun load(userSeq: Long, petSeq: Long): Pet =
        petRepository.findOwned(Math.toIntExact(userSeq), Math.toIntExact(petSeq))
            ?: throw BusinessException(PetErrorCode.USER_PET_NOT_FOUND)

    private fun detail(pet: Pet): PetDetail {
        val imageSeqs = petRepository.findImageSeqs(pet.petSeq!!)
        val summaries = imageUseCase.findSummaries(imageSeqs)
        val images = imageSeqs.mapIndexed { index, imageSeq -> PetImageRef(imageSeq, summaries[imageSeq]?.imageUrl, index + 1) }
        return PetDetail(pet, catalogUseCase.findClassifications(listOf(pet.combinationSeq))[pet.combinationSeq], images)
    }

    /** 사진 1~3장, 중복 없음, 본인 업로드만(Q407) */
    private fun requireImages(userSeq: Long, imageSeqs: List<Long>) {
        if (imageSeqs.size !in 1..MAX_IMAGES) throw BusinessException(PetErrorCode.IMAGE_INVALID_COUNT)
        if (imageSeqs.toSet().size != imageSeqs.size) throw invalid("imageSeqs", "같은 사진을 두 번 넣을 수 없습니다.")
        imageSeqs.forEach { imageUseCase.requireOwned(userSeq, it) }
    }

    private fun validateText(name: String, age: String?, weight: String?, microchipNumber: String?) {
        val errors = buildList {
            if (name.isBlank()) add(ErrorDetail("name", "필수 입력값입니다."))
            if (name.length > 100) add(ErrorDetail("name", "100자 이하로 입력해 주세요."))
            listOf("age" to age, "weight" to weight, "microchipNumber" to microchipNumber)
                .filter { (_, value) -> (value?.length ?: 0) > 30 }
                .forEach { (field, _) -> add(ErrorDetail(field, "30자 이하로 입력해 주세요.")) }
        }
        if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = errors)
    }

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)

    private companion object {
        const val MAX_IMAGES = 3
    }
}
