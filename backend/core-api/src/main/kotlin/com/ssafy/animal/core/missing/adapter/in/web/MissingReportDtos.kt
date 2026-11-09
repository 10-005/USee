package com.ssafy.animal.core.missing.adapter.`in`.web

import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.missing.application.port.`in`.CreateMissingCommand
import com.ssafy.animal.core.missing.application.port.`in`.CreatedMissing
import com.ssafy.animal.core.missing.application.port.`in`.MissingAuthor
import com.ssafy.animal.core.missing.application.port.`in`.MissingDetail
import com.ssafy.animal.core.missing.application.port.`in`.MissingImageRef
import com.ssafy.animal.core.missing.application.port.`in`.MissingSummary
import com.ssafy.animal.core.missing.application.port.`in`.UpdateMissingCommand
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingReport
import com.ssafy.animal.core.missing.domain.MissingStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional

/** 7.4. 길이·좌표 범위는 수정과 같은 규칙이라 서비스가 검증한다 */
data class CreateMissingRequest(
    @field:NotNull
    val combinationSeq: Int?,
    @field:NotNull
    val gender: MissingGender?,
    val age: String? = null,
    val weight: String? = null,
    val isNeutered: Boolean? = null,
    val microchipNumber: String? = null,
    val description: String? = null,
    @field:NotBlank
    val address: String?,
    @field:NotNull
    val latitude: Double?,
    @field:NotNull
    val longitude: Double?,
    @field:NotNull
    val occurredDttm: OffsetDateTime?,
    @field:NotBlank
    val contactPhone: String?,
    val isPhonePublic: Boolean? = null,
    @field:NotNull
    val imageSeqs: List<Long>?,
) {
    fun toCommand() = CreateMissingCommand(
        combinationSeq!!, gender!!, age, weight, isNeutered, microchipNumber, description,
        address!!, latitude!!, longitude!!, occurredDttm!!, contactPhone!!, isPhonePublic ?: false, imageSeqs!!,
    )
}

/** 7.5 부분 수정. 선택 필드는 미전달 null, 명시적 null은 Optional.empty() */
data class UpdateMissingRequest(
    val combinationSeq: Int? = null,
    val gender: MissingGender? = null,
    val age: Optional<String>? = null,
    val weight: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val microchipNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val occurredDttm: OffsetDateTime? = null,
    val contactPhone: String? = null,
    val isPhonePublic: Boolean? = null,
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = UpdateMissingCommand(
        combinationSeq, gender, age, weight, isNeutered, microchipNumber, description,
        address, latitude, longitude, occurredDttm, contactPhone, isPhonePublic, imageSeqs,
    )
}

data class ChangeMissingStatusRequest(
    @field:NotNull
    val status: MissingStatus?,
)

data class CreatedMissingResponse(val missingReportSeq: Int, val status: MissingStatus, val districtCode: String?, val aiMatchingRequested: Boolean) {
    companion object {
        fun from(created: CreatedMissing) =
            CreatedMissingResponse(created.missingReportSeq, created.status, created.districtCode, created.aiMatchingRequested)
    }
}

data class MissingStatusResponse(val missingReportSeq: Int, val status: MissingStatus) {
    companion object {
        fun from(report: MissingReport) = MissingStatusResponse(report.missingReportSeq!!, report.status)
    }
}

/** 7.1·7.3 목록 항목 */
data class MissingSummaryResponse(
    val missingReportSeq: Int,
    val classification: AnimalClassification?,
    val gender: MissingGender,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val status: MissingStatus,
    val address: String,
    val districtCode: String?,
    val occurredDttm: OffsetDateTime,
    val representativeImageUrl: String?,
    val regDttm: OffsetDateTime?,
) {
    companion object {
        fun from(summary: MissingSummary) = with(summary.report) {
            MissingSummaryResponse(
                missingReportSeq!!, summary.classification, gender, age, isNeutered, weight, status,
                address, districtCode, occurredDttm, summary.representativeImageUrl, regDttm,
            )
        }
    }
}

/** 7.2 상세 = 목록 필드 + 상세 필드 */
data class MissingDetailResponse(
    val missingReportSeq: Int,
    val classification: AnimalClassification?,
    val gender: MissingGender,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val status: MissingStatus,
    val address: String,
    val districtCode: String?,
    val occurredDttm: OffsetDateTime,
    val representativeImageUrl: String?,
    val regDttm: OffsetDateTime?,
    val user: MissingAuthor?,
    val microchipNumber: String?,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val images: List<MissingImageRef>,
    val isPhonePublic: Boolean,
    val contactPhone: String?,
    val isBookmarked: Boolean,
    val commentCount: Long,
    val modDttm: OffsetDateTime?,
) {
    companion object {
        fun from(detail: MissingDetail) = with(detail.report) {
            MissingDetailResponse(
                missingReportSeq!!, detail.classification, gender, age, isNeutered, weight, status,
                address, districtCode, occurredDttm, detail.images.firstOrNull()?.imageUrl, regDttm,
                detail.author, microchipNumber, description, latitude, longitude, detail.images,
                isPhonePublic, detail.contactPhone, detail.isBookmarked, detail.commentCount, modDttm,
            )
        }
    }
}
