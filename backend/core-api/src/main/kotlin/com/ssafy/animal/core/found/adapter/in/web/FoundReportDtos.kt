package com.ssafy.animal.core.found.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.response.PageResponse
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
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.Optional

private const val GENDER_DESC = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름"
private const val STATUS_DESC = "신고 상태. ACTIVE: 진행 중, CLOSED: 종료(읽기 전용)"
private const val KEEP_OR_CLEAR = ". 생략하면 유지, null을 보내면 지운다"
private const val KEEP_REQUIRED = ". 생략하면 유지, null·빈 값은 400"

/** 담당 보호소는 서버가 정한다. shelterSeq를 보내면 무시하지 않고 400(8.4·8.5) */
private fun rejectShelterSeq(shelterSeq: Optional<Any>?) {
    if (shelterSeq != null) {
        throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("shelterSeq", "담당 보호소는 지정할 수 없습니다.")))
    }
}

/** 8.4 */
data class RegisterFoundReportRequest(
    @field:Schema(description = "종·품종·색상 조합 번호(5.4 값)", example = "88")
    @field:NotNull val combinationSeq: Int? = null,
    @field:Schema(description = GENDER_DESC, example = "UNKNOWN")
    @field:NotNull val gender: Gender? = null,
    @field:Schema(description = "추정 나이. 자유 텍스트", nullable = true, example = "2살 추정")
    @field:Size(max = 30) val age: String? = null,
    @field:Schema(description = "추정 몸무게. 자유 텍스트(서버는 숫자로 해석하지 않는다)", nullable = true, example = "약 3kg")
    @field:Size(max = 30) val weight: String? = null,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null이면 모름", nullable = true, example = "null")
    val isNeutered: Boolean? = null,
    @field:Schema(description = "마이크로칩 번호", nullable = true, example = "410100012345678")
    @field:Size(max = 30) val microchipNumber: String? = null,
    @field:Schema(description = "특징 설명. 목록 keyword 검색 대상", nullable = true, example = "목에 빨간 목줄")
    val description: String? = null,
    @field:Schema(description = "신고자 연락처", example = "01012345678")
    @field:NotBlank @field:Size(max = 30) val reporterPhone: String? = null,
    @get:Schema(name = "isPhonePublic", description = "연락처 공개 여부. 생략하면 false(작성자·담당 보호소만 열람)", nullable = true, example = "false")
    val isPhonePublic: Boolean? = null,
    @field:Schema(description = "발견 장소 기준점 설명(자유 서술, 주소 아님)", nullable = true, example = "공원 정문 앞")
    val landmarkDescription: String? = null,
    @field:Schema(description = "발견 위치 주소(5.6 주소 검색 후보 값)", example = "대구광역시 중구 동인동1가")
    @field:NotBlank @field:Size(max = 255) val address: String? = null,
    @field:Schema(description = "위도. -90~90", example = "35.87")
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: Double? = null,
    @field:Schema(description = "경도. -180~180", example = "128.6")
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: Double? = null,
    @field:Schema(description = "발견 시각(ISO-8601)", example = "2026-10-29T10:00:00+09:00")
    @field:NotNull val occurredDttm: OffsetDateTime? = null,
    @field:Schema(description = "사진 번호 1~3개. 본인이 올린 사진만, 중복 불가. 배열 순서가 표시 순서(첫 번째가 대표)", example = "[1201]")
    @field:NotNull val imageSeqs: List<Long>? = null,
    @field:Schema(hidden = true)
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
    @field:Schema(description = "조합 번호" + KEEP_REQUIRED, nullable = true, example = "88")
    val combinationSeq: Optional<Int>? = null,
    @field:Schema(description = GENDER_DESC + KEEP_REQUIRED, nullable = true, example = "UNKNOWN")
    val gender: Optional<Gender>? = null,
    @field:Schema(description = "추정 나이. 최대 30자" + KEEP_OR_CLEAR, nullable = true, example = "2살 추정")
    val age: Optional<String>? = null,
    @field:Schema(description = "추정 몸무게. 최대 30자" + KEEP_OR_CLEAR, nullable = true, example = "약 3kg")
    val weight: Optional<String>? = null,
    @field:Schema(description = "중성화 여부. 생략하면 유지, null을 보내면 모름으로 바꾼다", nullable = true, example = "true")
    val isNeutered: Optional<Boolean>? = null,
    @field:Schema(description = "마이크로칩 번호. 최대 30자" + KEEP_OR_CLEAR, nullable = true, example = "410100012345678")
    val microchipNumber: Optional<String>? = null,
    @field:Schema(description = "특징 설명" + KEEP_OR_CLEAR, nullable = true, example = "목에 빨간 목줄")
    val description: Optional<String>? = null,
    @field:Schema(description = "신고자 연락처. 최대 30자" + KEEP_REQUIRED, nullable = true, example = "01012345678")
    val reporterPhone: Optional<String>? = null,
    @field:Schema(description = "연락처 공개 여부" + KEEP_REQUIRED, nullable = true, example = "true")
    val isPhonePublic: Optional<Boolean>? = null,
    @field:Schema(description = "기준점 설명" + KEEP_OR_CLEAR, nullable = true, example = "공원 정문 앞")
    val landmarkDescription: Optional<String>? = null,
    @field:Schema(description = "주소. 최대 255자. 바뀌면 districtCode를 다시 구한다" + KEEP_REQUIRED, nullable = true, example = "대구광역시 중구 동인동1가")
    val address: Optional<String>? = null,
    @field:Schema(description = "위도. -90~90. 바뀌면 districtCode를 다시 구한다" + KEEP_REQUIRED, nullable = true, example = "35.87")
    val latitude: Optional<Double>? = null,
    @field:Schema(description = "경도. -180~180. 바뀌면 districtCode를 다시 구한다" + KEEP_REQUIRED, nullable = true, example = "128.6")
    val longitude: Optional<Double>? = null,
    @field:Schema(description = "발견 시각" + KEEP_REQUIRED, nullable = true, example = "2026-10-29T10:00:00+09:00")
    val occurredDttm: Optional<OffsetDateTime>? = null,
    @field:Schema(description = "사진 최종 배열 1~3개(순서 = 표시 순서)" + KEEP_REQUIRED, nullable = true, example = "[1201, 1202]")
    val imageSeqs: Optional<List<Long>>? = null,
    @field:Schema(hidden = true)
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
    @field:Schema(description = "바꿀 상태. CLOSED: 종료, ACTIVE: 재개(작성자만)", example = "CLOSED")
    @field:NotNull val status: FoundStatus? = null,
    @field:Schema(description = "종료 사유. CLOSED일 때만 저장(앞뒤 공백 제거, 빈 값은 null). ACTIVE면 무시", nullable = true, example = "보호자가 직접 찾아감")
    val closedReason: String? = null,
)

