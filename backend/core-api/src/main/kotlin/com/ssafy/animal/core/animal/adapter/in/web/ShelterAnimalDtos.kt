package com.ssafy.animal.core.animal.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.animal.application.port.`in`.AnimalCard
import com.ssafy.animal.core.animal.application.port.`in`.AnimalDetail
import com.ssafy.animal.core.animal.application.port.`in`.ManageAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.ManagedAnimal
import com.ssafy.animal.core.animal.application.port.`in`.MyAnimalPage
import com.ssafy.animal.core.animal.application.port.`in`.RegisterAnimalCommand
import com.ssafy.animal.core.animal.application.port.`in`.RegisteredAnimal
import com.ssafy.animal.core.animal.application.port.`in`.ShelterAnimalDetail
import com.ssafy.animal.core.animal.application.port.`in`.UpdateAnimalCommand
import com.ssafy.animal.core.animal.domain.AnimalBasics
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDate
import java.util.Optional

// 본문의 enum은 문자열로 받아 직접 바꾼다. 허용값 밖이면 명세대로 400 COMMON_INVALID_INPUT(enum 필드면 COMMON_INVALID_JSON이 된다)

/** 11.4 */
data class RegisterAnimalRequest(
    @field:Schema(description = "연결할 구조 기록 번호. DONE인 구조만 가능. 생략하면 직접 등록", nullable = true, example = "301")
    val rescueSeq: Int? = null,
    @field:Schema(description = "종·품종·색상 조합 번호(5.4). 필수", example = "88")
    @field:NotNull val combinationSeq: Int? = null,
    @field:Schema(description = "성별. MALE=수컷, FEMALE=암컷, UNKNOWN=모름. 필수. 허용값 밖이면 400 COMMON_INVALID_INPUT", allowableValues = ["MALE", "FEMALE", "UNKNOWN"], example = "UNKNOWN")
    @field:NotNull val gender: String? = null,
    @field:Schema(description = "나이(자유 텍스트). 최대 30자", nullable = true, example = "1살 추정")
    val age: String? = null,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null=모름", nullable = true)
    val isNeutered: Boolean? = null,
    @field:Schema(description = "마이크로칩 번호. 최대 30자", nullable = true)
    val microchipNumber: String? = null,
    @field:Schema(description = "정부 동물등록번호. 최대 20자", nullable = true)
    val registrationNumber: String? = null,
    @field:Schema(description = "몸무게(자유 텍스트). 최대 30자", nullable = true, example = "3kg")
    val weight: String? = null,
    @field:Schema(description = "특징·성격", nullable = true, example = "사람을 잘 따름")
    val description: String? = null,
    @field:Schema(description = "건강 검진 내용", nullable = true, example = "기초 검진 완료")
    val healthCheck: String? = null,
    @field:Schema(description = "예방접종 상태", nullable = true)
    val vaccinationStatus: String? = null,
    @field:Schema(description = "공고 시작일(YYYY-MM-DD). 생략하면 등록일(KST)", nullable = true, example = "2026-10-29")
    val noticeStartDate: LocalDate? = null,
    @field:Schema(description = "공고 종료일(YYYY-MM-DD). 생략하면 시작일+10일. 시작일보다 빠르면 400", nullable = true, example = "2026-11-08")
    val noticeEndDate: LocalDate? = null,
    @field:Schema(description = "입양 상태. AVAILABLE=입양 가능, UNAVAILABLE=입양 불가. 생략하면 UNAVAILABLE. ADOPTED는 400", allowableValues = ["AVAILABLE", "UNAVAILABLE"], nullable = true, example = "UNAVAILABLE")
    val adoptionStatus: String? = null,
    @field:Schema(description = "사진 이미지 번호(4.2로 이 계정이 올린 것). 1~3장, 중복 불가. 배열 순서가 표시 순서이고 첫 장이 대표 사진", example = "[1701, 1702]")
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
    @field:Schema(description = "종·품종·색상 조합 번호(5.4). 보내면 변경. null은 미전달로 본다", nullable = true, example = "88")
    val combinationSeq: Int? = null,
    @field:Schema(description = "성별. MALE=수컷, FEMALE=암컷, UNKNOWN=모름. 보내면 변경. null은 미전달로 본다", allowableValues = ["MALE", "FEMALE", "UNKNOWN"], nullable = true, example = "UNKNOWN")
    val gender: String? = null,
    @field:Schema(description = "나이. 보내면 변경, null이면 비운다. 최대 30자", nullable = true, example = "2살 추정")
    val age: Optional<String>? = null,
    @field:Schema(description = "중성화 여부. 보내면 변경, null이면 모름으로 바꾼다", nullable = true, example = "true")
    val isNeutered: Optional<Boolean>? = null,
    @field:Schema(description = "몸무게. 보내면 변경, null이면 비운다. 최대 30자", nullable = true, example = "3.5kg")
    val weight: Optional<String>? = null,
    @field:Schema(description = "마이크로칩 번호. 보내면 변경, null이면 비운다. 최대 30자", nullable = true)
    val microchipNumber: Optional<String>? = null,
    @field:Schema(description = "정부 동물등록번호. 보내면 변경, null이면 비운다. 최대 20자", nullable = true)
    val registrationNumber: Optional<String>? = null,
    @field:Schema(description = "특징·성격. 보내면 변경, null이면 비운다", nullable = true)
    val description: Optional<String>? = null,
    @field:Schema(description = "건강 검진 내용. 보내면 변경, null이면 비운다", nullable = true)
    val healthCheck: Optional<String>? = null,
    @field:Schema(description = "예방접종 상태. 보내면 변경, null이면 비운다", nullable = true)
    val vaccinationStatus: Optional<String>? = null,
    @field:Schema(description = "사진 전체 최종 배열(1~3장, 중복 불가). 보내면 통째로 바꾸고 생략하거나 null이면 유지. 새로 넣는 이미지는 이 계정이 올린 것만", nullable = true, example = "[1702]")
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = UpdateAnimalCommand(
        combinationSeq, gender.toEnum("gender"), age, isNeutered, weight, microchipNumber, registrationNumber,
        description, healthCheck, vaccinationStatus, imageSeqs,
    )
}

