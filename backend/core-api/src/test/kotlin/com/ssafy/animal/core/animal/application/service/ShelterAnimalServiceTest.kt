package com.ssafy.animal.core.animal.application.service

import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.animal.AnimalTestWorld
import com.ssafy.animal.core.animal.application.port.`in`.AnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.ManageAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalSearchCondition
import com.ssafy.animal.core.animal.application.port.`in`.RegisterAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.UpdateAnimalCommand
import com.ssafy.animal.core.animal.application.port.out.RescueRow
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalBasics
import com.ssafy.animal.core.animal.domain.AnimalErrorCode
import com.ssafy.animal.core.animal.domain.AnimalForeignErrorCode
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.EndReason
import com.ssafy.animal.core.animal.domain.Gender
import com.ssafy.animal.core.animal.domain.ProtectionStatus
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.image.domain.ImageErrorCode
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShelterAnimalServiceTest {

    private val world = AnimalTestWorld()
    private val service = world.animalService
    private val shelter = world.shelterA

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException>(block = block).errorCode)

    private fun basics(combinationSeq: Int = 88, registrationNumber: String? = null) =
        AnimalBasics(combinationSeq, Gender.UNKNOWN, "1살 추정", null, null, registrationNumber, "3kg", "순함", null, null)

    private fun register(
        rescueSeq: Int? = null,
        imageSeqs: List<Long> = listOf(1701, 1702),
        start: LocalDate? = null,
        end: LocalDate? = null,
        adoptionStatus: AdoptionStatus? = null,
        basics: AnimalBasics = basics(),
    ) = service.register(shelter, RegisterAnimalCommand(rescueSeq, basics, start, end, adoptionStatus, imageSeqs))

    // ===== 11.4 =====

    @Test
    fun `11_4는 PROTECTING·UNAVAILABLE·공고 10일 기본값으로 등록하고 매칭 이벤트를 낸다`() {
        val created = register()

        assertEquals(ProtectionStatus.PROTECTING, created.protectionStatus)
        assertEquals(AdoptionStatus.UNAVAILABLE, created.adoptionStatus)
        assertEquals(created.noticeStartDate.plusDays(10), created.noticeEndDate)
        val animal = world.animals.getValue(created.animalSeq)
        assertEquals(7, animal.shelterSeq)
        assertEquals(77, animal.regUsrSeq)
        assertNull(animal.modUsrSeq)
        assertEquals(listOf(1701L, 1702L), world.images.getValue(created.animalSeq).map { it.imageSeq })
        val match = world.events.single() as MatchRequested
        assertEquals(MatchEventType.SHELTER_ANIMAL_REGISTERED, match.eventType)
        assertEquals("DOG", match.speciesCode)
        assertEquals(listOf("images/2026/11/1701.jpg", "images/2026/11/1702.jpg"), match.imageKeys)
        assertEquals(world.matchCandidates, match.candidates)
    }

    @Test
    fun `11_4 한쪽 날짜만 보내면 나머지는 기본 규칙, 역전이면 400`() {
        val created = register(start = LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 11, 11), created.noticeEndDate)

        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { register(start = LocalDate.of(2026, 11, 10), end = LocalDate.of(2026, 11, 1)) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { register(adoptionStatus = AdoptionStatus.ADOPTED) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { register(basics = basics(registrationNumber = "1".repeat(21))) }
    }

    @Test
    fun `11_4 구조 연결은 DONE만, 없거나 삭제면 404 RESCUE_NOT_FOUND`() {
        world.rescues[301] = RescueRow(301, "DONE", false, OffsetDateTime.now(), "WANDERING", "대구", null, null)
        world.rescues[302] = RescueRow(302, "PROCESSING", false, OffsetDateTime.now(), "WANDERING", "대구", null, null)
        world.rescues[303] = RescueRow(303, "DONE", true, OffsetDateTime.now(), "WANDERING", "대구", null, null)

        assertEquals(301, world.animals.getValue(register(rescueSeq = 301).animalSeq).rescueSeq)
        assertCode(AnimalForeignErrorCode.RESCUE_NOT_DONE) { register(rescueSeq = 302) }
        assertCode(AnimalForeignErrorCode.RESCUE_NOT_FOUND) { register(rescueSeq = 303) }
        assertCode(AnimalForeignErrorCode.RESCUE_NOT_FOUND) { register(rescueSeq = 999) }
    }

    @Test
    fun `11_4 사진 0장·4장·중복은 400 IMAGE_INVALID_COUNT, 남의 사진은 403, 없는 조합은 404`() {
        assertCode(AnimalForeignErrorCode.IMAGE_INVALID_COUNT) { register(imageSeqs = emptyList()) }
        assertCode(AnimalForeignErrorCode.IMAGE_INVALID_COUNT) { register(imageSeqs = listOf(1701, 1702, 1703, 1704)) }
        assertCode(AnimalForeignErrorCode.IMAGE_INVALID_COUNT) { register(imageSeqs = listOf(1701, 1701)) }
        assertCode(ImageErrorCode.IMAGE_NOT_OWNED) { register(imageSeqs = listOf(1801)) }
        assertCode(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND) { register(basics = basics(combinationSeq = 1)) }
        assertTrue(world.animals.isEmpty())
    }

    // ===== 11.1 · 11.3 =====

    @Test
    fun `11_1은 보호소 블록·대표 사진을 채우고 지역 필터는 보호소 소재지 기준이다`() {
        world.animal(shelterSeq = 7)
        world.animal(shelterSeq = 8, imageSeqs = listOf(1801))
        val page = PageRequest.of(0, 20)

        val all = service.search(AnimalSearchCondition(), page)
        assertEquals(2, all.totalElements)

        val gumi = service.search(AnimalSearchCondition(districtCode = "47190"), page).content.single()
        assertEquals(8, gumi.shelter!!.shelterSeq)
        assertEquals("47190", gumi.shelter!!.districtCode)
        assertEquals("https://cdn/1801.jpg", gumi.representativeImageUrl)
        assertEquals("말티즈", gumi.classification!!.breedName)

        assertEquals(0, service.search(AnimalSearchCondition(speciesSeq = 2), page).totalElements)
    }

    @Test
    fun `11_1 날짜 역전은 400, 없는 지역 코드는 400 CATALOG_INVALID_DISTRICT_CODE`() {
        val page = PageRequest.of(0, 20)
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) {
            service.search(AnimalSearchCondition(regFrom = LocalDate.of(2026, 11, 2), regTo = LocalDate.of(2026, 11, 1)), page)
        }
        assertCode(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE) { service.search(AnimalSearchCondition(districtCode = "99999"), page) }
    }

    @Test
    fun `11_3은 우리 보호소만, statusCounts는 status를 뺀 필터로 센다`() {
        world.animal()
        world.animal(adoptionStatus = AdoptionStatus.UNAVAILABLE)
        val ended = world.animal()
        service.manage(shelter, ended.animalSeq!!, ManageAnimalCommand(status = ProtectionStatus.ENDED))
        world.animal(shelterSeq = 8)

        val result = service.searchMine(shelter, MyAnimalSearchCondition(status = AnimalStatus.PROTECTING_AVAILABLE), PageRequest.of(0, 8))

        assertEquals(1, result.page.totalElements)
        assertEquals(
            mapOf("total" to 3L, "PROTECTING_AVAILABLE" to 1L, "PROTECTING_UNAVAILABLE" to 1L, "RETURNED" to 0L, "ADOPTED" to 0L, "ENDED" to 1L),
            result.statusCounts,
        )
        assertEquals(listOf("total", "PROTECTING_AVAILABLE", "PROTECTING_UNAVAILABLE", "RETURNED", "ADOPTED", "ENDED"), result.statusCounts.keys.toList())
    }

    // ===== 11.2 · 11.10 =====

    @Test
    fun `11_2는 구조 정보를 매번 읽고 연결이 없으면 5개 모두 null, 북마크 여부를 채운다`() {
        world.rescues[301] = RescueRow(301, "DONE", false, OffsetDateTime.parse("2026-10-29T09:30:00+09:00"), "WANDERING", "대구", null, "메모")
        val linked = register(rescueSeq = 301).animalSeq
        val direct = world.animal().animalSeq!!
        world.bookmarks += 42 to linked

        val detail = service.getForUser(42, linked)
        assertEquals("WANDERING", detail.rescueInfo.rescueReasonCode)
        assertEquals("0531234567", detail.shelter!!.phone)
        assertEquals(88, detail.classification!!.combinationSeq)
        assertEquals(1, detail.classification!!.speciesSeq)
        assertTrue(detail.isBookmarked)
        assertEquals(listOf(1, 2), detail.images.map { it.displayOrder })

        val plain = service.getForUser(42, direct)
        assertNull(plain.rescueInfo.rescuedDttm)
        assertNull(plain.rescueInfo.rescueReasonCode)
        assertEquals(false, plain.isBookmarked)
    }

    @Test
    fun `11_2·11_10 없는·삭제된 동물은 404, 11_10은 다른 보호소 동물도 404`() {
        val mine = world.animal(adoptionStatus = AdoptionStatus.UNAVAILABLE)
        service.delete(shelter, mine.animalSeq!!)
        val others = world.animal(shelterSeq = 8)

        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.getForUser(42, 999) }
        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.getForUser(42, mine.animalSeq!!) }
        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.getForShelter(shelter, others.animalSeq!!) }
    }

    @Test
    fun `11_10은 신청 건수(DRAFT·CANCELED 제외)와 채택 정보를 동봉한다`() {
        val animal = world.animal()
        val seq = animal.animalSeq!!
        val adopted = world.submitted(42, seq)
        world.submitted(43, seq)
        world.adoptionService.createDraft(44, seq, com.ssafy.animal.core.adoption.application.port.`in`.FormInput())
        world.adoptionService.review(shelter, adopted, com.ssafy.animal.core.adoption.application.port.`in`.ReviewDecision.APPROVE, null)
        world.adoptionService.complete(shelter, adopted)

        val detail = service.getForShelter(shelter, seq)

        assertEquals(mapOf("total" to 2L, "SUBMITTED" to 0L, "APPROVED" to 0L, "ADOPTED" to 1L, "REJECTED" to 1L), detail.applicationCounts)
        assertEquals(adopted, detail.adoptionInfo!!.completedApplicationSeq)
        assertEquals("김가람", detail.adoptionInfo!!.applicantName)
        assertNull(detail.adoptionInfo!!.reason)
        assertNull(detail.returnInfo)
    }

    // ===== 11.5 =====

    @Test
    fun `11_5는 보낸 필드만 바꾸고 사진 diff면 빠진 사진을 정리하고 재매칭을 요청한다`() {
        val seq = register(imageSeqs = listOf(1701, 1702)).animalSeq
        world.events.clear()

        val detail = service.update(
            shelter, seq,
            UpdateAnimalCommand(age = Optional.of("2살 추정"), weight = Optional.empty(), imageSeqs = listOf(1702, 1703)),
        )

        assertEquals("2살 추정", detail.age)
        assertNull(detail.weight)
        assertEquals("순함", detail.description)
        assertEquals(listOf(1702L, 1703L), detail.images.map { it.imageSeq })
        assertEquals(listOf(1701L), world.released)
        assertEquals(MatchEventType.IMAGES_CHANGED, (world.events.single() as MatchRequested).eventType)
        assertEquals(77, world.animals.getValue(seq).modUsrSeq)
    }

    @Test
    fun `11_5 순서만 바꾸면 재매칭하지 않고, 다른 보호소 동물은 404`() {
        val seq = register(imageSeqs = listOf(1701, 1702)).animalSeq
        world.events.clear()
        service.update(shelter, seq, UpdateAnimalCommand(imageSeqs = listOf(1702, 1701)))
        assertTrue(world.events.isEmpty())
        assertTrue(world.released.isEmpty())

        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.update(world.shelterB, seq, UpdateAnimalCommand(age = Optional.of("x"))) }
        assertCode(CatalogErrorCode.CATALOG_COMBINATION_NOT_FOUND) { service.update(shelter, seq, UpdateAnimalCommand(combinationSeq = 1)) }
    }

    // ===== 11.6 =====

    @Test
    fun `11_6 ENDED는 네 컬럼을 함께 바꾸고 활성 신청을 자동 거절·알린다`() {
        val seq = world.animal().animalSeq!!
        val app = world.submitted(42, seq)
        world.events.clear()

        val result = service.manage(shelter, seq, ManageAnimalCommand(status = ProtectionStatus.ENDED, endReason = EndReason.NATURAL_DEATH))

        assertEquals(ProtectionStatus.ENDED, result.protectionStatus)
        assertEquals(AdoptionStatus.UNAVAILABLE, result.adoptionStatus)
        assertEquals(EndReason.NATURAL_DEATH, result.endReason)
        assertNotNull(result.protectionEndedDttm)
        assertEquals(1, result.autoRejectedApplications)
        val rejected = world.applications.getValue(app)
        assertEquals(ApplicationStatus.REJECTED, rejected.status)
        assertNull(rejected.reviewedUsrSeq)
        assertTrue(rejected.rejectionReason!!.isNotBlank())
        val notice = world.events.single() as NotificationRequested
        assertEquals(NotificationType.ADOPTION_REJECTED, notice.notificationType)
        assertEquals(42, notice.recipientUserSeq)
        assertEquals("대구동물보호센터 말티즈 입양 신청 결과를 확인해보세요.", notice.message)
    }

    @Test
    fun `11_6 RETURNED는 반환 행을 만들고 다시 보내면 덮어쓴다, 같은 번호 회원이 1명이면 연결`() {
        val seq = world.animal().animalSeq!!

        val first = service.manage(shelter, seq, ManageAnimalCommand(status = ProtectionStatus.RETURNED, recipientName = "김가람", recipientPhone = "01012345678"))
        assertEquals(EndReason.RETURNED, first.endReason)
        assertEquals(42, first.returnInfo!!.returnedUserSeq)
        assertEquals(first.returnInfo!!.returnedDttm, first.protectionEndedDttm)

        val second = service.manage(shelter, seq, ManageAnimalCommand(status = ProtectionStatus.RETURNED, recipientName = "보호자", recipientPhone = "0100000"))
        assertEquals(first.returnInfo!!.returnSeq, second.returnInfo!!.returnSeq)
        assertEquals("보호자", second.returnInfo!!.recipientName)
        assertNull(second.returnInfo!!.returnedUserSeq)
        assertNotNull(world.returns.getValue(seq).modDttm)
        assertEquals(1, world.returns.size)
    }

    @Test
    fun `11_6 ADOPTED는 사유·비고를 저장하고 신청서는 연결하지 않는다, PROTECTING 복귀는 기본 UNAVAILABLE`() {
        val seq = world.animal().animalSeq!!

        val adopted = service.manage(shelter, seq, ManageAnimalCommand(status = ProtectionStatus.ADOPTED, reason = "직접 인계", memo = "2차 접종 안내"))
        assertEquals(AdoptionStatus.ADOPTED, adopted.adoptionStatus)
        assertEquals("직접 인계", adopted.adoptionInfo!!.reason)
        assertNull(adopted.adoptionInfo!!.completedApplicationSeq)

        val back = service.manage(shelter, seq, ManageAnimalCommand(status = ProtectionStatus.PROTECTING))
        assertEquals(AdoptionStatus.UNAVAILABLE, back.adoptionStatus)
        assertNull(back.endReason)
        assertNull(back.protectionEndedDttm)
        // 이력은 남는다
        assertEquals("직접 인계", world.animals.getValue(seq).adoptionReason)
    }

    @Test
    fun `11_6 공고기간만 바꾸면 상태는 그대로, 역전은 400`() {
        val seq = world.animal().animalSeq!!
        val result = service.manage(shelter, seq, ManageAnimalCommand(noticeEndDate = LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 15), result.noticeEndDate)
        assertEquals(AdoptionStatus.AVAILABLE, result.adoptionStatus)
        assertEquals(0, result.autoRejectedApplications)

        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.manage(shelter, seq, ManageAnimalCommand(noticeStartDate = LocalDate.of(2026, 12, 1))) }
    }

    @Test
    fun `11_6 상태와 맞지 않는 추가 필드·필수 누락·서버 전용 값은 400, 다른 보호소는 404`() {
        val seq = world.animal().animalSeq!!
        listOf(
            ManageAnimalCommand(status = ProtectionStatus.ENDED, adoptionStatus = AdoptionStatus.AVAILABLE),
            ManageAnimalCommand(adoptionStatus = AdoptionStatus.AVAILABLE),
            ManageAnimalCommand(status = ProtectionStatus.RETURNED, recipientName = "김가람"),
            ManageAnimalCommand(status = ProtectionStatus.ADOPTED),
            ManageAnimalCommand(status = ProtectionStatus.ENDED, endReason = EndReason.RETURNED),
            ManageAnimalCommand(status = ProtectionStatus.PROTECTING, adoptionStatus = AdoptionStatus.ADOPTED),
            ManageAnimalCommand(status = ProtectionStatus.PROTECTING, reason = "x"),
        ).forEach { assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.manage(shelter, seq, it) } }

        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.manage(world.shelterB, seq, ManageAnimalCommand(status = ProtectionStatus.ENDED)) }
    }

    // ===== 11.9 =====

    @Test
    fun `11_9 활성 신청이 있으면 409, 없으면 논리 삭제`() {
        val seq = world.animal().animalSeq!!
        val app = world.submitted(42, seq)
        assertCode(AnimalErrorCode.ANIMAL_HAS_ACTIVE_APPLICATIONS) { service.delete(shelter, seq) }

        world.adoptionService.cancel(42, app)
        service.delete(shelter, seq)
        assertTrue(world.animals.getValue(seq).isDeleted)
        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.delete(shelter, seq) }
    }
}
