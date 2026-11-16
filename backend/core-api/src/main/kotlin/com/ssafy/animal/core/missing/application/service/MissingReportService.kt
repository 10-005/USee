package com.ssafy.animal.core.missing.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.MatchEventType
import com.ssafy.animal.common.event.MatchRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.match.application.port.`in`.MatchCandidateUseCase
import com.ssafy.animal.core.missing.application.port.`in`.CreateMissingCommand
import com.ssafy.animal.core.missing.application.port.`in`.CreatedMissing
import com.ssafy.animal.core.missing.application.port.`in`.MissingAuthor
import com.ssafy.animal.core.missing.application.port.`in`.MissingDetail
import com.ssafy.animal.core.missing.application.port.`in`.MissingImageRef
import com.ssafy.animal.core.missing.application.port.`in`.MissingReportUseCase
import com.ssafy.animal.core.missing.application.port.`in`.MissingSearchFilter
import com.ssafy.animal.core.missing.application.port.`in`.MissingSummary
import com.ssafy.animal.core.missing.application.port.`in`.UpdateMissingCommand
import com.ssafy.animal.core.missing.application.port.out.MissingReportRepository
import com.ssafy.animal.core.missing.domain.MissingErrorCode
import com.ssafy.animal.core.missing.domain.MissingReport
import com.ssafy.animal.core.missing.domain.MissingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import com.ssafy.animal.core.image.application.port.`in`.ordered

/**
 * 등록·수정은 역지오코딩(외부 호출)을 트랜잭션 밖에서 먼저 하고(명세 21.1 2단계) 쓰기만 TransactionTemplate으로 묶는다.
 * 매칭 이벤트는 그 트랜잭션 안에서 publish하므로 커밋 후에만 나간다.
 */
