package com.ssafy.animal.core.animal.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.animal.application.port.`in`.AdoptionInfo
import com.ssafy.animal.core.animal.application.port.`in`.AdoptionTarget
import com.ssafy.animal.core.animal.application.port.`in`.AnimalAdoptionUseCase
import com.ssafy.animal.core.animal.application.port.`in`.AnimalCard
import com.ssafy.animal.core.animal.application.port.`in`.AnimalDetail
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSummary
import com.ssafy.animal.core.animal.application.port.`in`.ClassificationBrief
import com.ssafy.animal.core.animal.application.port.`in`.ClassificationFull
import com.ssafy.animal.core.animal.application.port.`in`.ImageRef
import com.ssafy.animal.core.animal.application.port.`in`.ManageAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.ManagedAnimal
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalPage
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.RegisterAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.RegisteredAnimal
import com.ssafy.animal.core.animal.application.port.`in`.RescueInfo
import com.ssafy.animal.core.animal.application.port.`in`.ReturnInfo
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalDetail
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalUseCase
import com.ssafy.animal.core.animal.application.port.`in`.ShelterBrief
import com.ssafy.animal.core.animal.application.port.`in`.ShelterContact
import com.ssafy.animal.core.animal.application.port.`in`.UpdateAnimalCommand
import com.ssafy.animal.core.animal.application.port.out.AnimalApplicationsPort
import com.ssafy.animal.core.animal.application.port.out.AnimalFilter
import com.ssafy.animal.core.animal.application.port.out.AnimalReferenceReader
import com.ssafy.animal.core.animal.application.port.out.ShelterAnimalRepository
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalBasics
import com.ssafy.animal.core.animal.domain.AnimalErrorCode
import com.ssafy.animal.core.animal.domain.AnimalForeignErrorCode
import com.ssafy.animal.core.animal.domain.AnimalReturn
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.EndReason
import com.ssafy.animal.core.animal.domain.ProtectionStatus
import com.ssafy.animal.core.animal.domain.ShelterAnimal
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Optional