data class RegisteredFoundReportResponse(
    @field:Schema(description = "새 발견 신고 번호", example = "210")
    val foundReportSeq: Int,
    @field:Schema(description = "항상 ACTIVE", example = "ACTIVE")
    val status: FoundStatus,
    @field:Schema(description = "좌표로 구한 시·군·구 코드(5자리). 역지오코딩 실패면 null", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "배정된 담당 보호소 번호", example = "7")
    val assignedShelterSeq: Int,
    @field:Schema(
        description = "배정 방식. DISTRICT: 시·군·구 지정 보호소, NEAREST: 지정 보호소가 없어 가장 가까운 보호소",
        allowableValues = ["DISTRICT", "NEAREST"],
        example = "DISTRICT",
    )
    val assignmentMethod: String,
)

data class FoundStatusResponse(
    @field:Schema(description = "발견 신고 번호", example = "210")
    val foundReportSeq: Int,
    @field:Schema(description = "바뀐 상태", example = "CLOSED")
    val status: FoundStatus,
    @field:Schema(description = "종료 사유. ACTIVE면 null", nullable = true, example = "보호자가 직접 찾아감")
    val closedReason: String?,
    @field:Schema(description = "종료 시각(서버 시각). ACTIVE면 null", nullable = true, example = "2026-10-30T09:00:00+09:00")
    val closedDttm: OffsetDateTime?,
) {
    companion object {
        fun from(report: FoundReport) = FoundStatusResponse(report.foundReportSeq!!, report.status, report.closedReason, report.closedDttm)
    }
}

