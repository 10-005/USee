package com.ssafy.animal.core.adoption.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.adoption.application.port.`in`.FormInput
import com.ssafy.animal.core.adoption.application.port.`in`.FormPatch
import com.ssafy.animal.core.adoption.application.port.`in`.ReviewDecision
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.animal.AnimalTestWorld
import com.ssafy.animal.core.animal.application.port.`in`.ManageAnimalCommand
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalErrorCode
import com.ssafy.animal.core.animal.domain.ProtectionStatus
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AdoptionServiceTest {

    private val world = AnimalTestWorld()
    private val service = world.adoptionService
    private val shelter = world.shelterA
    private val applicantUser = AuthUser(42, Role.USER, null)
    private val page = PageRequest.of(0, 20)

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException>(block = block).errorCode)

    private val notices get() = world.events.filterIsInstance<NotificationRequested>()

    // ===== 12.1 =====

    @Test
    fun `12_1은 문항을 화면 순서로, 보기·조건을 그대로 내린다`() {
        val questions = service.getQuestions().questions
        assertEquals(listOf(1, 2, 3, 17, 20), questions.map { it.questionSeq })
        assertEquals(listOf("아파트", "단독주택", "기타"), questions.first().options)
        assertEquals(17, questions.last().condition!!["depends_on"])
    }

    // ===== 12.4 · 12.5 · 12.7 =====

    @Test
    fun `12_4는 빈 답변으로 DRAFT를 만들고 보호소를 스냅샷한다`() {
        val seq = world.animal().animalSeq!!
        val created = service.createDraft(42, seq, FormInput())

        assertEquals(ApplicationStatus.DRAFT, created.status)
        val application = world.applications.getValue(created.applicationSeq)
        assertEquals(7, application.shelterSeq)
        assertNull(application.modUsrSeq)
        assertTrue(world.events.isEmpty())
    }

    @Test
    fun `12_4 입양 불가 동물은 409, 활성 신청이 있으면 409, 없는 동물은 404`() {
        val unavailable = world.animal(adoptionStatus = AdoptionStatus.UNAVAILABLE).animalSeq!!
        val seq = world.animal().animalSeq!!
        service.createDraft(42, seq, FormInput())

        assertCode(AdoptionErrorCode.ADOPTION_ANIMAL_NOT_AVAILABLE) { service.createDraft(42, unavailable, FormInput()) }
        assertCode(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS) { service.createDraft(42, seq, FormInput()) }
        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.createDraft(42, 999, FormInput()) }
    }

    @Test
    fun `12_5는 answers를 통째로 바꾸고 고정 필드는 미전달 유지·null 비움, 숨김 답변은 지운다`() {
        val seq = world.animal().animalSeq!!
        val draft = service.createDraft(42, seq, FormInput(careAddress = "대구", email = "a@b.com")).applicationSeq

        val saved = service.saveDraft(
            42, draft,
            FormPatch(answers = mapOf("1" to "아파트", "17" to "미이수", "20" to "2026-10-10"), email = Optional.empty()),
        )

        assertEquals(2, saved.savedQuestionCount)
        val application = world.applications.getValue(draft)
        assertEquals(mapOf("1" to "아파트", "17" to "미이수"), application.answersJsonb)
        assertEquals("대구", application.careAddress)
        assertNull(application.email)
        assertEquals(42, application.modUsrSeq)
    }

    @Test
    fun `12_5 없는 문항·보기 밖 값은 400, 남의 신청서는 404, DRAFT가 아니면 409`() {
        val seq = world.animal().animalSeq!!
        val draft = service.createDraft(42, seq, FormInput()).applicationSeq

        assertCode(AdoptionErrorCode.ADOPTION_INVALID_ANSWER) { service.saveDraft(42, draft, FormPatch(answers = mapOf("99" to "x"))) }
        assertCode(AdoptionErrorCode.ADOPTION_INVALID_ANSWER) { service.saveDraft(42, draft, FormPatch(answers = mapOf("1" to "펜트하우스"))) }
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.saveDraft(43, draft, FormPatch()) }

        service.submit(42, seq, world.completeForm())
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_DRAFT) { service.saveDraft(42, draft, FormPatch()) }
    }

    @Test
    fun `12_5 동물이 UNAVAILABLE이 돼도 DRAFT 저장은 되고 제출만 막힌다(Q437)`() {
        val seq = world.animal().animalSeq!!
        val draft = service.createDraft(42, seq, FormInput()).applicationSeq
        world.animalService.manage(shelter, seq, ManageAnimalCommand(status = ProtectionStatus.PROTECTING, adoptionStatus = AdoptionStatus.UNAVAILABLE))

        service.saveDraft(42, draft, FormPatch(answers = mapOf("1" to "아파트")))
        assertEquals(ApplicationStatus.DRAFT, world.applications.getValue(draft).status)
        assertCode(AdoptionErrorCode.ADOPTION_ANIMAL_NOT_AVAILABLE) { service.submit(42, seq, world.completeForm()) }
    }

    @Test
    fun `12_7은 DRAFT만 물리 삭제한다`() {
        val seq = world.animal().animalSeq!!
        val draft = service.createDraft(42, seq, FormInput()).applicationSeq
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.deleteDraft(43, draft) }
        service.deleteDraft(42, draft)
        assertTrue(world.applications.isEmpty())

        val submitted = world.submitted(42, seq)
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_DRAFT) { service.deleteDraft(42, submitted) }
    }

    // ===== 12.6 =====

    @Test
    fun `12_6은 DRAFT를 덮어써 제출하고 스냅샷을 남기고 보호소에 알린다`() {
        val seq = world.animal().animalSeq!!
        val draft = service.createDraft(42, seq, FormInput(answers = mapOf("1" to "기타"))).applicationSeq

        val submitted = service.submit(42, seq, world.completeForm())

        assertEquals(draft, submitted.applicationSeq)
        assertEquals(ApplicationStatus.SUBMITTED, submitted.status)
        assertNotNull(submitted.submittedDttm)
        val application = world.applications.getValue(draft)
        assertEquals("아파트", application.answersJsonb["1"])
        assertEquals("김가람", application.applicantName)
        assertEquals("01012345678", application.applicantPhone)
        val notice = notices.single()
        assertEquals(NotificationType.ADOPTION_SUBMITTED, notice.notificationType)
        assertEquals(77, notice.recipientUserSeq)
        assertEquals("말티즈 입양 신청서가 제출되었어요.", notice.message)
        assertEquals(TargetType.ADOPTION, notice.targetType)
        assertEquals(draft, notice.targetSeq)
    }

    @Test
    fun `12_6 DRAFT가 없으면 새 행을 바로 SUBMITTED로, 이미 제출했으면 409`() {
        val seq = world.animal().animalSeq!!
        val first = service.submit(42, seq, world.completeForm())
        assertNull(world.applications.getValue(first.applicationSeq).modUsrSeq)

        assertCode(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS) { service.submit(42, seq, world.completeForm()) }
    }

    @Test
    fun `12_6 필수 문항·조건부 필수·장소·희망일 누락은 400 REQUIRED, 과거 희망일·이메일 형식은 400 INVALID`() {
        val seq = world.animal().animalSeq!!
        val base = world.completeForm()

        assertCode(AdoptionErrorCode.ADOPTION_REQUIRED_ANSWER_MISSING) { service.submit(42, seq, base.copy(answers = base.answers - "2")) }
        // 17=이수면 20(이수일)이 필수가 된다
        assertCode(AdoptionErrorCode.ADOPTION_REQUIRED_ANSWER_MISSING) { service.submit(42, seq, base.copy(answers = base.answers + ("17" to "이수"))) }
        assertCode(AdoptionErrorCode.ADOPTION_REQUIRED_ANSWER_MISSING) { service.submit(42, seq, base.copy(careAddress = " ")) }
        assertCode(AdoptionErrorCode.ADOPTION_REQUIRED_ANSWER_MISSING) { service.submit(42, seq, base.copy(visitHopeDate = null)) }
        assertCode(AdoptionErrorCode.ADOPTION_INVALID_ANSWER) { service.submit(42, seq, base.copy(visitHopeDate = LocalDate.now().minusDays(1))) }
        assertCode(AdoptionErrorCode.ADOPTION_INVALID_ANSWER) { service.submit(42, seq, base.copy(email = "not-an-email")) }
        assertCode(AdoptionErrorCode.ADOPTION_INVALID_ANSWER) { service.submit(42, seq, base.copy(answers = base.answers + ("3" to "개"))) }
        assertTrue(world.applications.isEmpty())

        val ok = service.submit(42, seq, base.copy(answers = base.answers + ("17" to "이수") + ("20" to "2026-10-01") + ("3" to listOf("개"))))
        assertEquals(ApplicationStatus.SUBMITTED, ok.status)
    }

    // ===== 12.2 · 12.3 =====

    @Test
    fun `12_2는 내 신청만 AnimalSummary와 함께, status·animalSeq로 거른다`() {
        val seq = world.animal().animalSeq!!
        val other = world.animal(shelterSeq = 8, imageSeqs = listOf(1801)).animalSeq!!
        service.createDraft(42, seq, FormInput())
        world.submitted(42, other)
        world.submitted(43, seq)

        val mine = service.getMyApplications(42, null, null, page)
        assertEquals(2, mine.totalElements)
        val drafts = service.getMyApplications(42, ApplicationStatus.DRAFT, seq, page).content.single()
        assertEquals("말티즈", drafts.animalSummary!!.breedName)
        assertEquals("대구동물보호센터", drafts.animalSummary!!.shelterName)
        assertEquals("https://cdn/1701.jpg", drafts.animalSummary!!.representativeImageUrl)
        assertNotNull(drafts.displayDttm)
    }

    @Test
    fun `12_3 신청자는 DRAFT도 현재 회원 정보로 보고, 보호소는 DRAFT·타 보호소 신청을 404로 본다`() {
        val seq = world.animal().animalSeq!!
        val draft = service.createDraft(42, seq, FormInput()).applicationSeq

        val detail = service.getApplication(applicantUser, draft)
        assertEquals("김가람", detail.applicant.name)
        assertEquals(5, detail.questions.size)
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.getApplication(shelter, draft) }
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.getApplication(AuthUser(43, Role.USER, null), draft) }

        service.submit(42, seq, world.completeForm())
        assertEquals("01012345678", service.getApplication(shelter, draft).applicant.phone)
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.getApplication(world.shelterB, draft) }
    }

    // ===== 12.8 =====

    @Test
    fun `12_8은 SUBMITTED만 취소하고 알림은 없다`() {
        val seq = world.animal().animalSeq!!
        val app = world.submitted(42, seq)
        world.events.clear()

        assertEquals(ApplicationStatus.CANCELED, service.cancel(42, app).status)
        assertTrue(world.events.isEmpty())
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_SUBMITTED) { service.cancel(42, app) }
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.cancel(43, app) }
        // 취소 뒤 재신청은 새 행
        assertTrue(world.submitted(42, seq) != app)
    }

    // ===== 12.9 · 12.16 =====

    @Test
    fun `12_9·12_16은 DRAFT·CANCELED를 빼고 우리 보호소만 본다`() {
        val seq = world.animal().animalSeq!!
        world.submitted(42, seq)
        service.cancel(43, world.submitted(43, seq))
        service.createDraft(44, seq, FormInput())
        world.submitted(42, world.animal(shelterSeq = 8, imageSeqs = listOf(1801)).animalSeq!!)

        val received = service.getReceived(shelter, null, null, page).content.single()
        assertEquals("김가람", received.applicantName)
        assertEquals(mapOf("total" to 1L, "SUBMITTED" to 1L, "APPROVED" to 0L, "ADOPTED" to 0L, "REJECTED" to 0L), service.countReceived(shelter, seq))

        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.getReceived(shelter, ApplicationStatus.DRAFT, null, page) }
        assertCode(AnimalErrorCode.ANIMAL_NOT_FOUND) { service.countReceived(world.shelterB, seq) }
    }

    // ===== 12.10 =====

    @Test
    fun `12_10 승인·거절은 SUBMITTED에서만, 거절 사유 필수, 신청자에게 알린다`() {
        val seq = world.animal().animalSeq!!
        val approved = world.submitted(42, seq)
        val rejected = world.submitted(43, seq)
        world.events.clear()

        val result = service.review(shelter, approved, ReviewDecision.APPROVE, null)
        assertEquals(ApplicationStatus.APPROVED, result.status)
        assertEquals(77, result.reviewedUsrSeq)
        assertCode(AdoptionErrorCode.ADOPTION_REJECTION_REASON_REQUIRED) { service.review(shelter, rejected, ReviewDecision.REJECT, " ") }
        service.review(shelter, rejected, ReviewDecision.REJECT, "보호 환경이 맞지 않습니다.")

        assertEquals(listOf(NotificationType.ADOPTION_APPROVED, NotificationType.ADOPTION_REJECTED), notices.map { it.notificationType })
        assertEquals(listOf(42L, 43L), notices.map { it.recipientUserSeq })
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_SUBMITTED) { service.review(shelter, approved, ReviewDecision.APPROVE, null) }
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.review(world.shelterB, approved, ReviewDecision.APPROVE, null) }
    }

    // ===== 12.11 =====

    @Test
    fun `12_11은 한 번에 신청 ADOPTED·동물 ADOPTED·다른 활성 신청 자동 거절을 처리한다`() {
        val seq = world.animal().animalSeq!!
        val chosen = world.submitted(42, seq)
        val other = world.submitted(43, seq)
        service.createDraft(44, seq, FormInput())
        service.review(shelter, chosen, ReviewDecision.APPROVE, null)
        world.events.clear()

        val result = service.complete(shelter, chosen)

        assertEquals(ApplicationStatus.ADOPTED, result.applicationStatus)
        assertEquals(2, result.otherApplicationsAutoRejected)
        assertEquals(chosen, result.completedApplicationSeq)
        val animal = world.animals.getValue(seq)
        assertEquals(ProtectionStatus.ADOPTED, animal.protectionStatus)
        assertEquals(chosen, animal.completedApplicationSeq)
        assertNotNull(animal.protectionEndedDttm)
        assertEquals(ApplicationStatus.REJECTED, world.applications.getValue(other).status)
        assertEquals(
            setOf(42L to NotificationType.ADOPTION_COMPLETED, 43L to NotificationType.ADOPTION_REJECTED, 44L to NotificationType.ADOPTION_REJECTED),
            notices.map { it.recipientUserSeq to it.notificationType }.toSet(),
        )
    }

    @Test
    fun `12_11 승인 전이면 409 NOT_APPROVED, 이미 다른 신청서가 연결됐으면 409 ALREADY_FINISHED`() {
        val seq = world.animal().animalSeq!!
        val first = world.submitted(42, seq)
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_APPROVED) { service.complete(shelter, first) }
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND) { service.complete(world.shelterB, first) }

        service.review(shelter, first, ReviewDecision.APPROVE, null)
        service.complete(shelter, first)
        // 다른 경로로 승인 상태가 남은 신청서를 흉내 낸다(자동 거절 뒤라 실제로는 NOT_APPROVED가 먼저 걸린다)
        val second = world.submitted(43, world.animal().animalSeq!!)
        world.applications.getValue(second).also { org.springframework.test.util.ReflectionTestUtils.setField(it, "animalSeq", seq) }
        world.applications.getValue(second).approve(java.time.OffsetDateTime.now(), 77)
        assertCode(AdoptionErrorCode.ADOPTION_ANIMAL_ALREADY_FINISHED) { service.complete(shelter, second) }
    }

    @Test
    fun `12_11 동물이 11_6으로 먼저 ADOPTED여도 연결이 없으면 채택을 허용하고 상태는 그대로 둔다`() {
        val seq = world.animal().animalSeq!!
        val app = world.submitted(42, seq)
        service.review(shelter, app, ReviewDecision.APPROVE, null)
        // 11.6 ADOPTED는 활성 신청을 자동 거절하므로, 승인 신청이 남은 상황은 동물 상태만 직접 바꿔 흉내 낸다
        world.animals.getValue(seq).markAdoptedExternally("직접 인계", null, java.time.OffsetDateTime.parse("2026-11-01T10:00:00+09:00"), 77)

        service.complete(shelter, app)

        val animal = world.animals.getValue(seq)
        assertEquals(app, animal.completedApplicationSeq)
        assertEquals(java.time.OffsetDateTime.parse("2026-11-01T10:00:00+09:00"), animal.protectionEndedDttm)
    }

    // ===== 12.13 =====

    @Test
    fun `12_13은 APPROVED만 REJECTED로 바꾸고 사유 필수, 신청자에게 알린다`() {
        val seq = world.animal().animalSeq!!
        val app = world.submitted(42, seq)
        assertCode(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_APPROVED) { service.fail(shelter, app, "사유") }
        service.review(shelter, app, ReviewDecision.APPROVE, null)
        assertCode(AdoptionErrorCode.ADOPTION_REJECTION_REASON_REQUIRED) { service.fail(shelter, app, null) }
        world.events.clear()

        val result = service.fail(shelter, app, "승인 이후 입양 진행이 완료되지 못했습니다.")

        assertEquals(ApplicationStatus.REJECTED, result.status)
        assertEquals(NotificationType.ADOPTION_REJECTED, notices.single().notificationType)
    }

    // ===== 2.6 =====

    @Test
    fun `탈퇴하면 본인 활성 신청은 CANCELED, ADOPTED 이력은 남는다`() {
        val seq = world.animal().animalSeq!!
        val adopted = world.submitted(42, seq)
        service.review(shelter, adopted, ReviewDecision.APPROVE, null)
        service.complete(shelter, adopted)
        val draft = service.createDraft(42, world.animal().animalSeq!!, FormInput()).applicationSeq
        val submitted = world.submitted(42, world.animal().animalSeq!!)

        service.cancelAllOfWithdrawnUser(42)

        assertEquals(ApplicationStatus.ADOPTED, world.applications.getValue(adopted).status)
        assertEquals(ApplicationStatus.CANCELED, world.applications.getValue(draft).status)
        assertEquals(ApplicationStatus.CANCELED, world.applications.getValue(submitted).status)
    }
}
