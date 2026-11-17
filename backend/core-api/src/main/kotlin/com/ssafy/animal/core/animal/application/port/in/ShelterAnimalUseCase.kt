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
import io.swagger.v3.oas.annotations.media.Schema
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
    @field:Schema(description = "보호동물 번호", example = "700")
    val animalSeq: Int,
    @field:Schema(description = "항상 PROTECTING", example = "PROTECTING")
    val protectionStatus: ProtectionStatus,
    @field:Schema(description = "입양 상태. AVAILABLE=입양 가능, UNAVAILABLE=입양 불가(기본)", example = "UNAVAILABLE")
    val adoptionStatus: AdoptionStatus,
    @field:Schema(description = "공고 시작일. 생략했으면 등록일(KST)", example = "2026-10-29")
    val noticeStartDate: LocalDate,
    @field:Schema(description = "공고 종료일. 생략했으면 시작일+10일", example = "2026-11-08")
    val noticeEndDate: LocalDate,
)

/** 목록 카드용 축약 분류(11.1 예시) */
data class ClassificationBrief(
    @field:Schema(description = "종·품종·색상 조합 번호", example = "88")
    val combinationSeq: Int,
    @field:Schema(description = "축종 이름", example = "개")
    val speciesName: String,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "색상 이름", example = "흰색")
    val colorName: String,
) {
    companion object {
        fun of(c: AnimalClassification) = ClassificationBrief(c.combinationSeq, c.speciesName, c.breedName, c.colorName)
    }
}

/** 1.4 AnimalClassification 전체형 */
data class ClassificationFull(
    @field:Schema(description = "종·품종·색상 조합 번호", example = "88")
    val combinationSeq: Int,
    @field:Schema(description = "축종 번호", example = "1")
    val speciesSeq: Int,
    @field:Schema(description = "축종 이름", example = "개")
    val speciesName: String,
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "색상 번호", example = "3")
    val colorSeq: Int,
    @field:Schema(description = "색상 이름", example = "흰색")
    val colorName: String,
) {
    companion object {
        fun of(c: AnimalClassification) =
            ClassificationFull(c.combinationSeq, c.speciesSeq, c.speciesName, c.breedSeq, c.breedName, c.colorSeq, c.colorName)
    }
}

data class ShelterBrief(
    @field:Schema(description = "보호소 번호", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "보호소 이름", example = "대구동물보호센터")
    val shelterName: String,
    @field:Schema(description = "보호소 소재지 시군구 코드(5자리)", nullable = true, example = "27110")
    val districtCode: String?,
)

data class ShelterContact(
    @field:Schema(description = "보호소 번호. 채팅방 생성(14.2)에 쓴다", example = "7")
    val shelterSeq: Int,
    @field:Schema(description = "보호소 이름", example = "대구동물보호센터")
    val shelterName: String,
    @field:Schema(description = "보호소 전화번호", example = "0531234567")
    val phone: String,
    @field:Schema(description = "보호소 주소", example = "대구광역시 중구 동덕로 123")
    val address: String,
)

@Schema(name = "ShelterAnimalImageRef", description = "보호동물 사진")
data class ImageRef(
    @field:Schema(description = "이미지 번호", example = "1701")
    val imageSeq: Long,
    @field:Schema(description = "이미지 URL. 이미지 행이 없으면 null", nullable = true, example = "https://cdn.example.com/images/1701.jpg")
    val imageUrl: String?,
    @field:Schema(description = "표시 순서(1부터)", example = "1")
    val displayOrder: Int,
)

/** 11.1·11.3 목록 항목 */
data class AnimalCard(
    @field:Schema(description = "보호동물 번호", example = "700")
    val animalSeq: Int,
    @field:Schema(description = "종·품종·색상 분류(이름 포함 축약형). 조합을 찾지 못하면 null", nullable = true)
    val classification: ClassificationBrief?,
    @field:Schema(description = "성별. MALE=수컷, FEMALE=암컷, UNKNOWN=모름", example = "UNKNOWN")
    val gender: Gender,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "1살 추정")
    val age: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null=모름", nullable = true)
    val isNeutered: Boolean?,
    @field:Schema(description = "몸무게(자유 텍스트)", nullable = true, example = "3kg")
    val weight: String?,
    @field:Schema(description = "보호 상태. PROTECTING=보호 중, RETURNED=반환 완료, ADOPTED=입양 완료, ENDED=보호 종료", example = "PROTECTING")
    val protectionStatus: ProtectionStatus,
    @field:Schema(description = "입양 상태. AVAILABLE=입양 가능, UNAVAILABLE=입양 불가, ADOPTED=입양 완료", example = "UNAVAILABLE")
    val adoptionStatus: AdoptionStatus,
    @field:Schema(description = "공고 시작일", example = "2026-10-29")
    val noticeStartDate: LocalDate,
    @field:Schema(description = "공고 종료일", example = "2026-11-08")
    val noticeEndDate: LocalDate,
    @field:Schema(description = "보호소 { shelterSeq, shelterName, districtCode }. 보호소 행이 없으면 null", nullable = true)
    val shelter: ShelterBrief?,
    @field:Schema(description = "대표 사진 URL(첫 번째 사진). 없으면 null", nullable = true, example = "https://cdn.example.com/images/1701.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "등록 시각", example = "2026-10-29T12:00:00+09:00")
    val regDttm: OffsetDateTime?,
)

