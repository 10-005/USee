package com.ssafy.animal.core.rescue.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
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
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.Optional

// 사유 코드는 문자열로 받아 정의되지 않은 값도 400 COMMON_INVALID_INPUT으로 낸다(10.3. enum으로 받으면 COMMON_INVALID_JSON)
private const val REASON_CODES = "WANDERING|INJURY_ILLNESS|DANGEROUS_PLACE|OTHER"

// 문서용 enum 설명
private const val STATUS_DESC = "구조 상태. PROCESSING=진행 중, DONE=구조 완료, FAILED=구조 실패, CANCELED=취소"
private const val SOURCE_DESC = "접수 경로. REPORT=발견 신고 연계(foundReportSeq 있음), DIRECT=직접 접수(foundReportSeq null)"
private const val REASON_DESC = "구조 사유. WANDERING=배회/유기 의심, INJURY_ILLNESS=부상/질병, DANGEROUS_PLACE=위험 장소, OTHER=기타"

private val RescueRecord.source get() = if (foundReportSeq == null) RescueSource.DIRECT else RescueSource.REPORT

/** 10.3. 직접 접수는 foundReportSeq null */
data class CreateRescueRequest(
    @field:Schema(description = "연계할 발견 신고 번호. 생략하거나 null이면 직접 접수", nullable = true, example = "210")
    val foundReportSeq: Int? = null,
    @field:Schema(
        description = "$REASON_DESC. 필수. 정의되지 않은 값은 400 COMMON_INVALID_INPUT",
        allowableValues = ["WANDERING", "INJURY_ILLNESS", "DANGEROUS_PLACE", "OTHER"],
        example = "WANDERING",
    )
    @field:NotBlank @field:Pattern(regexp = REASON_CODES, message = "정의되지 않은 구조 사유 코드입니다.") val rescueReasonCode: String? = null,
    @field:Schema(description = "구조 장소. 필수, 공백 불가, 최대 255자", example = "구미시 인동동 123-12")
    @field:NotBlank @field:Size(max = 255) val rescueAddress: String? = null,
    @field:Schema(description = "상세 장소(건물명·주변 지형). 선택, 최대 200자", nullable = true, example = "송산도서관 인근 마을")
    @field:Size(max = 200) val rescueAddressDetail: String? = null,
    @field:Schema(description = "실제 구조 일시(ISO-8601, 오프셋 포함). 필수", example = "2026-10-23T17:47:00+09:00")
    @field:NotNull val rescuedDttm: OffsetDateTime? = null,
    @field:Schema(description = "구조 메모. 선택", nullable = true, example = "공원 벤치 아래 은신 중, 포획틀 필요")
    val rescueMemo: String? = null,
) {
    fun toCommand() = CreateRescueCommand(
        foundReportSeq,
        RescueContent(RescueReasonCode.valueOf(rescueReasonCode!!), rescueAddress!!, rescueAddressDetail, rescuedDttm!!, rescueMemo),
    )
}

/** 10.4 부분 수정. 필드 미전달은 null, 명시적 null은 Optional.empty() */
data class UpdateRescueRequest(
    @field:Schema(
        description = "$REASON_DESC. 보내면 변경, null 불가. 종료 상태에서는 보낼 수 없다",
        allowableValues = ["WANDERING", "INJURY_ILLNESS", "DANGEROUS_PLACE", "OTHER"],
        example = "INJURY_ILLNESS",
    )
    val rescueReasonCode: Optional<String>? = null,
    @field:Schema(description = "구조 장소. 보내면 변경. null·공백 불가, 최대 255자. 종료 상태에서는 보낼 수 없다", example = "구미시 인동동 123-12")
    val rescueAddress: Optional<String>? = null,
    @field:Schema(description = "상세 장소. 보내면 변경, null이면 비운다. 최대 200자. 종료 상태에서는 보낼 수 없다", nullable = true, example = "송산도서관 인근 마을")
    val rescueAddressDetail: Optional<String>? = null,
    @field:Schema(description = "실제 구조 일시. 보내면 변경, null 불가. 종료 상태에서는 보낼 수 없다", example = "2026-10-23T17:47:00+09:00")
    val rescuedDttm: Optional<OffsetDateTime>? = null,
    @field:Schema(description = "구조 메모. 보내면 변경, null이면 비운다. 종료 상태에서도 고칠 수 있는 유일한 필드", nullable = true, example = "포획틀 설치 완료")
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
    @field:Schema(description = "바꿀 종료 상태. DONE=구조 완료, FAILED=구조 실패, CANCELED=취소. 필수. PROCESSING은 409", example = "FAILED")
    @field:NotNull val status: RescueStatus? = null,
    @field:Schema(description = "실패·취소 사유. FAILED·CANCELED면 공백 아닌 값 필수. DONE이면 보내도 저장하지 않는다", nullable = true, example = "현장 도착 전 이동")
    val resultReason: String? = null,
)

data class CreatedRescueResponse(
    @field:Schema(description = "새 구조 기록 번호", example = "301")
    val rescueSeq: Int,
    @field:Schema(description = "항상 PROCESSING", example = "PROCESSING")
    val status: RescueStatus,
    @field:Schema(description = "접수 시각", example = "2026-10-23T18:00:00+09:00")
    val regDttm: OffsetDateTime?,
) {
    companion object {
        fun from(rescue: RescueRecord) = CreatedRescueResponse(rescue.rescueSeq!!, rescue.status, rescue.regDttm)
    }
}

