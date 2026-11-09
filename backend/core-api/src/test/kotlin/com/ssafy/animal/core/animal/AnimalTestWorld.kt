package com.ssafy.animal.core.animal

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.adoption.application.port.out.AdoptionApplicationRepository
import com.ssafy.animal.core.adoption.application.port.out.AdoptionQuestionRepository
import com.ssafy.animal.core.adoption.application.port.out.AdoptionReferenceReader
import com.ssafy.animal.core.adoption.application.service.AdoptionService
import com.ssafy.animal.core.adoption.application.service.AnimalApplicationsService
import com.ssafy.animal.core.adoption.domain.AdoptionApplication
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.adoption.domain.AdoptionFormQuestion
import com.ssafy.animal.core.adoption.domain.Applicant
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.adoption.domain.QuestionType
import com.ssafy.animal.core.animal.application.port.out.AnimalFilter
import com.ssafy.animal.core.animal.application.port.out.AnimalReferenceReader
import com.ssafy.animal.core.animal.application.port.out.RescueRow
import com.ssafy.animal.core.animal.application.port.out.ShelterAnimalRepository
import com.ssafy.animal.core.animal.application.port.out.ShelterRow
import com.ssafy.animal.core.animal.application.service.ShelterAnimalService
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalBasics
import com.ssafy.animal.core.animal.domain.AnimalReturn
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.Gender
import com.ssafy.animal.core.animal.domain.ShelterAnimal
import com.ssafy.animal.core.animal.domain.ShelterAnimalImage
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
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 보호동물·입양 서비스 테스트용 인메모리 세계. 두 서비스를 실제로 엮어 11.6 자동 거절·12.11 완료 같은 교차 흐름까지 본다.
 * DB 제약(부분 UNIQUE)과 잠금은 흉내만 낸다. 저장소 쿼리 자체는 Docker가 없어 통합 테스트를 생략한다.
 */
class AnimalTestWorld {

    val events = mutableListOf<Any>()
    val released = mutableListOf<Long>()

    // ===== 기준 데이터 =====
    /** 보호소 7(담당 SHELTER 계정 77), 보호소 8(88) */
    val shelterA = AuthUser(77, Role.SHELTER, 7)
    val shelterB = AuthUser(88, Role.SHELTER, 8)
    val shelters = mutableMapOf(
        7 to ShelterRow(7, "대구동물보호센터", "0531234567", "대구광역시 중구", "27110"),
        8 to ShelterRow(8, "구미보호소", "0541234567", "경북 구미시", "47190"),
    )
    val shelterMembers = mutableMapOf(7 to 77, 8 to 88)
    val classifications = mutableMapOf(
        88 to AnimalClassification(88, 1, SpeciesCode.DOG, "개", 21, "말티즈", 3, "흰색"),
        90 to AnimalClassification(90, 2, SpeciesCode.CAT, "고양이", 40, "코리안숏헤어", 5, "검정"),
    )
    val districts = mutableSetOf("27110", "47190")
    val rescues = mutableMapOf<Int, RescueRow>()
    /** imageSeq → 업로더 userSeq */
    val imageOwners = mutableMapOf(1701L to 77, 1702L to 77, 1703L to 77, 1704L to 77, 1801L to 88)
    val bookmarks = mutableSetOf<Pair<Int, Int>>()
    val users = mutableMapOf(
        42 to Applicant("김가람", LocalDate.of(1997, 5, 12), "01012345678"),
        43 to Applicant("이나래", LocalDate.of(1995, 1, 1), "01099998888"),
        44 to Applicant("박다온", LocalDate.of(1990, 3, 3), "01077776666"),
    )

    val questions = mutableListOf(
        question(1, QuestionType.SINGLE_CHOICE, true, options = listOf("아파트", "단독주택", "기타")),
        question(2, QuestionType.TEXT, true),
        question(3, QuestionType.MULTI_CHOICE, false, options = listOf("개", "고양이", "없음")),
        question(17, QuestionType.SINGLE_CHOICE, true, options = listOf("이수", "미이수")),
        question(20, QuestionType.DATE, true, condition = mapOf("depends_on" to 17, "operator" to "EQ", "value" to "이수", "required_when_matched" to true)),
    )

    fun question(
        seq: Int,
        type: QuestionType,
        required: Boolean,
        options: List<String>? = null,
        condition: Map<String, Any?>? = null,
    ) = AdoptionFormQuestion(seq, "문항 $seq", type, required, seq, options, condition)

    // ===== 보호동물 저장소 + 다른 도메인 읽기 =====
    val animals = mutableMapOf<Int, ShelterAnimal>()
    val images = mutableMapOf<Int, List<ShelterAnimalImage>>()
    val returns = mutableMapOf<Int, AnimalReturn>()
    private var animalSeq = 700
    private var returnSeq = 800L

    val animalRepository = FakeAnimalRepository()

