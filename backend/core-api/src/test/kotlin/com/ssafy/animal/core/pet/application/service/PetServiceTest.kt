package com.ssafy.animal.core.pet.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.pet.application.port.`in`.CreatePetCommand
import com.ssafy.animal.core.pet.application.port.`in`.UpdatePetCommand
import com.ssafy.animal.core.pet.application.port.out.PetRepository
import com.ssafy.animal.core.pet.domain.Pet
import com.ssafy.animal.core.pet.domain.PetErrorCode
import com.ssafy.animal.core.pet.domain.PetGender
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetServiceTest {

    private val pets = mutableMapOf<Int, Pet>()
    private val links = mutableMapOf<Int, List<Long>>()

    private val petRepository = object : PetRepository {
        override fun saveAndFlush(pet: Pet): Pet {
            if (pet.petSeq == null) ReflectionTestUtils.setField(pet, "petSeq", pets.size + 1)
            pets[pet.petSeq!!] = pet
            return pet
        }
        override fun findOwned(userSeq: Int, petSeq: Int) = pets[petSeq]?.takeIf { it.userSeq == userSeq && !it.isDeleted }
        override fun findPage(userSeq: Int, pageable: Pageable) =
            PageImpl(pets.values.filter { it.userSeq == userSeq && !it.isDeleted }, pageable, 1)
        override fun findImageSeqs(petSeq: Int) = links[petSeq].orEmpty()
        override fun findRepresentativeImageSeqs(petSeqs: Collection<Int>) =
            petSeqs.mapNotNull { seq -> links[seq]?.firstOrNull()?.let { seq to it } }.toMap()
        override fun replaceImages(petSeq: Int, imageSeqs: List<Long>) {
            links[petSeq] = imageSeqs
        }
    }

    private val catalogUseCase = object : CatalogUseCase {
        override fun requireClassification(combinationSeq: Int) =
            findClassifications(listOf(combinationSeq))[combinationSeq]
                ?: throw BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND)
        override fun findClassifications(combinationSeqs: Collection<Int>) = combinationSeqs.filter { it == 88 }
            .associateWith { AnimalClassification(it, 1, SpeciesCode.DOG, "개", 3, "말티즈", 2, "흰색") }
        override fun requireDistrict(districtCode: String) = Unit
    }

    /** imageSeq → 업로더. 1000번대는 회원 1, 2000번대는 회원 2 */
    private val released = mutableListOf<Long>()
    private val imageUseCase = object : ImageUseCase {
        override fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary {
            val owner = when (imageSeq / 1000) { 1L -> 1L; 2L -> 2L; else -> throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND) }
            if (owner != userSeq) throw BusinessException(ImageErrorCode.IMAGE_NOT_OWNED)
            return ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        }
        override fun findSummary(imageSeq: Long) = ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        override fun deleteIfUnreferenced(imageSeq: Long) {
            released += imageSeq
        }
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
    }

    private val service = PetService(petRepository, catalogUseCase, imageUseCase)

    private fun create(imageSeqs: List<Long> = listOf(1001, 1002), combinationSeq: Int = 88, name: String = "초코") =
        service.create(1, CreatePetCommand(name, combinationSeq, PetGender.MALE, age = "3살", imageSeqs = imageSeqs))

    private fun assertFails(code: ErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `6_3은 사진을 순서대로 연결하고 첫 장을 대표로 돌려준다`() {
        val created = create(listOf(1002, 1001, 1003))

        assertEquals(3, created.imageCount)
        assertEquals(1002, created.representativeImageSeq)
        assertEquals(listOf(1002L, 1001L, 1003L), links[created.petSeq])
        assertEquals(1, pets[created.petSeq]!!.userSeq)
    }

    @Test
    fun `6_3 검증 - 사진 수, 중복, 타인 사진, 없는 사진, 없는 조합, 빈 이름`() {
        assertFails(PetErrorCode.IMAGE_INVALID_COUNT) { create(emptyList()) }
        assertFails(PetErrorCode.IMAGE_INVALID_COUNT) { create(listOf(1001, 1002, 1003, 1004)) }
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { create(listOf(1001, 1001)) }
        assertFails(ImageErrorCode.IMAGE_NOT_OWNED) { create(listOf(2001)) }
        assertFails(ImageErrorCode.IMAGE_NOT_FOUND) { create(listOf(9001)) }
        assertFails(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND) { create(combinationSeq = 99) }
        assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { create(name = "  ") }
        assertTrue(pets.isEmpty())
    }

    @Test
    fun `6_1·6_2는 분류와 대표 사진을 붙이고 타인·삭제는 404`() {
        val seq = create().petSeq

        val summary = service.getPage(1, PageRequest.of(0, 20)).content.single()
        assertEquals("말티즈", summary.classification!!.breedName)
        assertEquals("https://cdn/1001.jpg", summary.representativeImageUrl)
        assertEquals(listOf(1, 2), service.get(1, seq.toLong()).images.map { it.displayOrder })
        assertFails(PetErrorCode.USER_PET_NOT_FOUND) { service.get(2, seq.toLong()) }
    }

    @Test
    fun `6_4는 보낸 필드만 바꾸고 명시적 null은 지우며 빠진 사진만 정리한다`() {
        val seq = create().petSeq.toLong()

        val detail = service.update(1, seq, UpdatePetCommand(name = "콩이", age = Optional.empty(), imageSeqs = listOf(1003, 1001)))

        assertEquals("콩이", detail.pet.name)
        assertNull(detail.pet.age)
        assertEquals(PetGender.MALE, detail.pet.gender)
        assertEquals(listOf(1003L, 1001L), links[seq.toInt()])
        assertEquals(listOf(1002L), released)
    }

    @Test
    fun `6_4 타인 반려동물은 404, 잘못된 조합은 404 CATALOG`() {
        val seq = create().petSeq.toLong()

        assertFails(PetErrorCode.USER_PET_NOT_FOUND) { service.update(2, seq, UpdatePetCommand(name = "x")) }
        assertFails(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND) { service.update(1, seq, UpdatePetCommand(combinationSeq = 99)) }
    }

    @Test
    fun `6_5는 소프트 삭제하고 사진 연결을 풀어 정리하며 이후 404`() {
        val seq = create().petSeq

        service.delete(1, seq.toLong())

        assertTrue(pets[seq]!!.isDeleted)
        assertEquals(emptyList(), links[seq])
        assertEquals(listOf(1001L, 1002L), released)
        assertFails(PetErrorCode.USER_PET_NOT_FOUND) { service.delete(1, seq.toLong()) }
    }
}
