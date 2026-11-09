package com.ssafy.animal.core.animal.application.port.`in`

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.core.animal.domain.AdoptionStatus
import com.ssafy.animal.core.animal.domain.AnimalBasics
import com.ssafy.animal.core.animal.domain.AnimalStatus
import com.ssafy.animal.core.animal.domain.EndReason
import com.ssafy.animal.core.animal.domain.Gender
import com.ssafy.animal.core.animal.domain.ProtectionStatus
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional

/** 11장 보호동물. 반환값은 응답 형태 그대로라 컨트롤러가 바로 내린다 */
interface ShelterAnimalUseCase {
    /** 11.1 */
    fun search(condition: AnimalSearchCondition, pageable: Pageable): Page<AnimalCard>

    /** 11.2 앱(USER) 상세 */
    fun getForUser(userSeq: Long, animalSeq: Int): AnimalDetail

    /** 11.3 */
    fun searchMine(shelter: AuthUser, condition: MyAnimalSearchCondition, pageable: Pageable): MyAnimalPage

    /** 11.4 */
    fun register(shelter: AuthUser, command: RegisterAnimalCommand): RegisteredAnimal

    /** 11.5. 응답은 11.10 상세 */
    fun update(shelter: AuthUser, animalSeq: Int, command: UpdateAnimalCommand): ShelterAnimalDetail

    /** 11.6 */
    fun manage(shelter: AuthUser, animalSeq: Int, command: ManageAnimalCommand): ManagedAnimal

    /** 11.9 */
    fun delete(shelter: AuthUser, animalSeq: Int)

    /** 11.10 보호소 웹 상세 */
    fun getForShelter(shelter: AuthUser, animalSeq: Int): ShelterAnimalDetail
}

data class AnimalSearchCondition(
    val status: AnimalStatus? = null,
    val regFrom: LocalDate? = null,
    val regTo: LocalDate? = null,
    val districtCode: String? = null,
    val speciesSeq: Int? = null,
    val gender: Gender? = null,
    val isNeutered: Boolean? = null,
    val keyword: String? = null,
)

enum class RegistrationPeriod(val days: Long) { RECENT_7D(7), RECENT_30D(30), RECENT_90D(90) }

data class MyAnimalSearchCondition(
    val status: AnimalStatus? = null,
    val period: RegistrationPeriod? = null,
    val speciesSeq: Int? = null,
    val gender: Gender? = null,
    val isNeutered: Boolean? = null,
)

/** 11.4. imageSeqs는 1~3장 */
data class RegisterAnimalCommand(
    val rescueSeq: Int?,
    val basics: AnimalBasics,
    val noticeStartDate: LocalDate?,
    val noticeEndDate: LocalDate?,
    val adoptionStatus: AdoptionStatus?,
    val imageSeqs: List<Long>,
)

/** 11.5. 필드 미전달은 null, 명시적 null은 Optional.empty()(3.2 UpdateMyProfileCommand와 같은 방식). imageSeqs는 전체 최종 배열 */
data class UpdateAnimalCommand(
    val combinationSeq: Int? = null,
    val gender: Gender? = null,
    val age: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val weight: Optional<String>? = null,
    val microchipNumber: Optional<String>? = null,
    val registrationNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val healthCheck: Optional<String>? = null,
    val vaccinationStatus: Optional<String>? = null,
    val imageSeqs: List<Long>? = null,
)

/** 11.6. status별 추가 입력 검증은 서비스가 한다 */
data class ManageAnimalCommand(
    val noticeStartDate: LocalDate? = null,
    val noticeEndDate: LocalDate? = null,
    val status: ProtectionStatus? = null,
    val adoptionStatus: AdoptionStatus? = null,
    val endReason: EndReason? = null,
    val recipientName: String? = null,
    val recipientPhone: String? = null,
    val reason: String? = null,
    val memo: String? = null,
)

data class RegisteredAnimal(
    val animalSeq: Int,
    val protectionStatus: ProtectionStatus,
    val adoptionStatus: AdoptionStatus,
    val noticeStartDate: LocalDate,
    val noticeEndDate: LocalDate,
)

/** 목록 카드용 축약 분류(11.1 예시) */
data class ClassificationBrief(val combinationSeq: Int, val speciesName: String, val breedName: String, val colorName: String) {
    companion object {
        fun of(c: AnimalClassification) = ClassificationBrief(c.combinationSeq, c.speciesName, c.breedName, c.colorName)
    }
}