    inner class FakeAnimalRepository : ShelterAnimalRepository, AnimalReferenceReader {
        override fun save(animal: ShelterAnimal): ShelterAnimal {
            if (animal.animalSeq == null) {
                ReflectionTestUtils.setField(animal, "animalSeq", ++animalSeq)
                ReflectionTestUtils.setField(animal, "regDttm", OffsetDateTime.now())
            }
            animals[animal.animalSeq!!] = animal
            return animal
        }
        override fun flush() = Unit
        override fun findActive(animalSeq: Int) = animals[animalSeq]?.takeUnless { it.isDeleted }
        override fun findActiveForUpdate(animalSeq: Int) = findActive(animalSeq)
        override fun findAllById(animalSeqs: Collection<Int>) = animalSeqs.mapNotNull { animals[it] }
        override fun search(filter: AnimalFilter, pageable: Pageable): Page<ShelterAnimal> {
            val all = filtered(filter).filter { filter.status == null || it.status == filter.status }.sortedByDescending { it.animalSeq }
            return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
        }
        override fun countByStatus(filter: AnimalFilter): Map<AnimalStatus, Long> = filtered(filter).groupingBy { it.status }.eachCount().mapValues { it.value.toLong() }
        private fun filtered(f: AnimalFilter) = animals.values.filter {
            !it.isDeleted && (f.shelterSeqs == null || it.shelterSeq in f.shelterSeqs!!) &&
                (f.combinationSeqs == null || it.combinationSeq in f.combinationSeqs!!) &&
                (f.gender == null || it.gender == f.gender || it.gender == Gender.UNKNOWN)
        }
        override fun findImages(animalSeq: Int) = images[animalSeq].orEmpty()
        override fun findRepresentativeImageSeqs(animalSeqs: Collection<Int>) =
            animalSeqs.mapNotNull { seq -> images[seq]?.firstOrNull()?.let { seq to it.imageSeq } }.toMap()
        override fun replaceImages(animalSeq: Int, imageSeqs: List<Long>) {
            images[animalSeq] = imageSeqs.mapIndexed { i, s -> ShelterAnimalImage(animalSeq, s, (i + 1).toShort()) }
        }
        override fun findReturn(animalSeq: Int) = returns[animalSeq]
        override fun saveReturn(animalReturn: AnimalReturn): AnimalReturn {
            ReflectionTestUtils.setField(animalReturn, "returnSeq", ++returnSeq)
            ReflectionTestUtils.setField(animalReturn, "regDttm", OffsetDateTime.now())
            returns[animalReturn.animalSeq] = animalReturn
            return animalReturn
        }

        override fun findRescue(rescueSeq: Int) = rescues[rescueSeq]
        override fun findShelters(shelterSeqs: Collection<Int>) = shelterSeqs.mapNotNull { shelters[it] }.associateBy { it.shelterSeq }
        override fun findShelterSeqsInDistrict(districtCode: String) = shelters.values.filter { it.districtCode == districtCode }.map { it.shelterSeq }
        override fun findCombinationSeqsOfSpecies(speciesSeq: Int) = classifications.values.filter { it.speciesSeq == speciesSeq }.map { it.combinationSeq }
        override fun findImageKeys(imageSeqs: Collection<Long>) = imageSeqs.map { "images/2026/11/$it.jpg" }
        override fun isBookmarked(userSeq: Int, animalSeq: Int) = (userSeq to animalSeq) in bookmarks
        override fun findActiveUserSeqsByPhone(phone: String) = users.filterValues { it.phone == phone }.keys.toList()
    }

    // ===== 입양 신청 저장소 + 문항 + 다른 도메인 읽기 =====
    val applications = mutableMapOf<Long, AdoptionApplication>()
    private var applicationSeq = 900L

    val applicationRepository = FakeApplicationRepository()