/** 8.1·8.3 목록 항목(7.1과 같은 구성 + shelterSeq) */
data class FoundReportSummaryResponse(
    @field:Schema(description = "발견 신고 번호", example = "210")
    val foundReportSeq: Int,
    @field:Schema(description = "종·품종·색상 분류. 조합을 찾지 못하면 null", nullable = true)
    val classification: AnimalClassification?,
    @field:Schema(description = GENDER_DESC, example = "UNKNOWN")
    val gender: Gender,
    @field:Schema(description = "추정 나이(자유 텍스트)", nullable = true, example = "2살 추정")
    val age: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null이면 모름", nullable = true, example = "null")
    val isNeutered: Boolean?,
    @field:Schema(description = "추정 몸무게(자유 텍스트)", nullable = true, example = "약 3kg")
    val weight: String?,
    @field:Schema(description = STATUS_DESC, example = "ACTIVE")
    val status: FoundStatus,
    @field:Schema(description = "담당 보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "발견 위치 주소", example = "대구광역시 중구 동인동1가")
    val address: String,
    @field:Schema(description = "시·군·구 코드(5자리). 역지오코딩 실패면 null", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "발견 시각", example = "2026-10-29T10:00:00+09:00")
    val occurredDttm: OffsetDateTime,
    @field:Schema(description = "대표 사진(표시 순서가 가장 앞선 사진) URL", nullable = true, example = "https://storage.example.com/images/1201.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "등록 시각", nullable = true, example = "2026-10-29T12:00:00+09:00")
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

@Schema(description = "담당 보호소")
data class ShelterRef(
    @field:Schema(description = "보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "보호소 이름", nullable = true, example = "대구 중구 동물보호센터")
    val shelterName: String?,
)

/** 8.2·8.5 상세 = 목록 필드 + 상세 필드 */
data class FoundReportResponse(
    @get:JsonUnwrapped val summary: FoundReportSummaryResponse,
    @field:Schema(description = "작성자")
    val reporter: ActorSummary,
    @field:Schema(description = "담당 보호소")
    val shelter: ShelterRef,
    @field:Schema(description = "마이크로칩 번호", nullable = true, example = "410100012345678")
    val microchipNumber: String?,
    @field:Schema(description = "특징 설명", nullable = true, example = "목에 빨간 목줄")
    val description: String?,
    @field:Schema(description = "기준점 설명", nullable = true, example = "공원 정문 앞")
    val landmarkDescription: String?,
    @field:Schema(description = "위도", example = "35.87")
    val latitude: Double,
    @field:Schema(description = "경도", example = "128.6")
    val longitude: Double,
    @field:Schema(description = "신고자 연락처. 공개이거나 조회자가 작성자·이 신고의 담당 보호소일 때만 값, 아니면 null", nullable = true, example = "01012345678")
    val reporterPhone: String?,
    @get:Schema(name = "isPhonePublic", description = "연락처 공개 여부", example = "false")
    val isPhonePublic: Boolean,
    @field:Schema(description = "종료 사유. 종료가 아니거나 사유가 없으면 null", nullable = true, example = "null")
    val closedReason: String?,
    @field:Schema(description = "종료 시각. 종료가 아니면 null", nullable = true, example = "null")
    val closedDttm: OffsetDateTime?,
    @field:Schema(description = "사진 목록(표시 순서대로)")
    val images: List<ImageRef>,
    @get:Schema(name = "isBookmarked", description = "조회자가 북마크했는지. SHELTER는 항상 false", example = "false")
    val isBookmarked: Boolean,
    @field:Schema(description = "삭제되지 않은 댓글 수", example = "2")
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
    @field:Schema(description = "신고자 실명·연락처(보호소 화면용)")
    val reporter: ReporterContact,
    @field:Schema(
        description = "구조 상태(이 신고의 최신 비삭제 구조 기록 기준). NONE: 구조 기록 없음, PROCESSING: 구조 진행 중, DONE: 구조 완료, ABORTED: 실패·취소",
        example = "NONE",
    )
    val rescueStatus: RescueProgress,
    @field:Schema(description = "진행 중·완료된 구조 기록 번호. rescueStatus가 PROCESSING·DONE일 때만 값, 아니면 null", nullable = true, example = "null")
    val activeRescueSeq: Int?,
) {
    companion object {
        fun from(item: AssignedFoundReportItem) =
            AssignedFoundReportResponse(FoundReportSummaryResponse.from(item.item), item.reporter, item.rescueStatus, item.activeRescueSeq)
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class FoundSummaryPageSuccessSchema : SuccessResponseSchema<PageResponse<FoundReportSummaryResponse>>()
abstract class FoundSummaryListSuccessSchema : SuccessResponseSchema<List<FoundReportSummaryResponse>>()
abstract class AssignedFoundPageSuccessSchema : SuccessResponseSchema<PageResponse<AssignedFoundReportResponse>>()
abstract class FoundDetailSuccessSchema : SuccessResponseSchema<FoundReportResponse>()
abstract class RegisteredFoundSuccessSchema : SuccessResponseSchema<RegisteredFoundReportResponse>()
abstract class FoundStatusSuccessSchema : SuccessResponseSchema<FoundStatusResponse>()
