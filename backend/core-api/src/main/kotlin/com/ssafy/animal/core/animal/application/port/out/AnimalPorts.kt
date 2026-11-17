package com.ssafy.animal.core.animal.application.port.out

import com.ssafy.animal.core.animal.application.port.`in`.AdoptionTarget
import com.ssafy.animal.core.animal.domain.AnimalReturn
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.Gender
import com.ssafy.animal.core.animal.domain.ShelterAnimal
import com.ssafy.animal.core.animal.domain.ShelterAnimalImage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

interface ShelterAnimalRepository {
    fun save(animal: ShelterAnimal): ShelterAnimal

    /** 변경을 바로 반영한다. 상태 조합 CHECK 위반(경합)은 409 ANIMAL_INVALID_STATUS_COMBINATION */
    fun flush()

    /** 비삭제만 */
    fun findActive(animalSeq: Int): ShelterAnimal?

    /** 비삭제만, SELECT ... FOR UPDATE */
    fun findActiveForUpdate(animalSeq: Int): ShelterAnimal?

    /** 삭제 포함(과거 신청 요약용) */
    fun findAllById(animalSeqs: Collection<Int>): List<ShelterAnimal>

    /** 비삭제만. 정렬은 regDttm만(11.1·11.3) */
    fun search(filter: AnimalFilter, pageable: Pageable): Page<ShelterAnimal>

    /** 비삭제만, filter.status는 무시하고 파생 상태별로 센다(11.3 statusCounts) */
    fun countByStatus(filter: AnimalFilter): Map<AnimalStatus, Long>

    /** displayOrder 순 */
    fun findImages(animalSeq: Int): List<ShelterAnimalImage>

    /** 동물별 첫 번째 사진(displayOrder 최소)의 imageSeq */
    fun findRepresentativeImageSeqs(animalSeqs: Collection<Int>): Map<Int, Long>

    /** 연결을 모두 지우고 주어진 순서(1부터)로 다시 만든 뒤 flush 한다 */
    fun replaceImages(animalSeq: Int, imageSeqs: List<Long>)

    fun findReturn(animalSeq: Int): AnimalReturn?

    fun saveReturn(animalReturn: AnimalReturn): AnimalReturn
}

/** 11.1·11.3 공통 조건. null은 조건 없음 */
data class AnimalFilter(
    val shelterSeqs: Collection<Int>? = null,
    val status: AnimalStatus? = null,
    val regFrom: OffsetDateTime? = null,
    /** 미포함 */
    val regBefore: OffsetDateTime? = null,
    val combinationSeqs: Collection<Int>? = null,
    val gender: Gender? = null,
    val isNeutered: Boolean? = null,
    val keyword: String? = null,
)

/** 다른 도메인 테이블 읽기(엔티티 import 금지라 native). 쓰기는 하지 않는다 */
interface AnimalReferenceReader {
    /** 삭제 포함. 없으면 null */
    fun findRescue(rescueSeq: Int): RescueRow?

    /** 삭제된 보호소도 표시는 한다 */
    fun findShelters(shelterSeqs: Collection<Int>): Map<Int, ShelterRow>

    /** 소재지(shelters.district_code) 기준(11.1) */
    fun findShelterSeqsInDistrict(districtCode: String): List<Int>

    /** 축종 필터(combination → breed → species) */
    fun findCombinationSeqsOfSpecies(speciesSeq: Int): List<Int>

    /** 매칭 이벤트 페이로드용 S3 키(Q432) */
    fun findImageKeys(imageSeqs: Collection<Long>): List<String>

    fun isBookmarked(userSeq: Int, animalSeq: Int): Boolean

    /** 반환 연결(11.6 RETURNED): 같은 전화번호의 활성 USER */
    fun findActiveUserSeqsByPhone(phone: String): List<Int>
}

data class RescueRow(
    val rescueSeq: Int,
    val status: String,
    val isDeleted: Boolean,
    val rescuedDttm: OffsetDateTime?,
    val rescueReasonCode: String?,
    val rescueAddress: String?,
    val rescueAddressDetail: String?,
    val rescueMemo: String?,
)

data class ShelterRow(
    val shelterSeq: Int,
    val shelterName: String,
    val phone: String,
    val address: String,
    val districtCode: String?,
)

/**
 * 보호동물 쪽에서 필요한 입양 신청 처리. adoption 패키지가 구현한다(신청서 엔티티·알림은 adoption 소유).
 * 건수 맵 키는 total·SUBMITTED·APPROVED·ADOPTED·REJECTED(12.16 규칙, DRAFT·CANCELED 제외)
 */
interface AnimalApplicationsPort {
    /** DRAFT/SUBMITTED/APPROVED가 하나라도 있는지(11.9) */
    fun hasActive(animalSeq: Int): Boolean

    fun countReceived(animalSeq: Int): Map<String, Long>

    fun findApplicantName(applicationSeq: Long): String?

    /**
     * 활성 신청을 자동 REJECTED(reviewedUsrSeq=null)하고 신청자마다 ADOPTION_REJECTED 알림. 처리 건수를 돌려준다.
     * 보호동물을 잠근 트랜잭션 안에서 부른다
     */
    fun rejectActive(target: AdoptionTarget, reason: String, exceptApplicationSeq: Long? = null): Int
}