/** 11.6 */
data class ManageAnimalRequest(
    @field:Schema(description = "공고 시작일(YYYY-MM-DD). 보내면 변경", nullable = true, example = "2026-10-29")
    val noticeStartDate: LocalDate? = null,
    @field:Schema(description = "공고 종료일(YYYY-MM-DD). 보내면 변경. 변경 결과가 시작일보다 빠르면 400", nullable = true, example = "2026-11-15")
    val noticeEndDate: LocalDate? = null,
    @field:Schema(description = "보호 상태. PROTECTING=보호 중, RETURNED=반환 완료, ADOPTED=입양 완료, ENDED=보호 종료. 생략하면 상태는 그대로", allowableValues = ["PROTECTING", "RETURNED", "ADOPTED", "ENDED"], nullable = true, example = "PROTECTING")
    val status: String? = null,
    @field:Schema(description = "입양 상태. AVAILABLE·UNAVAILABLE. status=PROTECTING일 때만. 생략하면 현재 값 유지(다른 상태에서 복귀하면 UNAVAILABLE)", allowableValues = ["AVAILABLE", "UNAVAILABLE"], nullable = true, example = "AVAILABLE")
    val adoptionStatus: String? = null,
    @field:Schema(description = "보호 종료 사유. NATURAL_DEATH=자연사, EUTHANIZED=안락사, OTHER=기타. status=ENDED일 때만. 생략하면 OTHER", allowableValues = ["NATURAL_DEATH", "EUTHANIZED", "OTHER"], nullable = true, example = "NATURAL_DEATH")
    val endReason: String? = null,
    @field:Schema(description = "반환받는 사람 이름. status=RETURNED일 때 필수, 최대 100자", nullable = true, example = "김가람")
    val recipientName: String? = null,
    @field:Schema(description = "반환받는 사람 연락처. status=RETURNED일 때 필수, 최대 30자", nullable = true, example = "01012345678")
    val recipientPhone: String? = null,
    @field:Schema(description = "외부 입양 처리 사유. status=ADOPTED일 때 필수, 최대 200자", nullable = true, example = "보호소 방문 상담 후 직접 인계")
    val reason: String? = null,
    @field:Schema(description = "외부 입양 비고. status=ADOPTED일 때만, 선택", nullable = true, example = "예방접종 2차 안내 완료")
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

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class ShelterAnimalPageSuccessSchema : SuccessResponseSchema<PageResponse<AnimalCard>>()
abstract class MyAnimalPageSuccessSchema : SuccessResponseSchema<MyAnimalPage>()
abstract class AnimalDetailSuccessSchema : SuccessResponseSchema<AnimalDetail>()
abstract class ShelterAnimalDetailSuccessSchema : SuccessResponseSchema<ShelterAnimalDetail>()
abstract class RegisteredAnimalSuccessSchema : SuccessResponseSchema<RegisteredAnimal>()
abstract class ManagedAnimalSuccessSchema : SuccessResponseSchema<ManagedAnimal>()