data class RescueStatusResponse(
    @field:Schema(description = "구조 기록 번호", example = "301")
    val rescueSeq: Int,
    @field:Schema(description = "바뀐 종료 상태. DONE=구조 완료, FAILED=구조 실패, CANCELED=취소", example = "FAILED")
    val status: RescueStatus,
    @field:Schema(description = "실패·취소 사유(앞뒤 공백 제거). DONE이면 null", nullable = true, example = "현장 도착 전 이동")
    val resultReason: String?,
    @field:Schema(description = "종료 처리 시각(서버 기록)", example = "2026-10-24T09:10:00+09:00")
    val completedDttm: OffsetDateTime?,
) {
    companion object {
        fun from(rescue: RescueRecord) = RescueStatusResponse(rescue.rescueSeq!!, rescue.status, rescue.resultReason, rescue.completedDttm)
    }
}

data class RescueSummaryResponse(
    @field:Schema(description = "구조 기록 번호(화면 '등록번호')", example = "301")
    val rescueSeq: Int,
    @field:Schema(description = STATUS_DESC, example = "PROCESSING")
    val status: RescueStatus,
    @field:Schema(description = SOURCE_DESC, example = "REPORT")
    val source: RescueSource,
    @field:Schema(description = "연계된 발견 신고 번호. 직접 접수면 null", nullable = true, example = "210")
    val foundReportSeq: Int?,
    @field:Schema(description = "실제 구조 일시", example = "2026-10-23T17:47:00+09:00")
    val rescuedDttm: OffsetDateTime,
    @field:Schema(description = REASON_DESC, example = "WANDERING")
    val rescueReasonCode: RescueReasonCode,
    @field:Schema(description = "구조 메모", nullable = true, example = "공원 벤치 아래 은신 중, 포획틀 필요")
    val rescueMemo: String?,
    @field:Schema(description = "실패·취소 사유(화면 '비고'). PROCESSING·DONE이면 null", nullable = true)
    val resultReason: String?,
    @field:Schema(description = "동물 정보. REPORT는 발견 신고, DIRECT는 연결 보호동물 중 가장 먼저 등록된 1마리. 아직 없으면 null", nullable = true)
    val animal: RescueAnimal?,
    @field:Schema(description = "접수 시각", example = "2026-10-10T22:20:00+09:00")
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
    @field:Schema(
        description = "상태별 건수. 키는 total·PROCESSING·DONE·FAILED·CANCELED. status를 뺀 나머지 필터만 적용해 센다. total은 네 값의 합",
        example = """{"total":24,"PROCESSING":3,"DONE":16,"FAILED":3,"CANCELED":2}""",
    )
    val statusCounts: Map<String, Long>,
    @field:Schema(description = "현재 status 탭의 목록. totalElements는 statusCounts의 해당 값과 같다(status 생략이면 total)")
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
    @field:Schema(description = "구조 기록 번호", example = "301")
    val rescueSeq: Int,
    @field:Schema(description = "접수한 보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = SOURCE_DESC, example = "REPORT")
    val source: RescueSource,
    @field:Schema(description = "연계된 발견 신고 번호. 직접 접수면 null", nullable = true, example = "210")
    val foundReportSeq: Int?,
    @field:Schema(description = STATUS_DESC, example = "PROCESSING")
    val status: RescueStatus,
    @field:Schema(description = REASON_DESC, example = "WANDERING")
    val rescueReasonCode: RescueReasonCode,
    @field:Schema(description = "구조 장소", example = "구미시 인동동 123-12")
    val rescueAddress: String,
    @field:Schema(description = "상세 장소", nullable = true, example = "송산도서관 인근 마을")
    val rescueAddressDetail: String?,
    @field:Schema(description = "실제 구조 일시", example = "2026-10-23T17:47:00+09:00")
    val rescuedDttm: OffsetDateTime,
    @field:Schema(description = "구조 메모", nullable = true, example = "공원 벤치 아래 은신 중, 포획틀 필요")
    val rescueMemo: String?,
    @field:Schema(description = "실패·취소 사유. PROCESSING·DONE이면 null", nullable = true)
    val resultReason: String?,
    @field:Schema(description = "접수 시각(화면 '접수일자')", example = "2026-10-10T22:20:00+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "마지막 수정 시각(화면 '업데이트'). 수정 전이면 null", nullable = true, example = "2026-11-12T22:20:00+09:00")
    val modDttm: OffsetDateTime?,
    @field:Schema(description = "종료 처리 시각. PROCESSING이면 null", nullable = true)
    val completedDttm: OffsetDateTime?,
    @field:Schema(description = "이 구조에 연결된 삭제되지 않은 보호동물 수. 1 이상이면 삭제(10.6)할 수 없다", example = "0")
    val linkedAnimalCount: Long,
    @field:Schema(description = "연계된 발견 신고 요약. 직접 접수면 null. 신고가 삭제됐어도 남은 정보로 채운다", nullable = true)
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

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class RescuePageSuccessSchema : SuccessResponseSchema<RescuePageResponse>()
abstract class RescueDetailSuccessSchema : SuccessResponseSchema<RescueResponse>()
abstract class CreatedRescueSuccessSchema : SuccessResponseSchema<CreatedRescueResponse>()
abstract class RescueStatusSuccessSchema : SuccessResponseSchema<RescueStatusResponse>()
