package com.ssafy.animal.core.rescue.adapter.`in`.web

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.rescue.application.port.`in`.CreateRescueCommand
import com.ssafy.animal.core.rescue.application.port.`in`.FoundReportSummary
import com.ssafy.animal.core.rescue.application.port.`in`.RescueAnimal
import com.ssafy.animal.core.rescue.application.port.`in`.RescueDetail
import com.ssafy.animal.core.rescue.application.port.`in`.RescueItem
import com.ssafy.animal.core.rescue.application.port.`in`.RescuePage
import com.ssafy.animal.core.rescue.application.port.`in`.RescueSource
import com.ssafy.animal.core.rescue.application.port.`in`.UpdateRescueCommand
import com.ssafy.animal.core.rescue.domain.RescueContent
import com.ssafy.animal.core.rescue.domain.RescueReasonCode
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.Optional

// 사유 코드는 문자열로 받아 정의되지 않은 값도 400 COMMON_INVALID_INPUT으로 낸다(10.3. enum으로 받으면 COMMON_INVALID_JSON)
private const val REASON_CODES = "WANDERING|INJURY_ILLNESS|DANGEROUS_PLACE|OTHER"

private val RescueRecord.source get() = if (foundReportSeq == null) RescueSource.DIRECT else RescueSource.REPORT

/** 10.3. 직접 접수는 foundReportSeq null */
data class CreateRescueRequest(
    val foundReportSeq: Int? = null,
    @field:NotBlank @field:Pattern(regexp = REASON_CODES, message = "정의되지 않은 구조 사유 코드입니다.") val rescueReasonCode: String? = null,
    @field:NotBlank @field:Size(max = 255) val rescueAddress: String? = null,
    @field:Size(max = 200) val rescueAddressDetail: String? = null,
    @field:NotNull val rescuedDttm: OffsetDateTime? = null,
    val rescueMemo: String? = null,
) {
    fun toCommand() = CreateRescueCommand(
        foundReportSeq,
        RescueContent(RescueReasonCode.valueOf(rescueReasonCode!!), rescueAddress!!, rescueAddressDetail, rescuedDttm!!, rescueMemo),
    )
}

/** 10.4 부분 수정. 필드 미전달은 null, 명시적 null은 Optional.empty() */
data class UpdateRescueRequest(
    val rescueReasonCode: Optional<String>? = null,
    val rescueAddress: Optional<String>? = null,
    val rescueAddressDetail: Optional<String>? = null,
    val rescuedDttm: Optional<OffsetDateTime>? = null,
    val rescueMemo: Optional<String>? = null,
) {
    fun toCommand(): UpdateRescueCommand {
        val reason = rescueReasonCode?.map {
            runCatching { RescueReasonCode.valueOf(it) }.getOrElse {
                throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("rescueReasonCode", "정의되지 않은 구조 사유 코드입니다.")))
            }
        }
        return UpdateRescueCommand(reason, rescueAddress, rescueAddressDetail, rescuedDttm, rescueMemo)
    }
}

/** 10.5 */
data class ChangeRescueStatusRequest(
    @field:NotNull val status: RescueStatus? = null,
    val resultReason: String? = null,
)

data class CreatedRescueResponse(val rescueSeq: Int, val status: RescueStatus, val regDttm: OffsetDateTime?) {
    companion object {
        fun from(rescue: RescueRecord) = CreatedRescueResponse(rescue.rescueSeq!!, rescue.status, rescue.regDttm)
    }
}

data class RescueStatusResponse(val rescueSeq: Int, val status: RescueStatus, val resultReason: String?, val completedDttm: OffsetDateTime?) {
    companion object {
        fun from(rescue: RescueRecord) = RescueStatusResponse(rescue.rescueSeq!!, rescue.status, rescue.resultReason, rescue.completedDttm)
    }
}

data class RescueSummaryResponse(
    val rescueSeq: Int,
    val status: RescueStatus,
    val source: RescueSource,
    val foundReportSeq: Int?,
    val rescuedDttm: OffsetDateTime,
    val rescueReasonCode: RescueReasonCode,
    val rescueMemo: String?,
    val resultReason: String?,
    val animal: RescueAnimal?,
    val regDttm: OffsetDateTime?,
) {
    companion object {
        fun from(item: RescueItem) = with(item.rescue) {
            RescueSummaryResponse(rescueSeq!!, status, source, foundReportSeq, rescuedDttm, rescueReasonCode, rescueMemo, resultReason, item.animal, regDttm)
        }
    }
}

/** 10.1 전용 DTO: Page를 statusCounts와 함께 한 겹 감싼다(Q471) */
data class RescuePageResponse(
    val statusCounts: Map<String, Long>,
    val page: PageResponse<RescueSummaryResponse>,
) {
    companion object {
        fun from(result: RescuePage) = RescuePageResponse(
            statusCounts = linkedMapOf("total" to result.statusCounts.values.sum()) + result.statusCounts.mapKeys { it.key.name },
            page = PageResponse.of(result.page.map(RescueSummaryResponse::from)),
        )
    }
}

data class RescueResponse(
    val rescueSeq: Int,
    val shelterSeq: Int,
    val source: RescueSource,
    val foundReportSeq: Int?,
    val status: RescueStatus,
    val rescueReasonCode: RescueReasonCode,
    val rescueAddress: String,
    val rescueAddressDetail: String?,
    val rescuedDttm: OffsetDateTime,
    val rescueMemo: String?,
    val resultReason: String?,
    val regDttm: OffsetDateTime?,
    val modDttm: OffsetDateTime?,
    val completedDttm: OffsetDateTime?,
    val linkedAnimalCount: Long,
    val foundReportSummary: FoundReportSummary?,
) {
    companion object {
        fun from(detail: RescueDetail) = with(detail.rescue) {
            RescueResponse(
                rescueSeq!!, shelterSeq, source, foundReportSeq, status, rescueReasonCode, rescueAddress, rescueAddressDetail,
                rescuedDttm, rescueMemo, resultReason, regDttm, modDttm, completedDttm, detail.linkedAnimalCount, detail.foundReportSummary,
            )
        }
    }
}
