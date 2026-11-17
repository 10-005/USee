package com.ssafy.animal.core.shelter.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterView
import com.ssafy.animal.core.shelter.application.port.`in`.UpdateShelterCommand
import io.swagger.v3.oas.annotations.media.Schema
import java.util.Optional

/**
 * 9.4 부분 수정. 운영정보 다섯 필드만 바꾼다.
 * 식별정보 필드는 무시하지 않고 400으로 거절하려고(Q412) 받아만 둔다. 필드가 있으면(null이어도) 거절
 */
data class UpdateShelterRequest(
    @field:Schema(description = "센터 소개. 보내면 변경, null이면 비운다", nullable = true, example = "센터 소개")
    val description: Optional<String>? = null,
    @field:Schema(description = "운영 시간(자유 텍스트). 보내면 변경, null이면 비운다", nullable = true, example = "09:00~18:00")
    val operatingHours: Optional<String>? = null,
    @field:Schema(description = "휴무일(자유 텍스트). 보내면 변경, null이면 비운다", nullable = true, example = "일요일")
    val closedDays: Optional<String>? = null,
    @field:Schema(description = "프로필 이미지 번호(4.2로 이 계정이 올린 이미지). 보내면 변경, null이면 연결 해제", nullable = true, example = "1701")
    val profileImageSeq: Optional<Long>? = null,
    @field:Schema(description = "배경 이미지 번호(4.2로 이 계정이 올린 이미지). 보내면 변경, null이면 연결 해제", nullable = true, example = "1702")
    val backgroundImageSeq: Optional<Long>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val shelterName: Optional<Any>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val address: Optional<Any>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val phone: Optional<Any>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val latitude: Optional<Any>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val longitude: Optional<Any>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val districtCode: Optional<Any>? = null,
    @field:Schema(description = "보호소 식별정보. 보내면(null이어도) 400 COMMON_INVALID_INPUT. 수정할 수 없다", nullable = true, deprecated = true)
    val externalShelterCode: Optional<Any>? = null,
) {
    fun toCommand(): UpdateShelterCommand {
        val identity = mapOf(
            "shelterName" to shelterName, "address" to address, "phone" to phone, "latitude" to latitude,
            "longitude" to longitude, "districtCode" to districtCode, "externalShelterCode" to externalShelterCode,
        ).filterValues { it != null }.keys
        if (identity.isNotEmpty()) {
            throw BusinessException(
                CommonErrorCode.COMMON_INVALID_INPUT,
                errors = identity.map { ErrorDetail(it, "보호소 식별정보는 수정할 수 없습니다.") },
            )
        }
        return UpdateShelterCommand(description, operatingHours, closedDays, profileImageSeq, backgroundImageSeq)
    }
}

/** 9.1 목록 항목: 상세에서 description·operatingHours·closedDays·backgroundImage를 뺀 것 */
data class ShelterSummaryResponse(
    @field:Schema(description = "보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "보호소 이름", example = "행복동물보호센터")
    val shelterName: String,
    @field:Schema(description = "주소", example = "대구광역시 중구 동덕로 123")
    val address: String,
    @field:Schema(description = "소재지 시군구 코드(5자리, 5.5)", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "위도", example = "35.87")
    val latitude: Double,
    @field:Schema(description = "경도", example = "128.6")
    val longitude: Double,
    @field:Schema(description = "전화번호", example = "0531234567")
    val phone: String,
    @field:Schema(description = "프로필 이미지 { imageSeq, imageUrl }. 없으면 null", nullable = true)
    val profileImage: ImageSummary?,
) {
    companion object {
        fun from(view: ShelterView) = with(view.shelter) {
            ShelterSummaryResponse(shelterSeq!!, shelterName, address, districtCode, latitude, longitude, phone, view.profileImage)
        }
    }
}

/** 9.2·9.3·9.4 상세. externalShelterCode는 내보내지 않는다 */
data class ShelterResponse(
    @field:Schema(description = "보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "보호소 이름. 수정 불가", example = "행복동물보호센터")
    val shelterName: String,
    @field:Schema(description = "주소. 수정 불가", example = "대구광역시 중구 동덕로 123")
    val address: String,
    @field:Schema(description = "소재지 시군구 코드(5자리, 5.5). 수정 불가", nullable = true, example = "27110")
    val districtCode: String?,
    @field:Schema(description = "위도. 수정 불가", example = "35.87")
    val latitude: Double,
    @field:Schema(description = "경도. 수정 불가", example = "128.6")
    val longitude: Double,
    @field:Schema(description = "전화번호. 수정 불가", example = "0531234567")
    val phone: String,
    @field:Schema(description = "센터 소개", nullable = true, example = "센터 소개")
    val description: String?,
    @field:Schema(description = "운영 시간", nullable = true, example = "09:00~18:00")
    val operatingHours: String?,
    @field:Schema(description = "휴무일", nullable = true, example = "일요일")
    val closedDays: String?,
    @field:Schema(description = "프로필 이미지 { imageSeq, imageUrl }. 없으면 null", nullable = true)
    val profileImage: ImageSummary?,
    @field:Schema(description = "배경 이미지 { imageSeq, imageUrl }. 없으면 null", nullable = true)
    val backgroundImage: ImageSummary?,
) {
    companion object {
        fun from(view: ShelterView) = with(view.shelter) {
            ShelterResponse(
                shelterSeq!!, shelterName, address, districtCode, latitude, longitude, phone,
                description, operatingHours, closedDays, view.profileImage, view.backgroundImage,
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ShelterPageSuccessSchema : SuccessResponseSchema<PageResponse<ShelterSummaryResponse>>()
abstract class ShelterDetailSuccessSchema : SuccessResponseSchema<ShelterResponse>()
