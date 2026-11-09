package com.ssafy.animal.core.pet.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.pet.application.port.`in`.CreatePetCommand
import com.ssafy.animal.core.pet.application.port.`in`.CreatedPet
import com.ssafy.animal.core.pet.application.port.`in`.PetDetail
import com.ssafy.animal.core.pet.application.port.`in`.PetImageRef
import com.ssafy.animal.core.pet.application.port.`in`.PetSummary
import com.ssafy.animal.core.pet.application.port.`in`.UpdatePetCommand
import com.ssafy.animal.core.pet.domain.PetGender
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime
import java.util.Optional

/** 6.3. 길이 검증은 수정과 같은 규칙이라 서비스가 한다 */
data class CreatePetRequest(
    @field:Schema(description = "이름. 최대 100자, 앞뒤 공백은 잘라 저장", example = "초코")
    @field:NotBlank
    val name: String?,
    @field:Schema(description = "종·품종·색상 조합 번호(기준정보 API에서 조회)", example = "88")
    @field:NotNull
    val combinationSeq: Int?,
    @field:Schema(description = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름", example = "MALE")
    @field:NotNull
    val gender: PetGender?,
    @field:Schema(description = "나이(자유 텍스트). 선택, 최대 30자", nullable = true, example = "3살")
    val age: String? = null,
    @field:Schema(description = "몸무게(자유 텍스트). 선택, 최대 30자", nullable = true, example = "4.5kg")
    val weight: String? = null,
    @get:Schema(name = "isNeutered", description = "중성화 여부. true·false·null(모름)", nullable = true, example = "true")
    val isNeutered: Boolean? = null,
    @field:Schema(description = "마이크로칩 번호. 선택, 최대 30자", nullable = true, example = "410100012345678")
    val microchipNumber: String? = null,
    @field:Schema(description = "특징 설명. 선택", nullable = true, example = "파란 목줄")
    val description: String? = null,
    @field:Schema(description = "사진 imageSeq 1~3개, 중복 불가, 본인이 확정한 이미지만. 배열 순서가 표시 순서이고 첫 번째가 대표", example = "[1001, 1002]")
    @field:NotNull
    val imageSeqs: List<Long>?,
) {
    fun toCommand() =
        CreatePetCommand(name!!, combinationSeq!!, gender!!, age, weight, isNeutered, microchipNumber, description, imageSeqs!!)
}

/** 6.4 부분 수정. 선택 필드는 미전달 null, 명시적 null은 Optional.empty() */
data class UpdatePetRequest(
    @field:Schema(description = "이름. 최대 100자. 생략·null이면 유지, 공백은 400", nullable = true, example = "초코")
    val name: String? = null,
    @field:Schema(description = "종·품종·색상 조합 번호. 생략·null이면 유지", nullable = true, example = "88")
    val combinationSeq: Int? = null,
    @field:Schema(description = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름. 생략·null이면 유지", nullable = true, example = "MALE")
    val gender: PetGender? = null,
    @field:Schema(description = "나이. 최대 30자. 생략하면 유지, null이면 지움", nullable = true, example = "4살")
    val age: Optional<String>? = null,
    @field:Schema(description = "몸무게. 최대 30자. 생략하면 유지, null이면 지움", nullable = true, example = "5kg")
    val weight: Optional<String>? = null,
    @field:Schema(description = "중성화 여부. 생략하면 유지, null이면 모름으로 바꿈", nullable = true, example = "true")
    val isNeutered: Optional<Boolean>? = null,
    @field:Schema(description = "마이크로칩 번호. 최대 30자. 생략하면 유지, null이면 지움", nullable = true, example = "410100012345678")
    val microchipNumber: Optional<String>? = null,
    @field:Schema(description = "특징 설명. 생략하면 유지, null이면 지움", nullable = true, example = "빨간 목줄")
    val description: Optional<String>? = null,
    @field:Schema(description = "사진 전체 최종 배열(1~3개, 중복 불가). 원하는 순서대로 보내면 표시 순서를 다시 매긴다. 생략·null이면 유지", nullable = true, example = "[1002, 1003]")
    val imageSeqs: List<Long>? = null,
) {
    fun toCommand() = UpdatePetCommand(name, combinationSeq, gender, age, weight, isNeutered, microchipNumber, description, imageSeqs)
}

data class CreatedPetResponse(
    @field:Schema(description = "새 반려동물 번호", example = "5")
    val petSeq: Int,
    @field:Schema(description = "연결한 사진 수", example = "2")
    val imageCount: Int,
    @field:Schema(description = "대표 사진 imageSeq(imageSeqs의 첫 번째)", example = "1001")
    val representativeImageSeq: Long,
) {
    companion object {
        fun from(created: CreatedPet) = CreatedPetResponse(created.petSeq, created.imageCount, created.representativeImageSeq)
    }
}

data class PetSummaryResponse(
    @field:Schema(description = "반려동물 번호", example = "5")
    val petSeq: Int,
    @field:Schema(description = "이름", example = "초코")
    val name: String,
    @field:Schema(description = "종·품종·색상. 조합을 찾지 못하면 null", nullable = true)
    val classification: AnimalClassification?,
    @field:Schema(description = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름", example = "MALE")
    val gender: PetGender,
    @field:Schema(description = "대표 사진 URL(표시 순서 1). 없으면 null", nullable = true, example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "등록 시각", example = "2026-11-09T10:15:30+09:00")
    val regDttm: OffsetDateTime?,
) {
    companion object {
        fun from(summary: PetSummary) = with(summary.pet) {
            PetSummaryResponse(petSeq!!, name, summary.classification, gender, summary.representativeImageUrl, regDttm)
        }
    }
}

data class PetDetailResponse(
    @field:Schema(description = "반려동물 번호", example = "5")
    val petSeq: Int,
    @field:Schema(description = "이름", example = "초코")
    val name: String,
    @field:Schema(description = "종·품종·색상. 조합을 찾지 못하면 null", nullable = true)
    val classification: AnimalClassification?,
    @field:Schema(description = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름", example = "MALE")
    val gender: PetGender,
    @field:Schema(description = "대표 사진 URL(images의 첫 번째). 없으면 null", nullable = true, example = "https://cdn.example.com/images/2026/11/3f1c2a9e.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "3살")
    val age: String?,
    @field:Schema(description = "몸무게(자유 텍스트)", nullable = true, example = "4.5kg")
    val weight: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null은 모름", nullable = true, example = "true")
    val isNeutered: Boolean?,
    @field:Schema(description = "마이크로칩 번호", nullable = true, example = "410100012345678")
    val microchipNumber: String?,
    @field:Schema(description = "특징 설명", nullable = true, example = "파란 목줄")
    val description: String?,
    @field:Schema(description = "사진 목록(표시 순서대로)")
    val images: List<PetImageRef>,
    @field:Schema(description = "등록 시각", example = "2026-11-09T10:15:30+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "마지막 수정 시각", example = "2026-11-09T11:20:00+09:00")
    val modDttm: OffsetDateTime?,
) {
    companion object {
        fun from(detail: PetDetail) = with(detail.pet) {
            PetDetailResponse(
                petSeq!!, name, detail.classification, gender, detail.images.firstOrNull()?.imageUrl,
                age, weight, isNeutered, microchipNumber, description, detail.images, regDttm, modDttm,
            )
        }
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class PetPageSuccessSchema : SuccessResponseSchema<PageResponse<PetSummaryResponse>>()
abstract class PetDetailSuccessSchema : SuccessResponseSchema<PetDetailResponse>()
abstract class CreatedPetSuccessSchema : SuccessResponseSchema<CreatedPetResponse>()
