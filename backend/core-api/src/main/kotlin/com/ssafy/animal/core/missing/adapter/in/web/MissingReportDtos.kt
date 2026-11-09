package com.ssafy.animal.core.missing.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
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
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional

private const val GENDER_DESC = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름"
private const val STATUS_DESC = "신고 상태. ACTIVE: 찾는 중, CLOSED: 종료(읽기 전용)"

/** 7.4. 길이·좌표 범위는 수정과 같은 규칙이라 서비스가 검증한다 */
data class CreateMissingRequest(
    @field:Schema(description = "종·품종·색상 조합 번호(5.4 값)", example = "88")
    @field:NotNull
    val combinationSeq: Int?,
    @field:Schema(description = GENDER_DESC, example = "MALE")
    @field:NotNull
    val gender: MissingGender?,
    @field:Schema(description = "나이. 자유 텍스트, 최대 30자", nullable = true, example = "3살")
    val age: String? = null,
    @field:Schema(description = "몸무게. 자유 텍스트, 최대 30자(서버는 숫자로 해석하지 않는다)", nullable = true, example = "4.5kg")
    val weight: String? = null,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null이면 모름", nullable = true, example = "true")
    val isNeutered: Boolean? = null,
    @field:Schema(description = "마이크로칩 번호. 최대 30자", nullable = true, example = "410100012345678")
    val microchipNumber: String? = null,
    @field:Schema(description = "특징 설명. 목록 keyword 검색 대상", nullable = true, example = "파란 목줄")
    val description: String? = null,
    @field:Schema(description = "실종 위치 주소(5.6 주소 검색 후보 값). 최대 255자, 앞뒤 공백은 지운다", example = "대구광역시 중구 동인동1가")
    @field:NotBlank
    val address: String?,
    @field:Schema(description = "위도. -90~90", example = "35.87")
    @field:NotNull
    val latitude: Double?,
    @field:Schema(description = "경도. -180~180", example = "128.6")
    @field:NotNull
    val longitude: Double?,
    @field:Schema(description = "실종 시각(ISO-8601)", example = "2026-10-28T18:00:00+09:00")
    @field:NotNull
    val occurredDttm: OffsetDateTime?,
    @field:Schema(description = "연락처. 최대 30자, 앞뒤 공백은 지운다", example = "01012345678")
    @field:NotBlank
    val contactPhone: String?,
    @get:Schema(name = "isPhonePublic", description = "연락처 공개 여부. 생략하면 false(작성자만 열람)", nullable = true, example = "false")
    val isPhonePublic: Boolean? = null,
    @field:Schema(description = "사진 번호 1~3개. 본인이 올린 사진만, 중복 불가. 배열 순서가 표시 순서(첫 번째가 대표)", example = "[1001, 1002]")
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
    @field:Schema(description = "조합 번호. 생략·null이면 유지", nullable = true, example = "88")
    val combinationSeq: Int? = null,
    @field:Schema(description = GENDER_DESC + ". 생략·null이면 유지", nullable = true, example = "MALE")
    val gender: MissingGender? = null,
    @field:Schema(description = "나이. 최대 30자. 생략하면 유지, null을 보내면 지운다", nullable = true, example = "3살")
    val age: Optional<String>? = null,
    @field:Schema(description = "몸무게. 최대 30자. 생략하면 유지, null을 보내면 지운다", nullable = true, example = "4.5kg")
    val weight: Optional<String>? = null,
    @field:Schema(description = "중성화 여부. 생략하면 유지, null을 보내면 모름으로 바꾼다", nullable = true, example = "true")
    val isNeutered: Optional<Boolean>? = null,
    @field:Schema(description = "마이크로칩 번호. 최대 30자. 생략하면 유지, null을 보내면 지운다", nullable = true, example = "410100012345678")
    val microchipNumber: Optional<String>? = null,
    @field:Schema(description = "특징 설명. 생략하면 유지, null을 보내면 지운다", nullable = true, example = "파란 목줄")
    val description: Optional<String>? = null,
    @field:Schema(description = "주소. 최대 255자, 빈 문자열 불가. 생략·null이면 유지", nullable = true, example = "대구광역시 중구 동인동1가")
    val address: String? = null,
    @field:Schema(description = "위도. longitude와 함께 보낸다. 바꾸면 districtCode를 다시 구한다. 생략·null이면 유지", nullable = true, example = "35.87")
    val latitude: Double? = null,
    @field:Schema(description = "경도. latitude와 함께 보낸다. 생략·null이면 유지", nullable = true, example = "128.6")
    val longitude: Double? = null,
    @field:Schema(description = "실종 시각. 생략·null이면 유지", nullable = true, example = "2026-10-28T18:00:00+09:00")
    val occurredDttm: OffsetDateTime? = null,
    @field:Schema(description = "연락처. 최대 30자, 빈 문자열 불가. 생략·null이면 유지", nullable = true, example = "01012345678")
    val contactPhone: String? = null,
    @get:Schema(name = "isPhonePublic", description = "연락처 공개 여부. 생략·null이면 유지", nullable = true, example = "false")
    val isPhonePublic: Boolean? = null,
    @field:Schema(description = "사진 최종 배열 1~3개(순서 = 표시 순서). 생략·null이면 유지", nullable = true, example = "[1002, 1001]")
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = UpdateMissingCommand(
        combinationSeq, gender, age, weight, isNeutered, microchipNumber, description,
        address, latitude, longitude, occurredDttm, contactPhone, isPhonePublic, imageSeqs,
    )
}