/** 11.3. statusCounts 키는 total과 파생 상태 다섯 개 */
data class MyAnimalPage(
    @field:Schema(description = "상태 탭별 건수. 키는 total·PROTECTING_AVAILABLE·PROTECTING_UNAVAILABLE·RETURNED·ADOPTED·ENDED. status를 뺀 나머지 필터만 적용해 센다. total은 다섯 값의 합", example = """{"total":31,"PROTECTING_AVAILABLE":9,"PROTECTING_UNAVAILABLE":12,"RETURNED":4,"ADOPTED":5,"ENDED":1}""")
    val statusCounts: Map<String, Long>,
    @field:Schema(description = "현재 status 탭의 목록. totalElements는 statusCounts의 해당 값과 같다(status 생략이면 total)")
    val page: PageResponse<AnimalCard>,
)

/** 구조 정보 5개. 구조 연결이 없으면 모두 null(11.2 [초안]) */
data class RescueInfo(
    @field:Schema(description = "실제 구조 일시", nullable = true, example = "2026-10-29T09:30:00+09:00")
    val rescuedDttm: OffsetDateTime?,
    @field:Schema(description = "구조 사유. WANDERING=배회/유기 의심, INJURY_ILLNESS=부상/질병, DANGEROUS_PLACE=위험 장소, OTHER=기타", nullable = true, example = "WANDERING")
    val rescueReasonCode: String?,
    @field:Schema(description = "구조 장소", nullable = true, example = "대구광역시 중구 동인동 12")
    val rescueAddress: String?,
    @field:Schema(description = "상세 장소", nullable = true)
    val rescueAddressDetail: String?,
    @field:Schema(description = "구조 메모", nullable = true)
    val rescueMemo: String?,
) {
    companion object {
        val EMPTY = RescueInfo(null, null, null, null, null)
    }
}

/** 11.2 */
data class AnimalDetail(
    @field:Schema(description = "보호동물 번호", example = "700")
    val animalSeq: Int,
    @field:Schema(description = "종·품종·색상 분류(1.4 AnimalClassification 전체형). 조합을 찾지 못하면 null", nullable = true)
    val classification: ClassificationFull?,
    @field:Schema(description = "성별. MALE=수컷, FEMALE=암컷, UNKNOWN=모름", example = "UNKNOWN")
    val gender: Gender,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "1살 추정")
    val age: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null=모름", nullable = true)
    val isNeutered: Boolean?,
    @field:Schema(description = "몸무게(자유 텍스트)", nullable = true, example = "3kg")
    val weight: String?,
    @field:Schema(description = "보호 상태. PROTECTING=보호 중, RETURNED=반환 완료, ADOPTED=입양 완료, ENDED=보호 종료", example = "PROTECTING")
    val protectionStatus: ProtectionStatus,
    @field:Schema(description = "입양 상태. AVAILABLE=입양 가능, UNAVAILABLE=입양 불가, ADOPTED=입양 완료", example = "UNAVAILABLE")
    val adoptionStatus: AdoptionStatus,
    @field:Schema(description = "공고 시작일", example = "2026-10-29")
    val noticeStartDate: LocalDate,
    @field:Schema(description = "공고 종료일", example = "2026-11-08")
    val noticeEndDate: LocalDate,
    @field:Schema(description = "보호소 연락처 { shelterSeq, shelterName, phone, address }. 보호소 행이 없으면 null", nullable = true)
    val shelter: ShelterContact?,
    @field:Schema(description = "대표 사진 URL(첫 번째 사진). 없으면 null", nullable = true, example = "https://cdn.example.com/images/1701.jpg")
    val representativeImageUrl: String?,
    @field:Schema(description = "등록 시각", example = "2026-10-29T12:00:00+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "마지막 수정 시각. 수정 전이면 null", nullable = true, example = "2026-11-05T14:00:00+09:00")
    val modDttm: OffsetDateTime?,
    @field:Schema(description = "연결된 구조 기록 번호. 직접 등록이면 null", nullable = true, example = "301")
    val rescueSeq: Int?,
    @field:Schema(description = "마이크로칩 번호", nullable = true)
    val microchipNumber: String?,
    @field:Schema(description = "정부 동물등록번호. 없으면 null(화면 '미등록')", nullable = true)
    val registrationNumber: String?,
    @field:Schema(description = "특징·성격", nullable = true, example = "사람을 잘 따름")
    val description: String?,
    @field:Schema(description = "건강 검진 내용", nullable = true, example = "기초 검진 완료")
    val healthCheck: String?,
    @field:Schema(description = "예방접종 상태", nullable = true, example = "예방접종 확인 중")
    val vaccinationStatus: String?,
    @field:Schema(description = "보호 종료 사유. NATURAL_DEATH=자연사, EUTHANIZED=안락사, OTHER=기타, RETURNED=반환, ADOPTED=입양. 보호 중이면 null", nullable = true)
    val endReason: EndReason?,
    @field:Schema(description = "보호 종료 시각(RETURNED·ADOPTED·ENDED). 보호 중이면 null", nullable = true)
    val protectionEndedDttm: OffsetDateTime?,
    @field:Schema(description = "구조 정보. 구조 연결이 없거나 구조가 삭제됐으면 다섯 필드 모두 null(객체는 항상 있음)")
    val rescueInfo: RescueInfo,
    @field:Schema(description = "사진 목록(displayOrder 순, 1~3장)")
    val images: List<ImageRef>,
    @get:Schema(name = "isBookmarked", description = "내가 북마크했는지", example = "false")
    val isBookmarked: Boolean,
)