    inner class FakeApplicationRepository : AdoptionApplicationRepository, AdoptionQuestionRepository, AdoptionReferenceReader {
        override fun saveAndFlush(application: AdoptionApplication): AdoptionApplication {
            val duplicate = application.status in ApplicationStatus.ACTIVE && applications.values.any {
                it !== application && it.userSeq == application.userSeq && it.animalSeq == application.animalSeq && it.status in ApplicationStatus.ACTIVE
            }
            if (duplicate) throw BusinessException(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS)
            if (application.applicationSeq == null) {
                ReflectionTestUtils.setField(application, "applicationSeq", ++applicationSeq)
                ReflectionTestUtils.setField(application, "regDttm", OffsetDateTime.now())
            }
            applications[application.applicationSeq!!] = application
            return application
        }
        override fun find(applicationSeq: Long) = applications[applicationSeq]
        override fun findForUpdate(applicationSeq: Long) = applications[applicationSeq]
        override fun findAnimalSeq(applicationSeq: Long) = applications[applicationSeq]?.animalSeq
        override fun findActiveForUpdate(userSeq: Int, animalSeq: Int) =
            applications.values.firstOrNull { it.userSeq == userSeq && it.animalSeq == animalSeq && it.status in ApplicationStatus.ACTIVE }
        override fun findActiveByAnimalForUpdate(animalSeq: Int) = applications.values.filter { it.animalSeq == animalSeq && it.status in ApplicationStatus.ACTIVE }
        override fun existsActiveByAnimal(animalSeq: Int) = findActiveByAnimalForUpdate(animalSeq).isNotEmpty()
        override fun delete(application: AdoptionApplication) {
            applications.remove(application.applicationSeq)
        }
        override fun findMine(userSeq: Int, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<AdoptionApplication> {
            val all = applications.values.filter { it.userSeq == userSeq && (status == null || it.status == status) && (animalSeq == null || it.animalSeq == animalSeq) }
            return PageImpl(all, pageable, all.size.toLong())
        }
        override fun findReceived(shelterSeq: Int, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<AdoptionApplication> {
            val all = received(shelterSeq, animalSeq).filter { status == null || it.status == status }
            return PageImpl(all, pageable, all.size.toLong())
        }
        override fun countReceived(shelterSeq: Int?, animalSeq: Int?) = received(shelterSeq, animalSeq).groupingBy { it.status }.eachCount().mapValues { it.value.toLong() }
        private fun received(shelterSeq: Int?, animalSeq: Int?) = applications.values.filter {
            it.isVisibleToShelter && (shelterSeq == null || it.shelterSeq == shelterSeq) && (animalSeq == null || it.animalSeq == animalSeq)
        }
        override fun cancelActiveOfUser(userSeq: Int, now: OffsetDateTime): Int {
            val active = applications.values.filter { it.userSeq == userSeq && it.status in ApplicationStatus.ACTIVE }
            active.forEach { it.cancel(userSeq) }
            return active.size
        }
        override fun findAll() = questions.sortedBy { it.displayOrder }
        override fun findApplicant(userSeq: Int) = users[userSeq]
        override fun findShelterUserSeqs(shelterSeq: Int) = listOfNotNull(shelterMembers[shelterSeq])
    }

    val catalog = object : CatalogUseCase {
        override fun requireClassification(combinationSeq: Int) =
            classifications[combinationSeq] ?: throw BusinessException(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND)
        override fun findClassifications(combinationSeqs: Collection<Int>) = combinationSeqs.mapNotNull { classifications[it] }.associateBy { it.combinationSeq }
        override fun requireDistrict(districtCode: String) {
            if (districtCode !in districts) throw BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE)
        }
    }

    val imageUseCase = object : ImageUseCase {
        override fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary {
            if (imageSeq !in imageOwners) throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
            if (imageOwners[imageSeq]?.toLong() != userSeq) throw BusinessException(ImageErrorCode.IMAGE_NOT_OWNED)
            return ImageSummary(imageSeq, url(imageSeq))
        }
        override fun findSummary(imageSeq: Long) = ImageSummary(imageSeq, url(imageSeq)).takeIf { imageSeq in imageOwners }
        override fun deleteIfUnreferenced(imageSeq: Long) {
            released += imageSeq
        }
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
        private fun url(imageSeq: Long) = "https://cdn/$imageSeq.jpg"
    }

    val eventPublisher = object : EventPublisher {
        override fun publish(event: Any) {
            events += event
        }
    }

    val animalApplications = AnimalApplicationsService(applicationRepository, eventPublisher)
    val animalService = ShelterAnimalService(animalRepository, animalRepository, animalApplications, catalog, imageUseCase, eventPublisher)
    val adoptionService = AdoptionService(applicationRepository, applicationRepository, applicationRepository, animalService, animalApplications, eventPublisher)

    /** 보호소 7의 PROTECTING 동물을 바로 만든다 */
    fun animal(
        shelterSeq: Int = 7,
        adoptionStatus: AdoptionStatus = AdoptionStatus.AVAILABLE,
        combinationSeq: Int = 88,
        gender: Gender = Gender.UNKNOWN,
        imageSeqs: List<Long> = listOf(1701),
    ): ShelterAnimal {
        val basics = AnimalBasics(combinationSeq, gender, "1살 추정", null, null, null, "3kg", "사람을 잘 따름", "기초 검진 완료", null)
        val animal = animalRepository.save(
            ShelterAnimal(shelterSeq, null, basics, LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 8), adoptionStatus, shelterMembers.getValue(shelterSeq)),
        )
        animalRepository.replaceImages(animal.animalSeq!!, imageSeqs)
        return animal
    }

    /** 바로 SUBMITTED 상태의 신청을 만든다(12.6 경로) */
    fun submitted(userSeq: Long, animalSeq: Int) =
        adoptionService.submit(userSeq, animalSeq, completeForm()).applicationSeq

    fun completeForm() = com.ssafy.animal.core.adoption.application.port.`in`.FormInput(
        answers = mapOf("1" to "아파트", "2" to "가족 모두 동의", "17" to "미이수"),
        careAddress = "경상북도 구미시 구미로 123",
        careAddressDetail = "7층",
        email = "a@b.com",
        visitHopeDate = LocalDate.now().plusDays(3),
    )
}