@Service
class MissingReportService(
    private val missingReportRepository: MissingReportRepository,
    private val catalogUseCase: CatalogUseCase,
    private val geocodeUseCase: GeocodeUseCase,
    private val imageUseCase: ImageUseCase,
    private val eventPublisher: EventPublisher,
    private val matchCandidates: MatchCandidateUseCase,
    transactionManager: PlatformTransactionManager,
) : MissingReportUseCase {

    private val tx = TransactionTemplate(transactionManager)
    private val readTx = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun search(filter: MissingSearchFilter, pageable: Pageable): Page<MissingSummary> = read {
        if (filter.occurredFrom != null && filter.occurredTo != null && filter.occurredFrom > filter.occurredTo) {
            throw invalid("occurredFrom", "시작일이 종료일보다 늦습니다.")
        }
        filter.districtCode?.let(catalogUseCase::requireDistrict)
        val page = missingReportRepository.search(filter, pageable)
        PageImpl(summaries(page.content), page.pageable, page.totalElements)
    }

    override fun get(viewerUserSeq: Long, missingReportSeq: Long): MissingDetail = read {
        val report = missingReportRepository.findVisible(Math.toIntExact(missingReportSeq)) ?: throw notFound()
        detail(viewerUserSeq, report)
    }

    override fun getMine(userSeq: Long): List<MissingSummary> = read {
        summaries(missingReportRepository.findMine(Math.toIntExact(userSeq)))
    }

    override fun create(userSeq: Long, command: CreateMissingCommand): CreatedMissing {
        validateCoordinate(command.latitude, command.longitude)
        val districtCode = geocodeUseCase.resolveDistrictCode(command.latitude, command.longitude)
        return write {
            val classification = catalogUseCase.requireClassification(command.combinationSeq)
            requireImages(userSeq, command.imageSeqs)
            val seq = Math.toIntExact(userSeq)
            val report = with(command) {
                MissingReport(
                    seq, combinationSeq, gender, age, weight, isNeutered, microchipNumber, description,
                    address.trim(), latitude, longitude, occurredDttm, districtCode, contactPhone.trim(), isPhonePublic,
                )
            }
            validate(report)
            missingReportRepository.saveAndFlush(report)
            missingReportRepository.replaceImages(report.missingReportSeq!!, command.imageSeqs)
            requestMatch(MatchEventType.MISSING_REPORT_REGISTERED, report, classification.speciesCode.name, command.imageSeqs)
            CreatedMissing(report.missingReportSeq!!, report.status, report.districtCode, aiMatchingRequested = true)
        }
    }

    override fun update(userSeq: Long, missingReportSeq: Long, command: UpdateMissingCommand): MissingDetail {
        // 좌표는 둘을 함께 받는다. 바뀌면 districtCode 재도출(실패면 null, Q406-2)
        if ((command.latitude == null) != (command.longitude == null)) {
            throw invalid("latitude", "latitude와 longitude는 함께 보내야 합니다.")
        }
        val newDistrict = if (command.latitude != null && command.longitude != null) {
            validateCoordinate(command.latitude, command.longitude)
            Optional.ofNullable(geocodeUseCase.resolveDistrictCode(command.latitude, command.longitude))
        } else {
            null
        }
        return write {
            val report = loadOwned(userSeq, missingReportSeq)
            if (report.status == MissingStatus.CLOSED) throw BusinessException(MissingErrorCode.MISSING_REPORT_CLOSED)
            command.combinationSeq?.let {
                catalogUseCase.requireClassification(it)
                report.combinationSeq = it
            }
            command.gender?.let { report.gender = it }
            report.age = command.age.orKeep(report.age)
            report.weight = command.weight.orKeep(report.weight)
            report.isNeutered = command.isNeutered.orKeep(report.isNeutered)
            report.microchipNumber = command.microchipNumber.orKeep(report.microchipNumber)
            report.description = command.description.orKeep(report.description)
            command.address?.let { report.address = it.trim() }
            command.occurredDttm?.let { report.occurredDttm = it }
            command.contactPhone?.let { report.contactPhone = it.trim() }
            command.isPhonePublic?.let { report.isPhonePublic = it }
            if (newDistrict != null) {
                report.latitude = command.latitude!!
                report.longitude = command.longitude!!
                report.districtCode = newDistrict.orElse(null)
            }
            validate(report)

            command.imageSeqs?.let { newSeqs ->
                requireImages(userSeq, newSeqs)
                val oldSeqs = missingReportRepository.findImageSeqs(report.missingReportSeq!!)
                if (oldSeqs != newSeqs) {
                    missingReportRepository.replaceImages(report.missingReportSeq!!, newSeqs)
                    (oldSeqs - newSeqs.toSet()).forEach(imageUseCase::deleteIfUnreferenced)
                }
                // 순서만 바뀐 것은 매칭에 영향이 없어 재매칭하지 않는다
                if (oldSeqs.toSet() != newSeqs.toSet()) {
                    val speciesCode = catalogUseCase.requireClassification(report.combinationSeq).speciesCode.name
                    requestMatch(MatchEventType.IMAGES_CHANGED, report, speciesCode, newSeqs)
                }
            }
            report.touchedBy(Math.toIntExact(userSeq))
            missingReportRepository.saveAndFlush(report)
            detail(userSeq, report)
        }
    }

    override fun changeStatus(userSeq: Long, missingReportSeq: Long, status: MissingStatus): MissingReport = write {
        val report = loadOwned(userSeq, missingReportSeq)
        if (report.status == status) throw invalid("status", "이미 ${status.name} 상태입니다.")
        val seq = Math.toIntExact(userSeq)
        report.changeStatus(status, seq)
        missingReportRepository.saveAndFlush(report)
        // 재개하면 다시 매칭 대상이 된다(7.4 "최초 등록/재개 시 AI 매칭 후보"). 이벤트 종류는 등록과 같게 보낸다
        if (status == MissingStatus.ACTIVE) {
            val speciesCode = catalogUseCase.requireClassification(report.combinationSeq).speciesCode.name
            requestMatch(MatchEventType.MISSING_REPORT_REGISTERED, report, speciesCode, missingReportRepository.findImageSeqs(report.missingReportSeq!!))
        }
        report
    }

    override fun delete(userSeq: Long, missingReportSeq: Long) = write {
        val report = loadOwned(userSeq, missingReportSeq)
        report.delete(Math.toIntExact(userSeq))
        missingReportRepository.saveAndFlush(report)
        // 7.7: 소프트 삭제여도 사진 연결은 풀고, 마지막 참조였을 때만 원본을 지운다
        val oldSeqs = missingReportRepository.findImageSeqs(report.missingReportSeq!!)
        missingReportRepository.replaceImages(report.missingReportSeq!!, emptyList())
        oldSeqs.forEach(imageUseCase::deleteIfUnreferenced)
        // 사진 없는 IMAGES_CHANGED를 보내 AI 워커가 이 신고의 벡터를 지우게 한다
        val speciesCode = catalogUseCase.requireClassification(report.combinationSeq).speciesCode.name
        eventPublisher.publish(
            MatchRequested(MatchEventType.IMAGES_CHANGED, TargetType.MISSING, report.missingReportSeq!!.toLong(), emptyList(), speciesCode, report.occurredDttm),
        )
    }

    private fun requestMatch(type: MatchEventType, report: MissingReport, speciesCode: String, imageSeqs: List<Long>) {
        eventPublisher.publish(
            MatchRequested(
                eventType = type,
                sourceType = TargetType.MISSING,
                sourceSeq = report.missingReportSeq!!.toLong(),
                imageKeys = missingReportRepository.findImageKeys(imageSeqs),
                speciesCode = speciesCode,
                occurredAt = report.occurredDttm,
                candidates = matchCandidates.findCandidates(TargetType.MISSING, report.missingReportSeq!!.toLong()),
            ),
        )
    }

    private fun loadOwned(userSeq: Long, missingReportSeq: Long): MissingReport =
        missingReportRepository.findOwned(Math.toIntExact(userSeq), Math.toIntExact(missingReportSeq)) ?: throw notFound()

    private fun summaries(reports: List<MissingReport>): List<MissingSummary> {
        val classifications = catalogUseCase.findClassifications(reports.map { it.combinationSeq })
        val representatives = missingReportRepository.findRepresentativeImageSeqs(reports.map { it.missingReportSeq!! })
        val images = imageUseCase.findSummaries(representatives.values)
        return reports.map { report ->
            val imageUrl = representatives[report.missingReportSeq]?.let { images[it] }?.imageUrl
            MissingSummary(report, classifications[report.combinationSeq], imageUrl)
        }
    }

    private fun detail(viewerUserSeq: Long, report: MissingReport): MissingDetail {
        val seq = report.missingReportSeq!!
        val imageSeqs = missingReportRepository.findImageSeqs(seq)
        val authorRow = missingReportRepository.findAuthor(report.userSeq)
        val summaries = imageUseCase.findSummaries(imageSeqs + listOfNotNull(authorRow?.profileImageSeq))
        val images = imageSeqs.mapIndexed { index, imageSeq -> MissingImageRef(imageSeq, summaries[imageSeq]?.imageUrl, index + 1) }
        val author = authorRow?.let {
            MissingAuthor(it.userSeq.toLong(), it.nickname, Role.valueOf(it.role), it.profileImageSeq?.let { p -> summaries[p] }?.ordered())
        }
        val isAuthor = report.userSeq.toLong() == viewerUserSeq
        return MissingDetail(
            report = report,
            classification = catalogUseCase.findClassifications(listOf(report.combinationSeq))[report.combinationSeq],
            images = images,
            author = author,
            // 공개면 로그인 사용자 전원, 비공개면 작성자만(7.2)
            contactPhone = report.contactPhone.takeIf { report.isPhonePublic || isAuthor },
            isBookmarked = missingReportRepository.isBookmarked(Math.toIntExact(viewerUserSeq), seq),
            commentCount = missingReportRepository.countComments(seq),
        )
    }

    /** 사진 1~3장, 중복 없음, 본인 업로드만(Q407) */
    private fun requireImages(userSeq: Long, imageSeqs: List<Long>) {
        if (imageSeqs.size !in 1..MAX_IMAGES) throw BusinessException(MissingErrorCode.IMAGE_INVALID_COUNT)
        if (imageSeqs.toSet().size != imageSeqs.size) throw invalid("imageSeqs", "같은 사진을 두 번 넣을 수 없습니다.")
        imageSeqs.forEach { imageUseCase.requireOwned(userSeq, it) }
    }

    private fun validateCoordinate(latitude: Double, longitude: Double) {
        if (latitude !in -90.0..90.0) throw invalid("latitude", "위도는 -90~90입니다.")
        if (longitude !in -180.0..180.0) throw invalid("longitude", "경도는 -180~180입니다.")
    }

    private fun validate(report: MissingReport) {
        val errors = buildList {
            if (report.address.isBlank()) add(ErrorDetail("address", "필수 입력값입니다."))
            if (report.address.length > 255) add(ErrorDetail("address", "255자 이하로 입력해 주세요."))
            if (report.contactPhone.isBlank()) add(ErrorDetail("contactPhone", "필수 입력값입니다."))
            listOf("age" to report.age, "weight" to report.weight, "microchipNumber" to report.microchipNumber, "contactPhone" to report.contactPhone)
                .filter { (_, value) -> (value?.length ?: 0) > 30 }
                .forEach { (field, _) -> add(ErrorDetail(field, "30자 이하로 입력해 주세요.")) }
        }
        if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = errors)
    }

    private fun notFound() = BusinessException(MissingErrorCode.MISSING_REPORT_NOT_FOUND)

    private fun invalid(field: String, reason: String) =
        BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, reason)))

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)

    private fun <T> write(block: () -> T): T = tx.execute { block() } as T

    private fun <T> read(block: () -> T): T = readTx.execute { block() } as T

    private companion object {
        const val MAX_IMAGES = 3
    }
}
