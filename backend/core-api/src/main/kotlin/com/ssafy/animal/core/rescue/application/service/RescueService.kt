package com.ssafy.animal.core.rescue.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.found.application.port.`in`.ReporterContact
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.rescue.application.port.`in`.CreateRescueCommand
import com.ssafy.animal.core.rescue.application.port.`in`.FoundReportSummary
import com.ssafy.animal.core.rescue.application.port.`in`.RescueAnimal
import com.ssafy.animal.core.rescue.application.port.`in`.RescueDetail
import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.application.port.`in`.RescueItem
import com.ssafy.animal.core.rescue.application.port.`in`.RescuePage
import com.ssafy.animal.core.rescue.application.port.`in`.RescueUseCase
import com.ssafy.animal.core.rescue.application.port.`in`.UpdateRescueCommand
import com.ssafy.animal.core.rescue.application.port.out.AnimalRow
import com.ssafy.animal.core.rescue.application.port.out.RescueRepository
import com.ssafy.animal.core.rescue.domain.RescueErrorCode
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RescueService(
    private val rescueRepository: RescueRepository,
    private val catalogUseCase: CatalogUseCase,
    private val imageUseCase: ImageUseCase,
) : RescueUseCase {

    @Transactional(readOnly = true)
    override fun getPage(shelterSeq: Long?, filter: RescueFilter, pageable: Pageable): RescuePage {
        val shelter = shelterOf(shelterSeq)
        val page = rescueRepository.findPage(shelter, filter, pageable)
        val rescues = page.content
        // 동물 정보 출처: REPORT는 발견 신고, DIRECT는 가장 먼저 등록된 보호동물(10.1)
        val fromReports = rescueRepository.findFoundReportAnimals(rescues.mapNotNull { it.foundReportSeq })
        val fromAnimals = rescueRepository.findFirstShelterAnimals(rescues.filter { it.foundReportSeq == null }.map { it.rescueSeq!! })
        val rows = rescues.associateWith { r -> r.foundReportSeq?.let(fromReports::get) ?: fromAnimals[r.rescueSeq] }
        val classifications = catalogUseCase.findClassifications(rows.values.mapNotNull { it?.combinationSeq })
        val counts = rescueRepository.countByStatus(shelter, filter)
        return RescuePage(
            statusCounts = RescueStatus.entries.associateWith { counts[it] ?: 0L },
            page = page.map { r ->
                RescueItem(
                    r,
                    rows[r]?.let { row ->
                        val c = classifications[row.combinationSeq]
                        RescueAnimal(c?.speciesName, c?.breedName, row.age, row.gender, row.isNeutered, c?.colorName, row.weight, imageUrl(row))
                    },
                )
            },
        )
    }

    @Transactional(readOnly = true)
    override fun get(shelterSeq: Long?, rescueSeq: Long) = detail(loadOwned(shelterSeq, rescueSeq))

    @Transactional
    override fun create(user: AuthUser, command: CreateRescueCommand): RescueRecord {
        val shelter = shelterOf(user.shelterSeq)
        command.foundReportSeq?.let { seq ->
            // 담당이 아닌 신고도, CLOSED 신고도 접수할 수 있다(R3 A·Q442). 삭제된 신고만 404
            if (!rescueRepository.existsActiveFoundReport(seq)) throw BusinessException(FoundErrorCode.FOUND_REPORT_NOT_FOUND)
            if (rescueRepository.existsOngoingRescue(seq)) throw BusinessException(RescueErrorCode.RESCUE_ACTIVE_ALREADY_EXISTS)
        }
        return rescueRepository.saveAndFlush(RescueRecord(shelter, command.foundReportSeq, command.content, Math.toIntExact(user.userSeq)))
    }

    @Transactional
    override fun update(user: AuthUser, rescueSeq: Long, command: UpdateRescueCommand): RescueDetail {
        val rescue = loadOwned(user.shelterSeq, rescueSeq)
        // 종료 상태는 메모만 고칠 수 있다(R4 A)
        if (rescue.status.isTerminal && command.nonMemoFields.isNotEmpty()) {
            throw BusinessException(
                CommonErrorCode.COMMON_INVALID_INPUT,
                errors = command.nonMemoFields.map { ErrorDetail(it, "종료된 구조는 메모만 수정할 수 있습니다.") },
            )
        }
        rescue.update(command.applyTo(rescue.content), Math.toIntExact(user.userSeq))
        return detail(rescueRepository.saveAndFlush(rescue))
    }

    @Transactional
    override fun changeStatus(user: AuthUser, rescueSeq: Long, status: RescueStatus, resultReason: String?): RescueRecord {
        val rescue = loadOwned(user.shelterSeq, rescueSeq)
        if (rescue.status != RescueStatus.PROCESSING || !status.isTerminal) throw BusinessException(RescueErrorCode.RESCUE_INVALID_TRANSITION)
        val reason = resultReason?.trim()?.ifEmpty { null }
        if (status != RescueStatus.DONE && reason == null) throw BusinessException(RescueErrorCode.RESCUE_RESULT_REASON_REQUIRED)
        rescue.complete(status, reason, OffsetDateTime.now(), Math.toIntExact(user.userSeq))
        return rescueRepository.saveAndFlush(rescue)
    }

    @Transactional
    override fun delete(user: AuthUser, rescueSeq: Long) {
        val rescue = loadOwned(user.shelterSeq, rescueSeq)
        if (rescueRepository.countLinkedAnimals(rescue.rescueSeq!!) > 0) throw BusinessException(RescueErrorCode.RESCUE_HAS_ANIMALS)
        rescue.delete(Math.toIntExact(user.userSeq))
        rescueRepository.saveAndFlush(rescue)
    }

    private fun detail(rescue: RescueRecord): RescueDetail {
        val summary = rescue.foundReportSeq?.let(rescueRepository::findFoundReportSummary)?.let { row ->
            FoundReportSummary(
                foundReportSeq = row.foundReportSeq,
                classification = catalogUseCase.findClassifications(listOf(row.animal.combinationSeq))[row.animal.combinationSeq],
                age = row.animal.age,
                gender = row.animal.gender,
                isNeutered = row.animal.isNeutered,
                weight = row.animal.weight,
                address = row.address,
                occurredDttm = row.occurredDttm,
                representativeImageUrl = imageUrl(row.animal),
                // 접수한 보호소에는 신고자 실명·연락처를 공개한다(R3·Q472)
                reporter = ReporterContact(if (row.reporterDeleted) WITHDRAWN_NAME else row.reporterName, row.reporterPhone),
                status = row.status,
            )
        }
        return RescueDetail(rescue, rescueRepository.countLinkedAnimals(rescue.rescueSeq!!), summary)
    }

    private fun imageUrl(row: AnimalRow) = row.imageSeq?.let(imageUseCase::findSummary)?.imageUrl

    private fun shelterOf(shelterSeq: Long?): Int = Math.toIntExact(requireNotNull(shelterSeq) { "SHELTER 토큰에 shelterSeq 없음" })

    /** 내 보호소의 삭제되지 않은 구조. 다른 보호소 것도 404(Q434) */
    private fun loadOwned(shelterSeq: Long?, rescueSeq: Long): RescueRecord =
        rescueRepository.findActive(Math.toIntExact(rescueSeq))?.takeIf { it.shelterSeq.toLong() == shelterSeq }
            ?: throw BusinessException(RescueErrorCode.RESCUE_NOT_FOUND)

    private companion object {
        const val WITHDRAWN_NAME = "(탈퇴한 사용자)"
    }
}
