package com.ssafy.animal.core.animal.application.port.`in`

import com.ssafy.animal.core.animal.domain.Gender

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
data class AnimalSummary(
    val animalSeq: Int,
    val speciesName: String?,
    val breedName: String?,
    val colorName: String?,
    val gender: Gender,
    val age: String?,
    val weight: String?,
    val isNeutered: Boolean?,
    val microchipNumber: String?,
    val registrationNumber: String?,
    val representativeImageUrl: String?,
    val shelterSeq: Int,
    val shelterName: String?,
    val shelterAddress: String?,
)
