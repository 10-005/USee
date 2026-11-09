package com.ssafy.animal.core.shelter.adapter.`in`.web

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterView
import com.ssafy.animal.core.shelter.application.port.`in`.UpdateShelterCommand
import java.util.Optional

/**
 * 9.4 부분 수정. 운영정보 다섯 필드만 바꾼다.
 * 식별정보 필드는 무시하지 않고 400으로 거절하려고(Q412) 받아만 둔다. 필드가 있으면(null이어도) 거절
 */
data class UpdateShelterRequest(
    val description: Optional<String>? = null,
    val operatingHours: Optional<String>? = null,
    val closedDays: Optional<String>? = null,
    val profileImageSeq: Optional<Long>? = null,
    val backgroundImageSeq: Optional<Long>? = null,
    val shelterName: Optional<Any>? = null,
    val address: Optional<Any>? = null,
    val phone: Optional<Any>? = null,
    val latitude: Optional<Any>? = null,
    val longitude: Optional<Any>? = null,
    val districtCode: Optional<Any>? = null,
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
    val shelterSeq: Int,
    val shelterName: String,
    val address: String,
    val districtCode: String?,
    val latitude: Double,
    val longitude: Double,
    val phone: String,
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
    val shelterSeq: Int,
    val shelterName: String,
    val address: String,
    val districtCode: String?,
    val latitude: Double,
    val longitude: Double,
    val phone: String,
    val description: String?,
    val operatingHours: String?,
    val closedDays: String?,
    val profileImage: ImageSummary?,
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