data class ReturnInfo(
    @field:Schema(description = "반환 기록 번호", example = "801")
    val returnSeq: Long,
    @field:Schema(description = "반환받은 사람 이름", example = "김가람")
    val recipientName: String,
    @field:Schema(description = "반환받은 사람 연락처", example = "01012345678")
    val recipientPhone: String,
    @field:Schema(description = "같은 전화번호의 활성 USER가 정확히 1명이면 그 회원 번호, 아니면 null(소유권 검증 아님)", nullable = true, example = "17")
    val returnedUserSeq: Int?,
    @field:Schema(description = "반환 처리 시각. 다시 지정했으면 마지막 지정 시각", example = "2026-11-05T14:00:00+09:00")
    val returnedDttm: OffsetDateTime?,
)

data class AdoptionInfo(
    @field:Schema(description = "12.11로 채택한 신청서 번호. 외부 입양(11.6)이면 null", nullable = true, example = "901")
    val completedApplicationSeq: Long?,
    @field:Schema(description = "채택한 신청서의 신청자 이름. 외부 입양이면 null", nullable = true, example = "김가람")
    val applicantName: String?,
    @field:Schema(description = "11.6으로 입력한 처리 사유. 12.11만 거쳤으면 null", nullable = true, example = "보호소 방문 상담 후 직접 인계")
    val reason: String?,
    @field:Schema(description = "11.6으로 입력한 비고", nullable = true)
    val memo: String?,
    @field:Schema(description = "입양 완료 시각(보호 종료 시각). 보호 중으로 되돌렸으면 null", nullable = true, example = "2026-11-05T14:00:00+09:00")
    val completedDttm: OffsetDateTime?,
)

