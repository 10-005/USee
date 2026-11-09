package com.ssafy.animal.core.found.application.service

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.found.application.port.`in`.ActorSummary
import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.AssignedFoundReportItem
import com.ssafy.animal.core.found.application.port.`in`.AssignmentMethod
import com.ssafy.animal.core.found.application.port.`in`.FoundReportDetail
import com.ssafy.animal.core.found.application.port.`in`.FoundReportItem
import com.ssafy.animal.core.found.application.port.`in`.FoundReportUseCase
import com.ssafy.animal.core.found.application.port.`in`.FoundSearchFilter
import com.ssafy.animal.core.found.application.port.`in`.ImageRef
import com.ssafy.animal.core.found.application.port.`in`.RegisterFoundReportCommand
import com.ssafy.animal.core.found.application.port.`in`.RegisteredFoundReport
import com.ssafy.animal.core.found.application.port.`in`.ReporterContact
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.application.port.`in`.UpdateFoundReportCommand
import com.ssafy.animal.core.found.application.port.out.FoundReportRepository
import com.ssafy.animal.core.found.domain.FoundErrorCode
import com.ssafy.animal.core.found.domain.FoundImageErrorCode
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionOperations
import java.time.OffsetDateTime

/**
 * 등록·수정은 역지오코딩(외부 호출)을 트랜잭션 밖에서 먼저 하고 쓰기만 transactionOperations로 묶는다(21.1 2단계).
 * 이벤트는 그 트랜잭션 안에서 publish해 커밋 후에만 나간다.
 */
