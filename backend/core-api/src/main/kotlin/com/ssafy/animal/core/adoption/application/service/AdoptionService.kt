package com.ssafy.animal.core.adoption.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.adoption.application.port.`in`.AdoptionUseCase
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicantView
import com.ssafy.animal.core.adoption.application.port.`in`.ApplicationDetail
import com.ssafy.animal.core.adoption.application.port.`in`.Completed
import com.ssafy.animal.core.adoption.application.port.`in`.DraftCreated
import com.ssafy.animal.core.adoption.application.port.`in`.DraftSaved
import com.ssafy.animal.core.adoption.application.port.`in`.Failed
import com.ssafy.animal.core.adoption.application.port.`in`.FormInput
import com.ssafy.animal.core.adoption.application.port.`in`.FormPatch
import com.ssafy.animal.core.adoption.application.port.`in`.MyApplicationItem
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionBrief
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionList
import com.ssafy.animal.core.adoption.application.port.`in`.QuestionView
import com.ssafy.animal.core.adoption.application.port.`in`.ReceivedItem
import com.ssafy.animal.core.adoption.application.port.`in`.ReviewDecision
import com.ssafy.animal.core.adoption.application.port.`in`.Reviewed
import com.ssafy.animal.core.adoption.application.port.`in`.ReviewerView
import com.ssafy.animal.core.adoption.application.port.`in`.StatusChanged
import com.ssafy.animal.core.adoption.application.port.`in`.Submitted
import com.ssafy.animal.core.adoption.application.port.out.AdoptionApplicationRepository
import com.ssafy.animal.core.adoption.application.port.out.AdoptionQuestionRepository
import com.ssafy.animal.core.adoption.application.port.out.AdoptionReferenceReader
import com.ssafy.animal.core.adoption.domain.AdoptionApplication
import com.ssafy.animal.core.adoption.domain.AdoptionErrorCode
import com.ssafy.animal.core.adoption.domain.AdoptionFormQuestion
import com.ssafy.animal.core.adoption.domain.AdoptionNotices
import com.ssafy.animal.core.adoption.domain.AnswerPolicy
import com.ssafy.animal.core.adoption.domain.Applicant
import com.ssafy.animal.core.adoption.domain.ApplicationForm
import com.ssafy.animal.core.adoption.domain.ApplicationStatus
import com.ssafy.animal.core.animal.application.port.`in`.AnimalAdoptionUseCase
import com.ssafy.animal.core.animal.application.port.out.AnimalApplicationsPort
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Optional