/** 11.10(11.5 응답 겸) */
data class ShelterAnimalDetail(
    @field:Schema(description = "보호동물 번호", example = "700")
    val animalSeq: Int,
    @field:Schema(description = "종·품종·색상 분류(1.4 AnimalClassification 전체형). 조합을 찾지 못하면 null", nullable = true)
    val classification: ClassificationFull?,
    @field:Schema(description = "성별. MALE=수컷, FEMALE=암컷, UNKNOWN=모름", example = "UNKNOWN")
    val gender: Gender,
    @field:Schema(description = "나이(자유 텍스트)", nullable = true, example = "1살 추정")
    val age: String?,
    @get:Schema(name = "isNeutered", description = "중성화 여부. null=모름", nullable = true)
    val isNeutered: Boolean?,
    @field:Schema(description = "몸무게(자유 텍스트)", nullable = true, example = "3kg")
    val weight: String?,
    @field:Schema(description = "마이크로칩 번호", nullable = true)
    val microchipNumber: String?,
    @field:Schema(description = "정부 동물등록번호. 없으면 null(화면 '미등록')", nullable = true)
    val registrationNumber: String?,
    @field:Schema(description = "특징·성격", nullable = true, example = "사람을 잘 따름")
    val description: String?,
    @field:Schema(description = "건강 검진 내용", nullable = true, example = "기초 검진 완료")
    val healthCheck: String?,
    @field:Schema(description = "예방접종 상태", nullable = true, example = "예방접종 확인 중")
    val vaccinationStatus: String?,
    @field:Schema(description = "보호 상태. PROTECTING=보호 중, RETURNED=반환 완료, ADOPTED=입양 완료, ENDED=보호 종료", example = "PROTECTING")
    val protectionStatus: ProtectionStatus,
    @field:Schema(description = "입양 상태. AVAILABLE=입양 가능, UNAVAILABLE=입양 불가, ADOPTED=입양 완료", example = "UNAVAILABLE")
    val adoptionStatus: AdoptionStatus,
    @field:Schema(description = "공고 시작일", example = "2026-10-29")
    val noticeStartDate: LocalDate,
    @field:Schema(description = "공고 종료일", example = "2026-11-08")
    val noticeEndDate: LocalDate,
    @field:Schema(description = "보호 종료 사유. NATURAL_DEATH=자연사, EUTHANIZED=안락사, OTHER=기타, RETURNED=반환, ADOPTED=입양. 보호 중이면 null", nullable = true)
    val endReason: EndReason?,
    @field:Schema(description = "보호 종료 시각(RETURNED·ADOPTED·ENDED). 보호 중이면 null", nullable = true)
    val protectionEndedDttm: OffsetDateTime?,
    @field:Schema(description = "연결된 구조 기록 번호. 직접 등록이면 null", nullable = true, example = "301")
    val rescueSeq: Int?,
    @field:Schema(description = "구조 정보. 구조 연결이 없거나 구조가 삭제됐으면 다섯 필드 모두 null(객체는 항상 있음)")
    val rescueInfo: RescueInfo,
    @field:Schema(description = "사진 목록(displayOrder 순, 1~3장)")
    val images: List<ImageRef>,
    @field:Schema(description = "반환 기록. 행이 있으면 현재 상태와 관계없이 채운다(복귀 이력 포함). 없으면 null", nullable = true)
    val returnInfo: ReturnInfo?,
    @field:Schema(description = "입양 정보. ADOPTED이거나 입양 사유·채택 신청서가 남아 있으면 채운다. 아니면 null", nullable = true)
    val adoptionInfo: AdoptionInfo?,
    @field:Schema(description = "이 동물의 입양 신청 상태별 건수(12.16과 같은 규칙). 키는 total·SUBMITTED·APPROVED·ADOPTED·REJECTED. DRAFT·CANCELED 제외", example = """{"total":5,"SUBMITTED":0,"APPROVED":0,"ADOPTED":1,"REJECTED":4}""")
    val applicationCounts: Map<String, Long>,
    @field:Schema(description = "등록 시각", example = "2026-10-29T12:00:00+09:00")
    val regDttm: OffsetDateTime?,
    @field:Schema(description = "마지막 수정 시각. 수정 전이면 null", nullable = true, example = "2026-11-05T14:00:00+09:00")
    val modDttm: OffsetDateTime?,
)

/** 11.6 */
data class ManagedAnimal(
    @field:Schema(description = "보호동물 번호", example = "700")
    val animalSeq: Int,
    @field:Schema(description = "보호 상태. PROTECTING=보호 중, RETURNED=반환 완료, ADOPTED=입양 완료, ENDED=보호 종료", example = "PROTECTING")
    val protectionStatus: ProtectionStatus,
    @field:Schema(description = "입양 상태. AVAILABLE=입양 가능, UNAVAILABLE=입양 불가, ADOPTED=입양 완료", example = "UNAVAILABLE")
    val adoptionStatus: AdoptionStatus,
    @field:Schema(description = "공고 시작일", example = "2026-10-29")
    val noticeStartDate: LocalDate,
    @field:Schema(description = "공고 종료일", example = "2026-11-08")
    val noticeEndDate: LocalDate,
    @field:Schema(description = "보호 종료 사유. NATURAL_DEATH=자연사, EUTHANIZED=안락사, OTHER=기타, RETURNED=반환, ADOPTED=입양. 보호 중이면 null", nullable = true)
    val endReason: EndReason?,
    @field:Schema(description = "보호 종료 시각(RETURNED·ADOPTED·ENDED). 보호 중이면 null", nullable = true)
    val protectionEndedDttm: OffsetDateTime?,
    @field:Schema(description = "반환 기록. protectionStatus가 RETURNED일 때만 채우고 아니면 null", nullable = true)
    val returnInfo: ReturnInfo?,
    @field:Schema(description = "입양 정보. protectionStatus가 ADOPTED일 때만 채우고 아니면 null", nullable = true)
    val adoptionInfo: AdoptionInfo?,
    @field:Schema(description = "이번 호출로 자동 거절된 진행 중 신청 수. 종료 상태로 바꾸지 않았으면 0", example = "2")
    val autoRejectedApplications: Int,
)
