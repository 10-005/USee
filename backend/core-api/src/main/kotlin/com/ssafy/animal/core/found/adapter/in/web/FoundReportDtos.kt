package com.ssafy.animal.core.found.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.found.application.port.`in`.ActorSummary
import com.ssafy.animal.core.found.application.port.`in`.AssignedFoundReportItem
import com.ssafy.animal.core.found.application.port.`in`.FoundReportDetail
import com.ssafy.animal.core.found.application.port.`in`.FoundReportItem
import com.ssafy.animal.core.found.application.port.`in`.ImageRef
import com.ssafy.animal.core.found.application.port.`in`.RegisterFoundReportCommand
import com.ssafy.animal.core.found.application.port.`in`.ReporterContact
import com.ssafy.animal.core.found.application.port.`in`.RescueProgress
import com.ssafy.animal.core.found.application.port.`in`.UpdateFoundReportCommand
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundReportContent
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.found.domain.Gender
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.Optional

/** 담당 보호소는 서버가 정한다. shelterSeq를 보내면 무시하지 않고 400(8.4·8.5) */
private fun rejectShelterSeq(shelterSeq: Optional<Any>?) {
    if (shelterSeq != null) {
        throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("shelterSeq", "담당 보호소는 지정할 수 없습니다.")))
    }
}

/** 8.4 */
data class RegisterFoundReportRequest(
    @field:NotNull val combinationSeq: Int? = null,
    @field:NotNull val gender: Gender? = null,
    @field:Size(max = 30) val age: String? = null,
    @field:Size(max = 30) val weight: String? = null,
    val isNeutered: Boolean? = null,
    @field:Size(max = 30) val microchipNumber: String? = null,
    val description: String? = null,
    @field:NotBlank @field:Size(max = 30) val reporterPhone: String? = null,
    val isPhonePublic: Boolean? = null,
    val landmarkDescription: String? = null,
    @field:NotBlank @field:Size(max = 255) val address: String? = null,
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: Double? = null,
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: Double? = null,
    @field:NotNull val occurredDttm: OffsetDateTime? = null,
    @field:NotNull val imageSeqs: List<Long>? = null,
    val shelterSeq: Optional<Any>? = null,
) {
    fun toCommand(): RegisterFoundReportCommand {
        rejectShelterSeq(shelterSeq)
        return RegisterFoundReportCommand(
            FoundReportContent(
                combinationSeq!!, gender!!, age, weight, isNeutered, microchipNumber, description, reporterPhone!!,
                isPhonePublic ?: false, landmarkDescription, address!!, latitude!!, longitude!!, occurredDttm!!,
            ),
            imageSeqs!!,
        )
    }
}

/** 8.5 부분 수정. 필드 미전달은 null, 명시적 null은 Optional.empty() */
data class UpdateFoundReportRequest(
    val combinationSeq: Optional<Int>? = null,
    val gender: Optional<Gender>? = null,
    val age: Optional<String>? = null,
    val weight: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val microchipNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val reporterPhone: Optional<String>? = null,
    val isPhonePublic: Optional<Boolean>? = null,
    val landmarkDescription: Optional<String>? = null,
    val address: Optional<String>? = null,
    val latitude: Optional<Double>? = null,
    val longitude: Optional<Double>? = null,
    val occurredDttm: Optional<OffsetDateTime>? = null,
    val imageSeqs: Optional<List<Long>>? = null,
    val shelterSeq: Optional<Any>? = null,
) {
    fun toCommand(): UpdateFoundReportCommand {
        rejectShelterSeq(shelterSeq)
        return UpdateFoundReportCommand(
            combinationSeq, gender, age, weight, isNeutered, microchipNumber, description, reporterPhone,
            isPhonePublic, landmarkDescription, address, latitude, longitude, occurredDttm, imageSeqs,
        )
    }
}

/** 8.6 */
data class ChangeFoundStatusRequest(
    @field:NotNull val status: FoundStatus? = null,
    val closedReason: String? = null,
)

data class RegisteredFoundReportResponse(
    val foundReportSeq: Int,
    val status: FoundStatus,
    val districtCode: String?,
    val assignedShelterSeq: Int,
    val assignmentMethod: String,
)

data class FoundStatusResponse(
    val foundReportSeq: Int,
    val status: FoundStatus,
    val closedReason: String?,
    val closedDttm: OffsetDateTime?,
) {
    companion object {
        fun from(report: FoundReport) = FoundStatusResponse(report.foundReportSeq!!, report.status, report.closedReason, report.closedDttm)
    }
}

/** 8.1·8.3 목록 항목(7.1과 같은 구성 + shelterSeq) */
data class FoundReportSummaryResponse(
    val foundReportSeq: Int,
    val classification: AnimalClassification?,
    val gender: Gender,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val status: FoundStatus,
    val shelterSeq: Int,
    val address: String,
    val districtCode: String?,
    val occurredDttm: OffsetDateTime,
    val representativeImageUrl: String?,
    val regDttm: OffsetDateTime?,
) {
    companion object {
        fun from(item: FoundReportItem) = with(item.report) {
            FoundReportSummaryResponse(
                foundReportSeq!!, item.classification, gender, age, isNeutered, weight, status, shelterSeq,
                address, districtCode, occurredDttm, item.representativeImageUrl, regDttm,
            )
        }
    }
}

data class ShelterRef(val shelterSeq: Int, val shelterName: String?)

/** 8.2·8.5 상세 = 목록 필드 + 상세 필드 */
data class FoundReportResponse(
    @get:JsonUnwrapped val summary: FoundReportSummaryResponse,
    val reporter: ActorSummary,
    val shelter: ShelterRef,
    val microchipNumber: String?,
    val description: String?,
    val landmarkDescription: String?,
    val latitude: Double,
    val longitude: Double,
    val reporterPhone: String?,
    val isPhonePublic: Boolean,
    val closedReason: String?,
    val closedDttm: OffsetDateTime?,
    val images: List<ImageRef>,
    val isBookmarked: Boolean,
    val commentCount: Long,
) {
    companion object {
        fun from(detail: FoundReportDetail) = with(detail.item.report) {
            FoundReportResponse(
                FoundReportSummaryResponse.from(detail.item), detail.reporter, ShelterRef(shelterSeq, detail.shelterName),
                microchipNumber, description, landmarkDescription, latitude, longitude, detail.reporterPhone, isPhonePublic,
                closedReason, closedDttm, detail.images, detail.isBookmarked, detail.commentCount,
            )
        }
    }
}

/** 8.8 = 목록 필드 + 신고자 실명·연락처 + 구조 상태 */
data class AssignedFoundReportResponse(
    @get:JsonUnwrapped val summary: FoundReportSummaryResponse,
    val reporter: ReporterContact,
    val rescueStatus: RescueProgress,
    val activeRescueSeq: Int?,
) {
    companion object {
        fun from(item: AssignedFoundReportItem) =
            AssignedFoundReportResponse(FoundReportSummaryResponse.from(item.item), item.reporter, item.rescueStatus, item.activeRescueSeq)
    }
}