/** 1.4 AnimalClassification 전체형 */
data class ClassificationFull(
    val combinationSeq: Int,
    val speciesSeq: Int,
    val speciesName: String,
    val breedSeq: Int,
    val breedName: String,
    val colorSeq: Int,
    val colorName: String,
) {
    companion object {
        fun of(c: AnimalClassification) =
            ClassificationFull(c.combinationSeq, c.speciesSeq, c.speciesName, c.breedSeq, c.breedName, c.colorSeq, c.colorName)
    }
}

data class ShelterBrief(val shelterSeq: Int, val shelterName: String, val districtCode: String?)

data class ShelterContact(val shelterSeq: Int, val shelterName: String, val phone: String, val address: String)

data class ImageRef(val imageSeq: Long, val imageUrl: String?, val displayOrder: Int)

/** 11.1·11.3 목록 항목 */
data class AnimalCard(
    val animalSeq: Int,
    val classification: ClassificationBrief?,
    val gender: Gender,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val protectionStatus: ProtectionStatus,
    val adoptionStatus: AdoptionStatus,
    val noticeStartDate: LocalDate,
    val noticeEndDate: LocalDate,
    val shelter: ShelterBrief?,
    val representativeImageUrl: String?,
    val regDttm: OffsetDateTime?,
)

/** 11.3. statusCounts 키는 total과 파생 상태 다섯 개 */
data class MyAnimalPage(
    val statusCounts: Map<String, Long>,
    val page: PageResponse<AnimalCard>,
)

/** 구조 정보 5개. 구조 연결이 없으면 모두 null(11.2 [초안]) */
data class RescueInfo(
    val rescuedDttm: OffsetDateTime?,
    val rescueReasonCode: String?,
    val rescueAddress: String?,
    val rescueAddressDetail: String?,
    val rescueMemo: String?,
) {
    companion object {
        val EMPTY = RescueInfo(null, null, null, null, null)
    }
}

/** 11.2 */
data class AnimalDetail(
    val animalSeq: Int,
    val classification: ClassificationFull?,
    val gender: Gender,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val protectionStatus: ProtectionStatus,
    val adoptionStatus: AdoptionStatus,
    val noticeStartDate: LocalDate,
    val noticeEndDate: LocalDate,
    val shelter: ShelterContact?,
    val representativeImageUrl: String?,
    val regDttm: OffsetDateTime?,
    val modDttm: OffsetDateTime?,
    val rescueSeq: Int?,
    val microchipNumber: String?,
    val registrationNumber: String?,
    val description: String?,
    val healthCheck: String?,
    val vaccinationStatus: String?,
    val endReason: EndReason?,
    val protectionEndedDttm: OffsetDateTime?,
    val rescueInfo: RescueInfo,
    val images: List<ImageRef>,
    val isBookmarked: Boolean,
)

data class ReturnInfo(
    val returnSeq: Long,
    val recipientName: String,
    val recipientPhone: String,
    val returnedUserSeq: Int?,
    val returnedDttm: OffsetDateTime?,
)

data class AdoptionInfo(
    val completedApplicationSeq: Long?,
    val applicantName: String?,
    val reason: String?,
    val memo: String?,
    val completedDttm: OffsetDateTime?,
)

/** 11.10(11.5 응답 겸) */
data class ShelterAnimalDetail(
    val animalSeq: Int,
    val classification: ClassificationFull?,
    val gender: Gender,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val microchipNumber: String?,
    val registrationNumber: String?,
    val description: String?,
    val healthCheck: String?,
    val vaccinationStatus: String?,
    val protectionStatus: ProtectionStatus,
    val adoptionStatus: AdoptionStatus,
    val noticeStartDate: LocalDate,
    val noticeEndDate: LocalDate,
    val endReason: EndReason?,
    val protectionEndedDttm: OffsetDateTime?,
    val rescueSeq: Int?,
    val rescueInfo: RescueInfo,
    val images: List<ImageRef>,
    val returnInfo: ReturnInfo?,
    val adoptionInfo: AdoptionInfo?,
    val applicationCounts: Map<String, Long>,
    val regDttm: OffsetDateTime?,
    val modDttm: OffsetDateTime?,
)

/** 11.6 */
data class ManagedAnimal(
    val animalSeq: Int,
    val protectionStatus: ProtectionStatus,
    val adoptionStatus: AdoptionStatus,
    val noticeStartDate: LocalDate,
    val noticeEndDate: LocalDate,
    val endReason: EndReason?,
    val protectionEndedDttm: OffsetDateTime?,
    val returnInfo: ReturnInfo?,
    val adoptionInfo: AdoptionInfo?,
    val autoRejectedApplications: Int,
)