@Service
class AdoptionService(
    private val applicationRepository: AdoptionApplicationRepository,
    private val questionRepository: AdoptionQuestionRepository,
    private val referenceReader: AdoptionReferenceReader,
    private val animalUseCase: AnimalAdoptionUseCase,
    private val animalApplications: AnimalApplicationsPort,
    private val eventPublisher: EventPublisher,
) : AdoptionUseCase {

    // ===== 12.1 =====
    @Transactional(readOnly = true)
    override fun getQuestions() = QuestionList(
        questionRepository.findAll().map {
            QuestionView(it.questionSeq, it.displayOrder, it.questionText, it.questionType, it.isRequired, it.optionsJsonb, it.conditionJsonb)
        },
    )

    // ===== 12.2 =====
    @Transactional(readOnly = true)
    override fun getMyApplications(userSeq: Long, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<MyApplicationItem> {
        val page = applicationRepository.findMine(userSeq.toInt(), status, animalSeq, pageable)
        val summaries = animalUseCase.findSummaries(page.content.map { it.animalSeq }.toSet())
        return page.map { MyApplicationItem(it.applicationSeq!!, it.status, it.displayDttm, it.submittedDttm, it.rejectionReason, summaries[it.animalSeq]) }
    }

    // ===== 12.3 =====
    @Transactional(readOnly = true)
    override fun getApplication(user: AuthUser, applicationSeq: Long): ApplicationDetail {
        val application = applicationRepository.find(applicationSeq) ?: throw notFound()
        val visible = when (user.role) {
            Role.USER -> application.userSeq.toLong() == user.userSeq
            // SHELTER는 소속 보호소의 제출된 신청만(DRAFT·CANCELED는 404, Q422·Q460)
            Role.SHELTER -> application.shelterSeq.toLong() == user.shelterSeq && application.isVisibleToShelter
        }
        if (!visible) throw notFound()
        val summary = animalUseCase.findSummaries(listOf(application.animalSeq))[application.animalSeq]
        // 제출 전에는 현재 회원 정보, 제출 뒤에는 스냅샷
        val applicant = if (application.submittedDttm == null) {
            referenceReader.findApplicant(application.userSeq) ?: Applicant(null, null, null)
        } else {
            Applicant(application.applicantName, application.applicantBirthDate, application.applicantPhone)
        }
        return with(application) {
            ApplicationDetail(
                applicationSeq = applicationSeq,
                userSeq = userSeq,
                status = status,
                animalSummary = summary,
                careAddress = careAddress,
                careAddressDetail = careAddressDetail,
                email = email,
                visitHopeDate = visitHopeDate,
                answers = answersJsonb,
                questions = questionRepository.findAll().map { QuestionBrief(it.questionSeq, it.questionText, it.questionType, it.displayOrder) },
                applicant = ApplicantView(applicant.name, applicant.birthDate, applicant.phone),
                submittedDttm = submittedDttm,
                reviewedDttm = reviewedDttm,
                reviewer = reviewedUsrSeq?.let { ReviewerView(it, summary?.shelterName) },
                rejectionReason = rejectionReason,
            )
        }
    }

    // ===== 12.4 =====
    @Transactional
    override fun createDraft(userSeq: Long, animalSeq: Int, form: FormInput): DraftCreated {
        val target = animalUseCase.lockForAdoption(animalSeq)
        if (!target.isAdoptable) throw BusinessException(AdoptionErrorCode.ADOPTION_ANIMAL_NOT_AVAILABLE)
        val user = userSeq.toInt()
        if (applicationRepository.findActiveForUpdate(user, animalSeq) != null) {
            throw BusinessException(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS)
        }
        val cleaned = toForm(form, questionRepository.findAll())
        val application = AdoptionApplication(user, animalSeq, target.shelterSeq).apply { saveDraft(cleaned, null) }
        applicationRepository.saveAndFlush(application)
        return DraftCreated(application.applicationSeq!!, application.status, animalSeq)
    }

    // ===== 12.5 =====
    @Transactional
    override fun saveDraft(userSeq: Long, applicationSeq: Long, patch: FormPatch): DraftSaved {
        val application = loadOwn(userSeq, applicationSeq)
        if (application.status != ApplicationStatus.DRAFT) throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_DRAFT)
        // UNAVAILABLE이 된 동물의 DRAFT 저장은 허용한다(제출만 차단, Q437)
        val answers = patch.answers?.let { AnswerPolicy.clean(it, questionRepository.findAll()) } ?: application.answersJsonb
        val form = ApplicationForm(
            answers = answers,
            careAddress = patch.careAddress.orKeep(application.careAddress),
            careAddressDetail = patch.careAddressDetail.orKeep(application.careAddressDetail),
            email = patch.email.orKeep(application.email),
            visitHopeDate = patch.visitHopeDate.orKeep(application.visitHopeDate),
        )
        validateLengths(form)
        application.saveDraft(form, userSeq.toInt())
        applicationRepository.saveAndFlush(application)
        return DraftSaved(applicationSeq, application.status, answers.size)
    }

    // ===== 12.6 =====
    @Transactional
    override fun submit(userSeq: Long, animalSeq: Int, form: FormInput): Submitted {
        // 동물 행을 잠가 같은 동물의 제출·관리 변경·입양 완료를 줄 세운다
        val target = animalUseCase.lockForAdoption(animalSeq)
        if (!target.isAdoptable) throw BusinessException(AdoptionErrorCode.ADOPTION_ANIMAL_NOT_AVAILABLE)
        val user = userSeq.toInt()
        val existing = applicationRepository.findActiveForUpdate(user, animalSeq)
        if (existing != null && existing.status != ApplicationStatus.DRAFT) {
            throw BusinessException(AdoptionErrorCode.ADOPTION_ACTIVE_APPLICATION_ALREADY_EXISTS)
        }
        val questions = questionRepository.findAll()
        val cleaned = toForm(form, questions)
        AnswerPolicy.requireComplete(cleaned, questions, LocalDate.now(KST))
        val applicant = referenceReader.findApplicant(user) ?: Applicant(null, null, null)

        val application = existing ?: AdoptionApplication(user, animalSeq, target.shelterSeq)
        application.submit(cleaned, applicant, OffsetDateTime.now(), existing?.let { user })
        applicationRepository.saveAndFlush(application)
        referenceReader.findShelterUserSeqs(target.shelterSeq)
            .forEach { eventPublisher.publish(AdoptionNotices.submitted(it, application, target.breedName)) }
        return Submitted(application.applicationSeq!!, application.status, application.submittedDttm)
    }

    // ===== 12.7 =====
    @Transactional
    override fun deleteDraft(userSeq: Long, applicationSeq: Long) {
        val application = loadOwn(userSeq, applicationSeq)
        if (application.status != ApplicationStatus.DRAFT) throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_DRAFT)
        applicationRepository.delete(application)
    }

    // ===== 12.8 =====
    @Transactional
    override fun cancel(userSeq: Long, applicationSeq: Long): StatusChanged {
        val application = loadOwn(userSeq, applicationSeq)
        // APPROVED 이후 직접 취소는 없다(Q459)
        if (application.status != ApplicationStatus.SUBMITTED) throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_SUBMITTED)
        application.cancel(userSeq.toInt())
        return StatusChanged(applicationSeq, application.status)
    }

    // ===== 12.9 =====
    @Transactional(readOnly = true)
    override fun getReceived(shelter: AuthUser, status: ApplicationStatus?, animalSeq: Int?, pageable: Pageable): Page<ReceivedItem> {
        if (status != null && status !in ApplicationStatus.RECEIVED) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("status", "SUBMITTED, APPROVED, ADOPTED, REJECTED만 조회할 수 있습니다.")))
        }
        val page = applicationRepository.findReceived(shelter.shelter, status, animalSeq, pageable)
        val summaries = animalUseCase.findSummaries(page.content.map { it.animalSeq }.toSet())
        return page.map { ReceivedItem(it.applicationSeq!!, it.status, it.applicantName, it.submittedDttm, summaries[it.animalSeq]) }
    }

    // ===== 12.16 =====
    @Transactional(readOnly = true)
    override fun countReceived(shelter: AuthUser, animalSeq: Int?): Map<String, Long> {
        animalSeq?.let { animalUseCase.requireOwnedBy(shelter.shelter, it) }
        return AnimalApplicationsService.toCounts(applicationRepository.countReceived(shelter.shelter, animalSeq))
    }

    // ===== 12.10 =====
    @Transactional
    override fun review(shelter: AuthUser, applicationSeq: Long, decision: ReviewDecision, rejectionReason: String?): Reviewed {
        val application = loadForShelter(shelter, applicationSeq)
        if (application.status != ApplicationStatus.SUBMITTED) throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_SUBMITTED)
        val now = OffsetDateTime.now()
        val type = when (decision) {
            ReviewDecision.APPROVE -> {
                application.approve(now, shelter.actor)
                NotificationType.ADOPTION_APPROVED
            }
            ReviewDecision.REJECT -> {
                application.reject(requireReason(rejectionReason), now, shelter.actor)
                NotificationType.ADOPTION_REJECTED
            }
        }
        notifyApplicant(type, application)
        return Reviewed(applicationSeq, application.status, application.reviewedDttm, application.reviewedUsrSeq)
    }

    // ===== 12.11 =====
    @Transactional
    override fun complete(shelter: AuthUser, applicationSeq: Long): Completed {
        // 잠금 순서: 동물 → 신청서(11.6 자동 거절과 같은 순서)
        val animalSeq = applicationRepository.findAnimalSeq(applicationSeq) ?: throw notFound()
        val target = animalUseCase.lockForAdoption(animalSeq)
        val application = loadForShelter(shelter, applicationSeq)
        if (application.status != ApplicationStatus.APPROVED) throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_APPROVED)
        // 11.6으로 먼저 ADOPTED가 된 동물(FK NULL)은 채택을 허용한다(Q479)
        if (target.completedApplicationSeq != null) throw BusinessException(AdoptionErrorCode.ADOPTION_ANIMAL_ALREADY_FINISHED)

        application.complete(shelter.actor)
        applicationRepository.saveAndFlush(application)
        animalUseCase.completeAdoption(animalSeq, applicationSeq, shelter.actor)
        val rejected = animalApplications.rejectActive(target, OTHER_ADOPTED_REASON, exceptApplicationSeq = applicationSeq)
        eventPublisher.publish(AdoptionNotices.result(NotificationType.ADOPTION_COMPLETED, application, target.shelterName, target.breedName))
        return Completed(applicationSeq, application.status, animalSeq, ADOPTED, ADOPTED, applicationSeq, rejected)
    }

    // ===== 12.13 =====
    @Transactional
    override fun fail(shelter: AuthUser, applicationSeq: Long, rejectionReason: String?): Failed {
        val application = loadForShelter(shelter, applicationSeq)
        if (application.status != ApplicationStatus.APPROVED) throw BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_APPROVED)
        application.reject(requireReason(rejectionReason), OffsetDateTime.now(), shelter.actor)
        notifyApplicant(NotificationType.ADOPTION_REJECTED, application)
        return Failed(applicationSeq, application.status, application.rejectionReason)
    }

    // ===== 2.6 =====
    @Transactional
    override fun cancelAllOfWithdrawnUser(userSeq: Long) {
        applicationRepository.cancelActiveOfUser(userSeq.toInt(), OffsetDateTime.now())
    }

    // ===== 공통 =====

    /** 본인 신청서를 잠가 읽는다. 타인 것은 존재를 감춘다(Q434) */
    private fun loadOwn(userSeq: Long, applicationSeq: Long) =
        applicationRepository.findForUpdate(applicationSeq)?.takeIf { it.userSeq.toLong() == userSeq } ?: throw notFound()

    /** 소속 보호소의 보이는 신청서를 잠가 읽는다. 타 보호소·DRAFT·CANCELED는 404 */
    private fun loadForShelter(shelter: AuthUser, applicationSeq: Long) =
        applicationRepository.findForUpdate(applicationSeq)
            ?.takeIf { it.shelterSeq == shelter.shelter && it.isVisibleToShelter } ?: throw notFound()

    private fun notifyApplicant(type: NotificationType, application: AdoptionApplication) {
        val summary = animalUseCase.findSummaries(listOf(application.animalSeq))[application.animalSeq]
        eventPublisher.publish(AdoptionNotices.result(type, application, summary?.shelterName, summary?.breedName))
    }

    private fun toForm(input: FormInput, questions: List<AdoptionFormQuestion>) = ApplicationForm(
        answers = AnswerPolicy.clean(input.answers, questions),
        careAddress = input.careAddress,
        careAddressDetail = input.careAddressDetail,
        email = input.email,
        visitHopeDate = input.visitHopeDate,
    ).also(::validateLengths)

    /** 컬럼 길이(V3). 넘으면 400 */
    private fun validateLengths(form: ApplicationForm) {
        val errors = listOf("careAddress" to (form.careAddress to 200), "careAddressDetail" to (form.careAddressDetail to 100), "email" to (form.email to 254))
            .filter { (_, v) -> (v.first?.length ?: 0) > v.second }
            .map { (field, v) -> ErrorDetail(field, "${v.second}자 이하로 입력해 주세요.") }
        if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = errors)
    }

    private fun requireReason(reason: String?) =
        reason?.trim()?.takeIf { it.isNotEmpty() } ?: throw BusinessException(AdoptionErrorCode.ADOPTION_REJECTION_REASON_REQUIRED)

    private fun notFound() = BusinessException(AdoptionErrorCode.ADOPTION_APPLICATION_NOT_FOUND)

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)

    private val AuthUser.shelter: Int get() = shelterSeq?.let(Math::toIntExact) ?: throw BusinessException(CommonErrorCode.AUTH_FORBIDDEN)

    private val AuthUser.actor: Int get() = Math.toIntExact(userSeq)

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private const val ADOPTED = "ADOPTED"
        private const val OTHER_ADOPTED_REASON = "다른 신청자의 입양이 확정되어 입양 신청이 자동으로 거절되었습니다."
    }
}
