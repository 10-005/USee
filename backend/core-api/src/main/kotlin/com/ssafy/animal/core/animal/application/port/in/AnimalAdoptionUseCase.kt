package com.ssafy.animal.core.animal.application.port.`in`

import com.ssafy.animal.core.animal.domain.Gender
import io.swagger.v3.oas.annotations.media.Schema

/**
 * adoption 패키지가 쓰는 보호동물 조회·변경. adoption은 ShelterAnimal 엔티티를 import하지 않고 이 포트만 쓴다.
 * 잠금 순서는 항상 보호동물 → 신청서(교착 방지).
 */
interface AnimalAdoptionUseCase {

    /** 비삭제 보호동물을 FOR UPDATE로 잠그고 읽는다. 없으면 404 ANIMAL_NOT_FOUND */
    fun lockForAdoption(animalSeq: Int): AdoptionTarget

    /** 소속 보호소 동물인지 확인(12.16 animalSeq). 아니면 404 ANIMAL_NOT_FOUND(Q434) */
    fun requireOwnedBy(shelterSeq: Int, animalSeq: Int)

    /** 12.11: lockForAdoption으로 잠근 뒤 부른다. 신청서 연결 + (아직 아니면) ADOPTED 전환 */
    fun completeAdoption(animalSeq: Int, applicationSeq: Long, actorSeq: Int)

    /** 12.2·12.3·12.9 AnimalSummary. 삭제된 동물도 과거 신청 표시용으로 채운다 */
    fun findSummaries(animalSeqs: Collection<Int>): Map<Int, AnimalSummary>
}

/** 신청 처리·알림 문구에 필요한 보호동물 정보 */
data class AdoptionTarget(
    val animalSeq: Int,
    val shelterSeq: Int,
    val shelterName: String,
    val breedName: String,
    val isAdoptable: Boolean,
    val completedApplicationSeq: Long?,
)

/** 12장 공통 AnimalSummary(Q458·Q459) */
@Schema(description = "입양 신청 화면의 보호동물 요약. 삭제된 보호동물도 과거 신청 표시용으로 채운다")
data class AnimalSummary(
    @field:Schema(description = "보호동물 번호", example = "301")
    val animalSeq: Int,
    @field:Schema(description = "종 이름", nullable = true, example = "개")
    val speciesName: String?,
    @field:Schema(description = "품종 이름", nullable = true, example = "말티즈")
    val breedName: String?,
    @field:Schema(description = "색상 이름", nullable = true, example = "흰색")
    val colorName: String?,
    @field:Schema(description = "성별. MALE: 수컷, FEMALE: 암컷, UNKNOWN: 모름", example = "MALE")
    val gender: Gender,
    @field:Schema(description = "나이(자유 입력)", nullable = true, example = "2살 추정")
    val age: String?,
    @field:Schema(description = "체중(자유 입력)", nullable = true, example = "3.2kg")
    val weight: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null이면 모름", nullable = true, example = "true")
    val isNeutered: Boolean?,
    @field:Schema(description = "마이크로칩 번호", nullable = true, example = "410100012345678")
    val microchipNumber: String?,
    @field:Schema(description = "동물 등록 번호", nullable = true, example = "4101000123456")
    val registrationNumber: String?,
    @field:Schema(description = "대표 사진 URL", nullable = true, example = "https://cdn.example.com/images/animal-301.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "보호소 이름", nullable = true, example = "행복동물보호센터")
    val shelterName: String?,
    @field:Schema(description = "보호소 주소", nullable = true, example = "인천광역시 중구 보호소로 1")
    val shelterAddress: String?,
)