@Service
class ShelterAnimalService(
    private val animalRepository: ShelterAnimalRepository,
    private val referenceReader: AnimalReferenceReader,
    private val applications: AnimalApplicationsPort,
    private val catalogUseCase: CatalogUseCase,
    private val imageUseCase: ImageUseCase,
    private val eventPublisher: EventPublisher,
) : ShelterAnimalUseCase, AnimalAdoptionUseCase {

    // ===== 11.1 =====
    @Transactional(readOnly = true)
    override fun search(condition: AnimalSearchCondition, pageable: Pageable): Page<AnimalCard> {
        val (regFrom, regTo) = condition.regFrom to condition.regTo
        if (regFrom != null && regTo != null && regFrom.isAfter(regTo)) throw invalid("regFrom", "시작일이 종료일보다 늦습니다.")
        val shelterSeqs = condition.districtCode?.let {
            catalogUseCase.requireDistrict(it)
            referenceReader.findShelterSeqsInDistrict(it)
        }
        val combinationSeqs = condition.speciesSeq?.let(referenceReader::findCombinationSeqsOfSpecies)
        if (shelterSeqs?.isEmpty() == true || combinationSeqs?.isEmpty() == true) return Page.empty(pageable)

        val filter = AnimalFilter(
            shelterSeqs = shelterSeqs,
            status = condition.status,
            regFrom = regFrom?.atStartOfDay(KST)?.toOffsetDateTime(),
            regBefore = regTo?.plusDays(1)?.atStartOfDay(KST)?.toOffsetDateTime(),
            combinationSeqs = combinationSeqs,
            gender = condition.gender,
            isNeutered = condition.isNeutered,
            keyword = condition.keyword?.trim()?.takeIf { it.isNotEmpty() },
        )
        val page = animalRepository.search(filter, pageable)
        val cards = toCards(page.content)
        return page.map { animal -> cards.getValue(animal.animalSeq!!) }
    }

    // ===== 11.3 =====
    @Transactional(readOnly = true)
    override fun searchMine(shelter: AuthUser, condition: MyAnimalSearchCondition, pageable: Pageable): MyAnimalPage {
        val combinationSeqs = condition.speciesSeq?.let(referenceReader::findCombinationSeqsOfSpecies)
        val filter = AnimalFilter(
            shelterSeqs = listOf(shelter.shelter),
            regFrom = condition.period?.let { OffsetDateTime.now().minusDays(it.days) },
            combinationSeqs = combinationSeqs,
            gender = condition.gender,
            isNeutered = condition.isNeutered,
        )
        val (page, counts) = if (combinationSeqs?.isEmpty() == true) {
            Page.empty<ShelterAnimal>(pageable) to emptyMap()
        } else {
            animalRepository.search(filter.copy(status = condition.status), pageable) to animalRepository.countByStatus(filter)
        }
        val cards = toCards(page.content)
        val statusCounts = linkedMapOf("total" to counts.values.sum())
        AnimalStatus.entries.forEach { statusCounts[it.name] = counts[it] ?: 0L }
        return MyAnimalPage(statusCounts, PageResponse.of(page.map { cards.getValue(it.animalSeq!!) }))
    }

    // ===== 11.2 =====
    @Transactional(readOnly = true)
    override fun getForUser(userSeq: Long, animalSeq: Int): AnimalDetail {
        val animal = animalRepository.findActive(animalSeq) ?: throw notFound()
        val seq = animal.animalSeq!!
        val images = images(seq)
        val shelter = referenceReader.findShelters(listOf(animal.shelterSeq))[animal.shelterSeq]
        return with(animal) {
            AnimalDetail(
                animalSeq = seq,
                classification = classification(combinationSeq)?.let(ClassificationFull::of),
                gender = gender, age = age, isNeutered = isNeutered, weight = weight,
                protectionStatus = protectionStatus, adoptionStatus = adoptionStatus,
                noticeStartDate = noticeStartDate, noticeEndDate = noticeEndDate,
                shelter = shelter?.let { ShelterContact(it.shelterSeq, it.shelterName, it.phone, it.address) },
                representativeImageUrl = images.firstOrNull()?.imageUrl,
                regDttm = regDttm, modDttm = modDttm, rescueSeq = rescueSeq,
                microchipNumber = microchipNumber, registrationNumber = registrationNumber,
                description = description, healthCheck = healthCheck, vaccinationStatus = vaccinationStatus,
                endReason = endReason, protectionEndedDttm = protectionEndedDttm,
                rescueInfo = rescueInfo(rescueSeq),
                images = images,
                isBookmarked = referenceReader.isBookmarked(Math.toIntExact(userSeq), seq),
            )
        }
    }

    // ===== 11.10 =====
    @Transactional(readOnly = true)
    override fun getForShelter(shelter: AuthUser, animalSeq: Int): ShelterAnimalDetail =
        shelterDetail(findOwned(shelter, animalSeq, lock = false))

    // ===== 11.4 =====
    @Transactional
    override fun register(shelter: AuthUser, command: RegisterAnimalCommand): RegisteredAnimal {
        validateBasics(command.basics)
        validateImageCount(command.imageSeqs)
        val adoptionStatus = command.adoptionStatus ?: AdoptionStatus.UNAVAILABLE
        if (adoptionStatus == AdoptionStatus.ADOPTED) throw invalid("adoptionStatus", "AVAILABLE 또는 UNAVAILABLE만 보낼 수 있습니다.")
        val start = command.noticeStartDate ?: LocalDate.now(KST)
        val end = command.noticeEndDate ?: start.plusDays(DEFAULT_NOTICE_DAYS)
        if (start.isAfter(end)) throw invalid("noticeEndDate", "공고 종료일은 시작일 이후여야 합니다.")
        command.rescueSeq?.let(::requireDoneRescue)
        val classification = catalogUseCase.requireClassification(command.basics.combinationSeq)
        command.imageSeqs.forEach { imageUseCase.requireOwned(shelter.userSeq, it) }

        val animal = animalRepository.save(
            ShelterAnimal(shelter.shelter, command.rescueSeq, command.basics, start, end, adoptionStatus, shelter.actor),
        )
        val seq = animal.animalSeq!!
        animalRepository.replaceImages(seq, command.imageSeqs)
        publishMatch(MatchEventType.SHELTER_ANIMAL_REGISTERED, seq, command.imageSeqs, classification.speciesCode.name)
        return RegisteredAnimal(seq, animal.protectionStatus, animal.adoptionStatus, animal.noticeStartDate, animal.noticeEndDate)
    }

    // ===== 11.5 =====
    @Transactional
    override fun update(shelter: AuthUser, animalSeq: Int, command: UpdateAnimalCommand): ShelterAnimalDetail {
        val animal = findOwned(shelter, animalSeq, lock = false)
        val combinationSeq = command.combinationSeq ?: animal.combinationSeq
        val classification = if (command.combinationSeq != null) catalogUseCase.requireClassification(combinationSeq) else null
        val basics = AnimalBasics(
            combinationSeq = combinationSeq,
            gender = command.gender ?: animal.gender,
            age = command.age.orKeep(animal.age),
            isNeutered = command.isNeutered.orKeep(animal.isNeutered),
            microchipNumber = command.microchipNumber.orKeep(animal.microchipNumber),
            registrationNumber = command.registrationNumber.orKeep(animal.registrationNumber),
            weight = command.weight.orKeep(animal.weight),
            description = command.description.orKeep(animal.description),
            healthCheck = command.healthCheck.orKeep(animal.healthCheck),
            vaccinationStatus = command.vaccinationStatus.orKeep(animal.vaccinationStatus),
        )
        validateBasics(basics)
        animal.updateBasics(basics, shelter.actor)
        command.imageSeqs?.let { replaceImages(shelter, animal, it, classification?.speciesCode?.name) }
        animalRepository.flush()
        return shelterDetail(animal)
    }

    private fun replaceImages(shelter: AuthUser, animal: ShelterAnimal, imageSeqs: List<Long>, speciesCode: String?) {
        validateImageCount(imageSeqs)
        val seq = animal.animalSeq!!
        val current = animalRepository.findImages(seq).map { it.imageSeq }
        if (current == imageSeqs) return
        (imageSeqs - current.toSet()).forEach { imageUseCase.requireOwned(shelter.userSeq, it) }
        animalRepository.replaceImages(seq, imageSeqs)
        // flush 뒤라 이 동물의 참조는 이미 빠져 있다(Q383)
        (current - imageSeqs.toSet()).forEach(imageUseCase::deleteIfUnreferenced)
        // 사진 diff가 있고 PROTECTING이면 재매칭(11.5). 순서만 바뀐 것은 diff가 아니다
        if (animal.protectionStatus == ProtectionStatus.PROTECTING && current.toSet() != imageSeqs.toSet()) {
            val code = speciesCode ?: classification(animal.combinationSeq)?.speciesCode?.name ?: return
            publishMatch(MatchEventType.IMAGES_CHANGED, seq, imageSeqs, code)
        }
    }

    // ===== 11.6 =====
    @Transactional
    override fun manage(shelter: AuthUser, animalSeq: Int, command: ManageAnimalCommand): ManagedAnimal {
        validateManage(command)
        val animal = findOwned(shelter, animalSeq, lock = true)
        val start = command.noticeStartDate ?: animal.noticeStartDate
        val end = command.noticeEndDate ?: animal.noticeEndDate
        if (start.isAfter(end)) throw invalid("noticeEndDate", "공고 종료일은 시작일 이후여야 합니다.")
        if (command.noticeStartDate != null || command.noticeEndDate != null) animal.changeNotice(start, end, shelter.actor)

        val now = OffsetDateTime.now()
        val actor = shelter.actor
        val rejectReason = when (command.status) {
            null -> null
            ProtectionStatus.PROTECTING -> {
                // 다른 상태에서 복귀할 때 생략하면 UNAVAILABLE, 이미 PROTECTING이면 현재 값 유지
                val current = animal.adoptionStatus.takeIf { animal.protectionStatus == ProtectionStatus.PROTECTING }
                animal.protect(command.adoptionStatus ?: current ?: AdoptionStatus.UNAVAILABLE, actor)
                null
            }
            ProtectionStatus.ENDED -> {
                animal.end(command.endReason ?: EndReason.OTHER, now, actor)
                "보호가 종료되어 입양 신청이 자동으로 거절되었습니다."
            }
            ProtectionStatus.RETURNED -> {
                val returned = saveReturn(animal.animalSeq!!, command.recipientName!!.trim(), command.recipientPhone!!.trim(), actor)
                animal.markReturned(returned.returnedDttm!!, actor)
                "보호동물이 보호자에게 반환되어 입양 신청이 자동으로 거절되었습니다."
            }
            ProtectionStatus.ADOPTED -> {
                animal.markAdoptedExternally(command.reason!!.trim(), command.memo, now, actor)
                "보호동물의 입양이 완료되어 입양 신청이 자동으로 거절되었습니다."
            }
        }
        val rejected = rejectReason?.let { applications.rejectActive(toTarget(animal), it) } ?: 0
        animalRepository.flush()
        return with(animal) {
            ManagedAnimal(
                animalSeq = animalSeq,
                protectionStatus = protectionStatus,
                adoptionStatus = this.adoptionStatus,
                noticeStartDate = noticeStartDate,
                noticeEndDate = noticeEndDate,
                endReason = endReason,
                protectionEndedDttm = protectionEndedDttm,
                returnInfo = if (protectionStatus == ProtectionStatus.RETURNED) returnInfo(animalSeq) else null,
                adoptionInfo = if (protectionStatus == ProtectionStatus.ADOPTED) adoptionInfo(this) else null,
                autoRejectedApplications = rejected,
            )
        }
    }

    /** 반환 행은 동물당 1건: 없으면 INSERT, 있으면 덮어쓰고 mod_dttm 갱신(Q479). 같은 번호의 활성 USER가 정확히 1명이면 연결 */
    private fun saveReturn(animalSeq: Int, name: String, phone: String, actor: Int): AnimalReturn {
        val linkedUser = referenceReader.findActiveUserSeqsByPhone(phone).singleOrNull()
        val existing = animalRepository.findReturn(animalSeq)
            ?: return animalRepository.saveReturn(AnimalReturn(animalSeq, name, phone, linkedUser, actor))
        existing.overwrite(name, phone, linkedUser, actor)
        animalRepository.flush()
        return existing
    }

    /** 입력 자체 검증(400). 상태와 맞지 않는 추가 필드도 거른다 */
    private fun validateManage(c: ManageAnimalCommand) {
        val extras = buildMap {
            if (c.adoptionStatus != null && c.status != ProtectionStatus.PROTECTING) put("adoptionStatus", "status=PROTECTING일 때만 보낼 수 있습니다.")
            if (c.endReason != null && c.status != ProtectionStatus.ENDED) put("endReason", "status=ENDED일 때만 보낼 수 있습니다.")
            if ((c.recipientName != null || c.recipientPhone != null) && c.status != ProtectionStatus.RETURNED) {
                put("recipientName", "status=RETURNED일 때만 보낼 수 있습니다.")
            }
            if ((c.reason != null || c.memo != null) && c.status != ProtectionStatus.ADOPTED) put("reason", "status=ADOPTED일 때만 보낼 수 있습니다.")
            if (c.adoptionStatus == AdoptionStatus.ADOPTED) put("adoptionStatus", "AVAILABLE 또는 UNAVAILABLE만 보낼 수 있습니다.")
            if (c.endReason == EndReason.RETURNED || c.endReason == EndReason.ADOPTED) put("endReason", "NATURAL_DEATH, EUTHANIZED, OTHER만 보낼 수 있습니다.")
            if (c.status == ProtectionStatus.RETURNED) {
                requireText(c.recipientName, "recipientName", 100)
                requireText(c.recipientPhone, "recipientPhone", 30)
            }
            if (c.status == ProtectionStatus.ADOPTED) requireText(c.reason, "reason", 200)
        }
        if (extras.isNotEmpty()) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = extras.map { (field, reason) -> ErrorDetail(field, reason) })
        }
    }

    private fun MutableMap<String, String>.requireText(value: String?, field: String, max: Int) {
        when {
            value.isNullOrBlank() -> put(field, "필수 입력값입니다.")
            value.trim().length > max -> put(field, "${max}자 이하로 입력해 주세요.")
        }
    }

    // ===== 11.9 =====
    @Transactional
    override fun delete(shelter: AuthUser, animalSeq: Int) {
        val animal = findOwned(shelter, animalSeq, lock = true)
        if (applications.hasActive(animalSeq)) throw BusinessException(AnimalErrorCode.ANIMAL_HAS_ACTIVE_APPLICATIONS)
        animal.delete(shelter.actor)
    }

    // ===== adoption 패키지용 =====
    @Transactional
    override fun lockForAdoption(animalSeq: Int): AdoptionTarget =
        toTarget(animalRepository.findActiveForUpdate(animalSeq) ?: throw notFound())

    @Transactional(readOnly = true)
    override fun requireOwnedBy(shelterSeq: Int, animalSeq: Int) {
        animalRepository.findActive(animalSeq)?.takeIf { it.shelterSeq == shelterSeq } ?: throw notFound()
    }

    @Transactional
    override fun completeAdoption(animalSeq: Int, applicationSeq: Long, actorSeq: Int) {
        val animal = animalRepository.findActiveForUpdate(animalSeq) ?: throw notFound()
        animal.completeAdoption(applicationSeq, OffsetDateTime.now(), actorSeq)
        animalRepository.flush()
    }

    @Transactional(readOnly = true)
    override fun findSummaries(animalSeqs: Collection<Int>): Map<Int, AnimalSummary> {
        if (animalSeqs.isEmpty()) return emptyMap()
        val animals = animalRepository.findAllById(animalSeqs.toSet())
        val classifications = catalogUseCase.findClassifications(animals.map { it.combinationSeq }.toSet())
        val shelters = referenceReader.findShelters(animals.map { it.shelterSeq }.toSet())
        val imageUrls = representativeImageUrls(animals)
        return animals.associate { a ->
            val c = classifications[a.combinationSeq]
            val s = shelters[a.shelterSeq]
            a.animalSeq!! to AnimalSummary(
                a.animalSeq!!, c?.speciesName, c?.breedName, c?.colorName, a.gender, a.age, a.weight, a.isNeutered,
                a.microchipNumber, a.registrationNumber, imageUrls[a.animalSeq!!], a.shelterSeq, s?.shelterName, s?.address,
            )
        }
    }

    // ===== 공통 =====

    private fun findOwned(shelter: AuthUser, animalSeq: Int, lock: Boolean): ShelterAnimal {
        val animal = if (lock) animalRepository.findActiveForUpdate(animalSeq) else animalRepository.findActive(animalSeq)
        // 다른 보호소 동물도 존재를 감춘다(Q434)
        return animal?.takeIf { it.shelterSeq == shelter.shelter } ?: throw notFound()
    }

    private fun toTarget(animal: ShelterAnimal) = AdoptionTarget(
        animalSeq = animal.animalSeq!!,
        shelterSeq = animal.shelterSeq,
        shelterName = referenceReader.findShelters(listOf(animal.shelterSeq))[animal.shelterSeq]?.shelterName.orEmpty(),
        breedName = classification(animal.combinationSeq)?.breedName.orEmpty(),
        isAdoptable = animal.isAdoptable,
        completedApplicationSeq = animal.completedApplicationSeq,
    )

    private fun shelterDetail(animal: ShelterAnimal): ShelterAnimalDetail {
        val seq = animal.animalSeq!!
        return with(animal) {
            ShelterAnimalDetail(
                animalSeq = seq,
                classification = classification(combinationSeq)?.let(ClassificationFull::of),
                gender = gender, age = age, isNeutered = isNeutered, weight = weight,
                microchipNumber = microchipNumber, registrationNumber = registrationNumber,
                description = description, healthCheck = healthCheck, vaccinationStatus = vaccinationStatus,
                protectionStatus = protectionStatus, adoptionStatus = adoptionStatus,
                noticeStartDate = noticeStartDate, noticeEndDate = noticeEndDate,
                endReason = endReason, protectionEndedDttm = protectionEndedDttm,
                rescueSeq = rescueSeq, rescueInfo = rescueInfo(rescueSeq),
                images = images(seq),
                // 반환 행은 현재 상태와 무관하게 보인다(복귀 이력 포함)
                returnInfo = returnInfo(seq),
                adoptionInfo = if (protectionStatus == ProtectionStatus.ADOPTED || adoptionReason != null || completedApplicationSeq != null) adoptionInfo(this) else null,
                applicationCounts = applications.countReceived(seq),
                regDttm = regDttm, modDttm = modDttm,
            )
        }
    }

    private fun returnInfo(animalSeq: Int) = animalRepository.findReturn(animalSeq)?.let {
        ReturnInfo(it.returnSeq!!, it.recipientName, it.recipientPhone, it.returnedUserSeq, it.returnedDttm)
    }

    private fun adoptionInfo(animal: ShelterAnimal) = AdoptionInfo(
        completedApplicationSeq = animal.completedApplicationSeq,
        applicantName = animal.completedApplicationSeq?.let(applications::findApplicantName),
        reason = animal.adoptionReason,
        memo = animal.adoptionMemo,
        completedDttm = animal.protectionEndedDttm,
    )

    private fun requireDoneRescue(rescueSeq: Int) {
        val rescue = referenceReader.findRescue(rescueSeq)?.takeUnless { it.isDeleted }
            ?: throw BusinessException(AnimalForeignErrorCode.RESCUE_NOT_FOUND)
        if (rescue.status != "DONE") throw BusinessException(AnimalForeignErrorCode.RESCUE_NOT_DONE)
    }

    /** 구조 정보는 복사하지 않고 매번 읽는다(Q461). 연결이 없거나 구조가 삭제되면 5개 모두 null */
    private fun rescueInfo(rescueSeq: Int?): RescueInfo {
        val rescue = rescueSeq?.let(referenceReader::findRescue)?.takeUnless { it.isDeleted } ?: return RescueInfo.EMPTY
        return RescueInfo(rescue.rescuedDttm, rescue.rescueReasonCode, rescue.rescueAddress, rescue.rescueAddressDetail, rescue.rescueMemo)
    }

    private fun images(animalSeq: Int) = animalRepository.findImages(animalSeq).map {
        ImageRef(it.imageSeq, imageUseCase.findSummary(it.imageSeq)?.imageUrl, it.displayOrder.toInt())
    }

    // ponytail: 대표 사진 URL을 동물마다 findSummary로 읽는다(페이지당 최대 100회). 느려지면 ImageUseCase에 일괄 조회를 요청
    private fun representativeImageUrls(animals: List<ShelterAnimal>): Map<Int, String?> =
        animalRepository.findRepresentativeImageSeqs(animals.map { it.animalSeq!! })
            .mapValues { (_, imageSeq) -> imageUseCase.findSummary(imageSeq)?.imageUrl }

    private fun toCards(animals: List<ShelterAnimal>): Map<Int, AnimalCard> {
        if (animals.isEmpty()) return emptyMap()
        val classifications = catalogUseCase.findClassifications(animals.map { it.combinationSeq }.toSet())
        val shelters = referenceReader.findShelters(animals.map { it.shelterSeq }.toSet())
        val imageUrls = representativeImageUrls(animals)
        return animals.associate { a ->
            a.animalSeq!! to AnimalCard(
                animalSeq = a.animalSeq!!,
                classification = classifications[a.combinationSeq]?.let(ClassificationBrief::of),
                gender = a.gender, age = a.age, isNeutered = a.isNeutered, weight = a.weight,
                protectionStatus = a.protectionStatus, adoptionStatus = a.adoptionStatus,
                noticeStartDate = a.noticeStartDate, noticeEndDate = a.noticeEndDate,
                shelter = shelters[a.shelterSeq]?.let { ShelterBrief(it.shelterSeq, it.shelterName, it.districtCode) },
                representativeImageUrl = imageUrls[a.animalSeq!!],
                regDttm = a.regDttm,
            )
        }
    }

    private fun classification(combinationSeq: Int) = catalogUseCase.findClassifications(listOf(combinationSeq))[combinationSeq]

    private fun publishMatch(type: MatchEventType, animalSeq: Int, imageSeqs: List<Long>, speciesCode: String) {
        eventPublisher.publish(
            MatchRequested(type, TargetType.SHELTER_ANIMAL, animalSeq.toLong(), referenceReader.findImageKeys(imageSeqs), speciesCode, OffsetDateTime.now()),
        )
    }

    /** 컬럼 길이(V3). 넘으면 400 */
    private fun validateBasics(b: AnimalBasics) {
        val tooLong = listOf("age" to (b.age to 30), "microchipNumber" to (b.microchipNumber to 30), "registrationNumber" to (b.registrationNumber to 20), "weight" to (b.weight to 30))
            .filter { (_, v) -> (v.first?.length ?: 0) > v.second }
            .map { (field, v) -> ErrorDetail(field, "${v.second}자 이하로 입력해 주세요.") }
        if (tooLong.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = tooLong)
    }

    private fun validateImageCount(imageSeqs: List<Long>) {
        if (imageSeqs.size !in 1..MAX_IMAGES || imageSeqs.toSet().size != imageSeqs.size) {
            throw BusinessException(AnimalForeignErrorCode.IMAGE_INVALID_COUNT)
        }
    }

    private fun notFound() = BusinessException(AnimalErrorCode.ANIMAL_NOT_FOUND)

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)

    private val AuthUser.shelter: Int get() = shelterSeq?.let(Math::toIntExact) ?: throw BusinessException(CommonErrorCode.AUTH_FORBIDDEN)

    private val AuthUser.actor: Int get() = Math.toIntExact(userSeq)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private const val DEFAULT_NOTICE_DAYS = 10L
        private const val MAX_IMAGES = 3
    }
}
