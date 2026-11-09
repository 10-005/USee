package com.ssafy.animal.core.animal.adapter.`in`.web

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.animal.application.port.`in`.ManageAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.RegisterAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.UpdateAnimalCommand
import com.ssafy.animal.core.animal.domain.AnimalBasics
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.Optional

// 본문의 enum은 문자열로 받아 직접 바꾼다. 허용값 밖이면 명세대로 400 COMMON_INVALID_INPUT(enum 필드면 COMMON_INVALID_JSON이 된다)

/** 11.4 */
data class RegisterAnimalRequest(
    val rescueSeq: Int? = null,
    @field:NotNull val combinationSeq: Int? = null,
    @field:NotNull val gender: String? = null,
    val age: String? = null,
    val isNeutered: Boolean? = null,
    val microchipNumber: String? = null,
    val registrationNumber: String? = null,
    val weight: String? = null,
    val description: String? = null,
    val healthCheck: String? = null,
    val vaccinationStatus: String? = null,
    val noticeStartDate: LocalDate? = null,
    val noticeEndDate: LocalDate? = null,
    val adoptionStatus: String? = null,
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = RegisterAnimalCommand(
        rescueSeq = rescueSeq,
        basics = AnimalBasics(
            combinationSeq!!, gender.toEnum("gender")!!, age, isNeutered, microchipNumber, registrationNumber,
            weight, description, healthCheck, vaccinationStatus,
        ),
        noticeStartDate = noticeStartDate,
        noticeEndDate = noticeEndDate,
        adoptionStatus = adoptionStatus.toEnum("adoptionStatus"),
        imageSeqs = imageSeqs.orEmpty(),
    )
}

/** 11.5. 공고기간·상태 필드는 받지 않는다(보내도 무시, Q479) */
data class UpdateAnimalRequest(
    val combinationSeq: Int? = null,
    val gender: String? = null,
    val age: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val weight: Optional<String>? = null,
    val microchipNumber: Optional<String>? = null,
    val registrationNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val healthCheck: Optional<String>? = null,
    val vaccinationStatus: Optional<String>? = null,
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = UpdateAnimalCommand(
        combinationSeq, gender.toEnum("gender"), age, isNeutered, weight, microchipNumber, registrationNumber,
        description, healthCheck, vaccinationStatus, imageSeqs,
    )
}

/** 11.6 */
data class ManageAnimalRequest(
    val noticeStartDate: LocalDate? = null,
    val noticeEndDate: LocalDate? = null,
    val status: String? = null,
    val adoptionStatus: String? = null,
    val endReason: String? = null,
    val recipientName: String? = null,
    val recipientPhone: String? = null,
    val reason: String? = null,
    val memo: String? = null,
) {
    fun toCommand() = ManageAnimalCommand(
        noticeStartDate, noticeEndDate, status.toEnum("status"), adoptionStatus.toEnum("adoptionStatus"),
        endReason.toEnum("endReason"), recipientName, recipientPhone, reason, memo,
    )
}

inline fun <reified E : Enum<E>> String?.toEnum(field: String): E? = this?.let { value ->
    enumValues<E>().firstOrNull { it.name == value }
        ?: throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail(field, "허용되지 않는 값입니다: $value")))
}