data class ChangeMissingStatusRequest(
    @field:Schema(description = "바꿀 상태. CLOSED: 종료, ACTIVE: 재개", example = "CLOSED")
    @field:NotNull
    val status: MissingStatus?,
)

data class CreatedMissingResponse(
    @field:Schema(description = "새 실종 신고 번호", example = "101")
    val missingReportSeq: Int,
    @field:Schema(description = "항상 ACTIVE", example = "ACTIVE")
    val status: MissingStatus,
    @field:Schema(description = "좌표로 구한 시·군·구 코드(5자리). 역지오코딩 실패면 null", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "AI 매칭 요청 여부. 항상 true", example = "true")
    val aiMatchingRequested: Boolean,
) {
    companion object {
        fun from(created: CreatedMissing) =
            CreatedMissingResponse(created.missingReportSeq, created.status, created.districtCode, created.aiMatchingRequested)
    }
}

data class MissingStatusResponse(
    @field:Schema(description = "실종 신고 번호", example = "101")
    val missingReportSeq: Int,
    @field:Schema(description = "바뀐 상태", example = "CLOSED")
    val status: MissingStatus,
) {
    companion object {
        fun from(report: MissingReport) = MissingStatusResponse(report.missingReportSeq!!, report.status)
    }
}

/** 7.1·7.3 목록 항목 */
data class MissingSummaryResponse(
    @field:Schema(description = "실종 신고 번호", example = "101")
    val missingReportSeq: Int,
    @field:Schema(description = "종·품종·색상 분류. 조합을 찾지 못하면 null", nullable = true)
    val classification: AnimalClassification?,
    @field:Schema(description = GENDER_DESC, example = "MALE")
    val gender: MissingGender,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "3살")
    val age: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null이면 모름", nullable = true, example = "true")
    val isNeutered: Boolean?,
    @field:Schema(description = "몸무게(자유 텍스트)", nullable = true, example = "4.5kg")
    val weight: String?,
    @field:Schema(description = STATUS_DESC, example = "ACTIVE")
    val status: MissingStatus,
    @field:Schema(description = "실종 위치 주소", example = "대구광역시 중구 동인동1가")
    val address: String,
    @field:Schema(description = "시·군·구 코드(5자리). 역지오코딩 실패면 null", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "실종 시각", example = "2026-10-28T18:00:00+09:00")
    val occurredDttm: OffsetDateTime,
    @field:Schema(description = "대표 사진(표시 순서 1) URL", nullable = true, example = "https://storage.example.com/images/1001.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "등록 시각", nullable = true, example = "2026-10-29T12:00:00+09:00")
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
    @field:Schema(description = "실종 신고 번호", example = "101")
    val missingReportSeq: Int,
    @field:Schema(description = "종·품종·색상 분류. 조합을 찾지 못하면 null", nullable = true)
    val classification: AnimalClassification?,
    @field:Schema(description = GENDER_DESC, example = "MALE")
    val gender: MissingGender,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "3살")
    val age: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null이면 모름", nullable = true, example = "true")
    val isNeutered: Boolean?,
    @field:Schema(description = "몸무게(자유 텍스트)", nullable = true, example = "4.5kg")
    val weight: String?,
    @field:Schema(description = STATUS_DESC, example = "ACTIVE")
    val status: MissingStatus,
    @field:Schema(description = "실종 위치 주소", example = "대구광역시 중구 동인동1가")
    val address: String,
    @field:Schema(description = "시·군·구 코드(5자리). 역지오코딩 실패면 null", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "실종 시각", example = "2026-10-28T18:00:00+09:00")
    val occurredDttm: OffsetDateTime,
    @field:Schema(description = "대표 사진(images의 첫 번째) URL", nullable = true, example = "https://storage.example.com/images/1001.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "등록 시각", nullable = true, example = "2026-10-29T12:00:00+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "작성자", nullable = true)
    val user: MissingAuthor?,
    @field:Schema(description = "마이크로칩 번호", nullable = true, example = "410100012345678")
    val microchipNumber: String?,
    @field:Schema(description = "특징 설명", nullable = true, example = "파란 목줄")
    val description: String?,
    @field:Schema(description = "위도", example = "35.87")
    val latitude: Double,
    @field:Schema(description = "경도", example = "128.6")
    val longitude: Double,
    @field:Schema(description = "사진 목록(표시 순서대로)")
    val images: List<MissingImageRef>,
    @get:Schema(name = "isPhonePublic", description = "연락처 공개 여부", example = "false")
    val isPhonePublic: Boolean,
    @field:Schema(description = "연락처. 공개이거나 조회자가 작성자일 때만 값, 아니면 null", nullable = true, example = "01012345678")
    val contactPhone: String?,
    @get:Schema(name = "isBookmarked", description = "조회자가 북마크했는지", example = "false")
    val isBookmarked: Boolean,
    @field:Schema(description = "삭제되지 않은 댓글 수", example = "3")
    val commentCount: Long,
    @field:Schema(description = "마지막 수정 시각", nullable = true, example = "2026-10-29T12:00:00+09:00")
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

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class MissingSummaryPageSuccessSchema : SuccessResponseSchema<PageResponse<MissingSummaryResponse>>()
abstract class MissingSummaryListSuccessSchema : SuccessResponseSchema<List<MissingSummaryResponse>>()
abstract class MissingDetailSuccessSchema : SuccessResponseSchema<MissingDetailResponse>()
abstract class CreatedMissingSuccessSchema : SuccessResponseSchema<CreatedMissingResponse>()
abstract class MissingStatusSuccessSchema : SuccessResponseSchema<MissingStatusResponse>()
