package com.ssafy.animal.core.rescue.application.port.`in`

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.found.application.port.`in`.RecentPeriod
import com.ssafy.animal.core.found.application.port.`in`.ReporterContact
import com.ssafy.animal.core.rescue.domain.RescueContent
import com.ssafy.animal.core.rescue.domain.RescueReasonCode
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime
import java.util.Optional

/** 10장은 모두 SHELTER 전용이고 범위는 토큰 shelterSeq. 다른 보호소의 구조는 404 RESCUE_NOT_FOUND로 감춘다(Q434) */
interface RescueUseCase {
    /** 10.1 목록 + 상태별 건수(status 외 필터만 적용) */
    fun getPage(shelterSeq: Long?, filter: RescueFilter, pageable: Pageable): RescuePage

    /** 10.2 */
    fun get(shelterSeq: Long?, rescueSeq: Long): RescueDetail

    /** 10.3 PROCESSING으로 접수 */
    fun create(user: AuthUser, command: CreateRescueCommand): RescueRecord

    /** 10.4 부분 수정. 종료 상태는 rescueMemo만 */
    fun update(user: AuthUser, rescueSeq: Long, command: UpdateRescueCommand): RescueDetail

    /** 10.5 PROCESSING → DONE/FAILED/CANCELED */
    fun changeStatus(user: AuthUser, rescueSeq: Long, status: RescueStatus, resultReason: String?): RescueRecord

    /** 10.6 논리 삭제. 연결 보호동물이 있으면 409 RESCUE_HAS_ANIMALS */
    fun delete(user: AuthUser, rescueSeq: Long)
}

/** 접수 경로: REPORT = 발견 신고 연계, DIRECT = 직접 접수 */
enum class RescueSource { REPORT, DIRECT }

data class RescueFilter(
    val status: RescueStatus? = null,
    val period: RecentPeriod? = null,
    val speciesSeq: Int? = null,
    val source: RescueSource? = null,
    val foundReportSeq: Int? = null,
)

data class CreateRescueCommand(val foundReportSeq: Int?, val content: RescueContent)

/**
 * 10.4. 필드별 null = 미전달(유지), Optional.empty() = 명시적 null.
 * 필수 필드(사유·장소·구조 일시)는 비울 수 없다
 */
data class UpdateRescueCommand(
    val rescueReasonCode: Optional<RescueReasonCode>? = null,
    val rescueAddress: Optional<String>? = null,
    val rescueAddressDetail: Optional<String>? = null,
    val rescuedDttm: Optional<OffsetDateTime>? = null,
    val rescueMemo: Optional<String>? = null,
) {
    /** 메모 말고 보낸 필드 이름 */
    val nonMemoFields: List<String>
        get() = listOfNotNull(
            "rescueReasonCode".takeIf { rescueReasonCode != null },
            "rescueAddress".takeIf { rescueAddress != null },
            "rescueAddressDetail".takeIf { rescueAddressDetail != null },
            "rescuedDttm".takeIf { rescuedDttm != null },
        )

    fun applyTo(c: RescueContent): RescueContent {
        val errors = mutableListOf<ErrorDetail>()
        if (rescueReasonCode?.isEmpty == true) errors += ErrorDetail("rescueReasonCode", "필수 입력값입니다.")
        if (rescueAddress != null && rescueAddress.map { it.isBlank() }.orElse(true)) errors += ErrorDetail("rescueAddress", "필수 입력값입니다.")
        if (rescuedDttm?.isEmpty == true) errors += ErrorDetail("rescuedDttm", "필수 입력값입니다.")
        if ((rescueAddress?.orElse(null)?.length ?: 0) > 255) errors += ErrorDetail("rescueAddress", "255자 이하입니다.")
        if ((rescueAddressDetail?.orElse(null)?.length ?: 0) > 200) errors += ErrorDetail("rescueAddressDetail", "200자 이하입니다.")
        if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = errors)
        return RescueContent(
            rescueReasonCode = rescueReasonCode?.get() ?: c.rescueReasonCode,
            rescueAddress = rescueAddress?.get() ?: c.rescueAddress,
            rescueAddressDetail = if (rescueAddressDetail == null) c.rescueAddressDetail else rescueAddressDetail.orElse(null),
            rescuedDttm = rescuedDttm?.get() ?: c.rescuedDttm,
            rescueMemo = if (rescueMemo == null) c.rescueMemo else rescueMemo.orElse(null),
        )
    }
}

/** 10.1 목록 항목의 동물 정보. REPORT는 발견 신고, DIRECT는 연결 보호동물 중 가장 먼저 등록된 1마리. 없으면 null */
data class RescueAnimal(
    val speciesName: String?,
    val breedName: String?,
    val age: String?,
    val gender: String,
    val isNeutered: Boolean?,
    val colorName: String?,
    val weight: String?,
    val representativeImageUrl: String?,
)

data class RescueItem(val rescue: RescueRecord, val animal: RescueAnimal?)

/** statusCounts: total + 상태 4종 */
data class RescuePage(val statusCounts: Map<RescueStatus, Long>, val page: Page<RescueItem>)

/** 10.2 foundReportSummary. 신고가 삭제되었어도 남은 정보로 채운다 */
data class FoundReportSummary(
    val foundReportSeq: Int,
    val classification: AnimalClassification?,
    val age: String?,
    val gender: String,
    val isNeutered: Boolean?,
    val weight: String?,
    val address: String,
    val occurredDttm: OffsetDateTime,
    val representativeImageUrl: String?,
    val reporter: ReporterContact,
    val status: String,
)

data class RescueDetail(
    val rescue: RescueRecord,
    val linkedAnimalCount: Long,
    val foundReportSummary: FoundReportSummary?,
)