@Service
class FoundReportService(
    private val foundReportRepository: FoundReportRepository,
    private val catalogUseCase: CatalogUseCase,
    private val geocodeUseCase: GeocodeUseCase,
    private val imageUseCase: ImageUseCase,
    private val eventPublisher: EventPublisher,
    private val transactionOperations: TransactionOperations,
) : FoundReportUseCase {

    @Transactional(readOnly = true)
    override fun search(filter: FoundSearchFilter, pageable: Pageable): Page<FoundReportItem> {
        validateDistrict(filter.districtCode)
        if (filter.occurredFrom != null && filter.occurredTo != null && filter.occurredFrom > filter.occurredTo) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("occurredFrom", "시작일이 종료일보다 늦습니다.")))
        }
        val page = foundReportRepository.search(filter.copy(keyword = filter.keyword?.trim()?.ifEmpty { null }), pageable)
        return page.map(items(page.content)::getValue)
    }

    @Transactional(readOnly = true)
    override fun get(viewer: AuthUser, foundReportSeq: Long): FoundReportDetail =
        detail(foundReportRepository.findVisible(Math.toIntExact(foundReportSeq)) ?: throw notFound(), viewer)

    @Transactional(readOnly = true)
    override fun getMine(userSeq: Long): List<FoundReportItem> {
        val reports = foundReportRepository.findMine(Math.toIntExact(userSeq))
        return reports.map(items(reports)::getValue)
    }

    override fun register(userSeq: Long, command: RegisterFoundReportCommand): RegisteredFoundReport {
        val content = command.content
        val classification = catalogUseCase.requireClassification(content.combinationSeq)
        validateImages(userSeq, command.imageSeqs, alreadyLinked = emptySet())
        val districtCode = geocodeUseCase.resolveDistrictCode(content.latitude, content.longitude)

        return transactionOperations.execute {
            // ① 시군구 지정 보호소 → ② 없으면 하버사인 최근접(Q406 A·Q406-2 A). 둘 다 없으면 시드 전 환경
            val districtShelter = districtCode?.let(foundReportRepository::findDistrictShelterSeq)
            val (shelterSeq, method) = when {
                districtShelter != null -> districtShelter to AssignmentMethod.DISTRICT
                else -> (
                    foundReportRepository.findNearestShelterSeq(content.latitude, content.longitude)
                        ?: throw BusinessException(FoundErrorCode.FOUND_SHELTER_ASSIGNMENT_UNAVAILABLE)
                    ) to AssignmentMethod.NEAREST
            }
            val report = foundReportRepository.saveAndFlush(FoundReport(Math.toIntExact(userSeq), shelterSeq, content, districtCode))
            val seq = report.foundReportSeq!!
            foundReportRepository.replaceImages(seq, command.imageSeqs)

            foundReportRepository.findShelterUserSeq(shelterSeq)?.let {
                eventPublisher.publish(
                    NotificationRequested(it.toLong(), NotificationType.SHELTER, SHELTER_MESSAGE, TargetType.FOUND, seq.toLong()),
                )
            }
            publishMatch(MatchEventType.FOUND_REPORT_REGISTERED, report, command.imageSeqs, classification)
            RegisteredFoundReport(seq, report.status, districtCode, shelterSeq, method)
        }!!
    }

    override fun update(viewer: AuthUser, foundReportSeq: Long, command: UpdateFoundReportCommand): FoundReportDetail {
        val seq = Math.toIntExact(foundReportSeq)
        val current = loadEditable(viewer.userSeq, seq)
        val content = command.applyTo(current.content)
        val classification = catalogUseCase.requireClassification(content.combinationSeq)
        val oldImageSeqs = foundReportRepository.findImages(listOf(seq))[seq].orEmpty().map { it.imageSeq }
        val newImageSeqs = command.imageSeqs?.get()
        newImageSeqs?.let { validateImages(viewer.userSeq, it, alreadyLinked = oldImageSeqs.toSet()) }

        val c = current.content
        val locationChanged = content.latitude != c.latitude || content.longitude != c.longitude || content.address != c.address
        val districtCode = if (locationChanged) geocodeUseCase.resolveDistrictCode(content.latitude, content.longitude) else current.districtCode

        return transactionOperations.execute {
            // 밖에서 읽은 뒤 상태가 바뀌었을 수 있어 트랜잭션 안에서 다시 확인한다
            val report = loadEditable(viewer.userSeq, seq)
            // 소속 보호소는 위치가 바뀌어도 자동 재배정하지 않는다(8.5)
            report.update(content, districtCode, Math.toIntExact(viewer.userSeq))
            foundReportRepository.saveAndFlush(report)
            if (newImageSeqs != null && newImageSeqs != oldImageSeqs) {
                foundReportRepository.replaceImages(seq, newImageSeqs)
                (oldImageSeqs - newImageSeqs.toSet()).forEach(imageUseCase::deleteIfUnreferenced)
                // 순서만 바뀐 것은 AI 재매칭 대상이 아니다
                if (newImageSeqs.toSet() != oldImageSeqs.toSet()) publishMatch(MatchEventType.IMAGES_CHANGED, report, newImageSeqs, classification)
            }
            detail(report, viewer)
        }!!
    }

    @Transactional
    override fun changeStatus(viewer: AuthUser, foundReportSeq: Long, status: FoundStatus, closedReason: String?): FoundReport {
        val report = foundReportRepository.findActive(Math.toIntExact(foundReportSeq)) ?: throw notFound()
        val isAuthor = viewer.role == Role.USER && report.userSeq.toLong() == viewer.userSeq
        val isAssignedShelter = viewer.role == Role.SHELTER && report.shelterSeq.toLong() == viewer.shelterSeq
        if (!isAuthor && !isAssignedShelter) throw notFound()
        // 담당 보호소는 종료만(Q421). 재개는 역할상 허용되지 않는 동작이라 403
        if (!isAuthor && status == FoundStatus.ACTIVE) throw BusinessException(CommonErrorCode.AUTH_FORBIDDEN)
        if (report.status == status) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("status", "이미 ${status.name} 상태입니다.")))
        }
        report.changeStatus(status, closedReason?.trim()?.ifEmpty { null }, OffsetDateTime.now(), Math.toIntExact(viewer.userSeq))
        return foundReportRepository.saveAndFlush(report)
    }

    @Transactional
    override fun delete(userSeq: Long, foundReportSeq: Long) {
        val report = loadOwned(userSeq, Math.toIntExact(foundReportSeq))
        report.delete(Math.toIntExact(userSeq))
        foundReportRepository.saveAndFlush(report)
    }

    @Transactional(readOnly = true)
    override fun getAssigned(shelterSeq: Long?, filter: AssignedFilter, pageable: Pageable): Page<AssignedFoundReportItem> {
        validateDistrict(filter.districtCode)
        val page = foundReportRepository.findAssigned(Math.toIntExact(requireNotNull(shelterSeq) { "SHELTER 토큰에 shelterSeq 없음" }), filter, pageable)
        val items = items(page.content)
        val reporters = foundReportRepository.findReporters(page.content.map { it.userSeq })
        val rescues = foundReportRepository.findLatestRescues(page.content.map { it.foundReportSeq!! })
        return page.map { report ->
            val rescue = rescues[report.foundReportSeq]
            val progress = when (rescue?.status) {
                null -> RescueProgress.NONE
                "PROCESSING" -> RescueProgress.PROCESSING
                "DONE" -> RescueProgress.DONE
                else -> RescueProgress.ABORTED
            }
            val row = reporters[report.userSeq]
            AssignedFoundReportItem(
                item = items.getValue(report),
                reporter = ReporterContact(if (row == null || row.isDeleted) WITHDRAWN_NAME else row.name, report.reporterPhone),
                rescueStatus = progress,
                // 발견 신고당 비삭제 PROCESSING/DONE은 1건뿐이고, 있으면 그게 최신이다(그 뒤로 새 구조를 만들 수 없음)
                activeRescueSeq = rescue?.rescueSeq?.takeIf { progress == RescueProgress.PROCESSING || progress == RescueProgress.DONE },
            )
        }
    }

    /** 목록 항목: 분류는 한 번에, 대표 사진은 display_order가 가장 앞선 사진 */
    private fun items(reports: List<FoundReport>): Map<FoundReport, FoundReportItem> {
        val classifications = catalogUseCase.findClassifications(reports.map { it.combinationSeq })
        val images = foundReportRepository.findImages(reports.map { it.foundReportSeq!! })
        // ponytail: 대표 사진 URL을 항목마다 findSummary로 읽는다(페이지 크기만큼 PK 조회). 느려지면 image에 일괄 조회 포트를 요청
        return reports.associateWith { report ->
            val first = images[report.foundReportSeq].orEmpty().firstOrNull()
            FoundReportItem(report, classifications[report.combinationSeq], first?.let { imageUseCase.findSummary(it.imageSeq)?.imageUrl })
        }
    }

    private fun detail(report: FoundReport, viewer: AuthUser): FoundReportDetail {
        val seq = report.foundReportSeq!!
        val images = foundReportRepository.findImages(listOf(seq))[seq].orEmpty()
            .map { ImageRef(it.imageSeq, imageUseCase.findSummary(it.imageSeq)?.imageUrl, it.displayOrder.toInt()) }
        val reporter = foundReportRepository.findReporters(listOf(report.userSeq))[report.userSeq]
        val isAuthor = report.userSeq.toLong() == viewer.userSeq
        val isAssignedShelter = viewer.role == Role.SHELTER && report.shelterSeq.toLong() == viewer.shelterSeq
        return FoundReportDetail(
            item = FoundReportItem(
                report,
                catalogUseCase.findClassifications(listOf(report.combinationSeq))[report.combinationSeq],
                images.firstOrNull()?.imageUrl,
            ),
            reporter = ActorSummary(
                userSeq = report.userSeq.toLong(),
                displayName = if (reporter == null || reporter.isDeleted) WITHDRAWN_NAME else reporter.nickname,
                role = Role.USER,
                profileImage = reporter?.takeUnless { it.isDeleted }?.profileImageSeq?.let(imageUseCase::findSummary),
            ),
            shelterName = foundReportRepository.findShelterName(report.shelterSeq),
            // 비공개 연락처는 작성자·이 신고의 담당 보호소만(8.2)
            reporterPhone = report.reporterPhone.takeIf { report.isPhonePublic || isAuthor || isAssignedShelter },
            images = images,
            isBookmarked = viewer.role == Role.USER && foundReportRepository.isBookmarked(Math.toIntExact(viewer.userSeq), seq),
            commentCount = foundReportRepository.countComments(seq),
        )
    }

    /** 사진 1~3장, 중복 없음, 새로 연결하는 사진은 본인 업로드만(Q407) */
    private fun validateImages(userSeq: Long, imageSeqs: List<Long>, alreadyLinked: Set<Long>) {
        if (imageSeqs.size !in 1..MAX_IMAGES) throw BusinessException(FoundImageErrorCode.IMAGE_INVALID_COUNT)
        if (imageSeqs.toSet().size != imageSeqs.size) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("imageSeqs", "같은 사진이 중복되었습니다.")))
        }
        imageSeqs.filter { it !in alreadyLinked }.forEach { imageUseCase.requireOwned(userSeq, it) }
    }

    private fun publishMatch(type: MatchEventType, report: FoundReport, imageSeqs: List<Long>, classification: AnimalClassification) =
        eventPublisher.publish(
            MatchRequested(
                eventType = type,
                sourceType = TargetType.FOUND,
                sourceSeq = report.foundReportSeq!!.toLong(),
                imageKeys = foundReportRepository.findImageKeys(imageSeqs),
                speciesCode = classification.speciesCode.name,
                occurredAt = report.occurredDttm,
            ),
        )

    private fun validateDistrict(districtCode: String?) {
        districtCode?.let(catalogUseCase::requireDistrict)
    }

    /** 작성자 본인의 삭제되지 않은 신고. 남의 신고도 404로 감춘다(Q434) */
    private fun loadOwned(userSeq: Long, seq: Int): FoundReport =
        foundReportRepository.findActive(seq)?.takeIf { it.userSeq.toLong() == userSeq } ?: throw notFound()

    private fun loadEditable(userSeq: Long, seq: Int): FoundReport =
        loadOwned(userSeq, seq).also { if (it.status == FoundStatus.CLOSED) throw BusinessException(FoundErrorCode.FOUND_REPORT_CLOSED) }

    private fun notFound() = BusinessException(FoundErrorCode.FOUND_REPORT_NOT_FOUND)

    private companion object {
        const val MAX_IMAGES = 3
        const val WITHDRAWN_NAME = "(탈퇴한 사용자)"
        // ponytail: 알림 본문 문구는 가안. 확정되면 여기만 바꾼다
        const val SHELTER_MESSAGE = "담당 지역에 새 발견 신고가 등록되었습니다."
    }
}
