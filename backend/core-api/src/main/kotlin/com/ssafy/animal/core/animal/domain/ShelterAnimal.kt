package com.ssafy.animal.core.animal.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime

enum class Gender { MALE, FEMALE, UNKNOWN }

enum class ProtectionStatus { PROTECTING, RETURNED, ADOPTED, ENDED }

enum class AdoptionStatus { AVAILABLE, UNAVAILABLE, ADOPTED }

/** RETURNED·ADOPTED는 서버가 정한다. 11.6 입력은 NATURAL_DEATH·EUTHANIZED·OTHER만 */
enum class EndReason { NATURAL_DEATH, EUTHANIZED, OTHER, RETURNED, ADOPTED }

/** 11.1·11.3 필터·탭용 파생 상태(Q473·Q475). DB의 두 상태 조합과 1:1 */
enum class AnimalStatus(val protectionStatus: ProtectionStatus, val adoptionStatus: AdoptionStatus) {
    PROTECTING_AVAILABLE(ProtectionStatus.PROTECTING, AdoptionStatus.AVAILABLE),
    PROTECTING_UNAVAILABLE(ProtectionStatus.PROTECTING, AdoptionStatus.UNAVAILABLE),
    RETURNED(ProtectionStatus.RETURNED, AdoptionStatus.UNAVAILABLE),
    ADOPTED(ProtectionStatus.ADOPTED, AdoptionStatus.ADOPTED),
    ENDED(ProtectionStatus.ENDED, AdoptionStatus.UNAVAILABLE),
    ;

    companion object {
        fun of(protectionStatus: ProtectionStatus, adoptionStatus: AdoptionStatus) =
            entries.first { it.protectionStatus == protectionStatus && it.adoptionStatus == adoptionStatus }
    }
}

/** DB 명세 20. shelter_animals. 이름 컬럼 없음, 첫 등록은 PROTECTING(Q479) */
@Entity
@Table(name = "shelter_animals")
class ShelterAnimal(
    shelterSeq: Int,
    rescueSeq: Int?,
    basics: AnimalBasics,
    noticeStartDate: LocalDate,
    noticeEndDate: LocalDate,
    adoptionStatus: AdoptionStatus,
    regUsrSeq: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var animalSeq: Int? = null
        protected set

    @Column(nullable = false)
    var shelterSeq: Int = shelterSeq
        protected set

    var rescueSeq: Int? = rescueSeq
        protected set

    /** 12.11로 채택한 신청서. 이 값은 12.11만 바꾼다(Q479) */
    var completedApplicationSeq: Long? = null
        protected set

    @Column(nullable = false)
    var combinationSeq: Int = basics.combinationSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var gender: Gender = basics.gender
        protected set

    @Column(length = 30)
    var age: String? = null
        protected set

    var isNeutered: Boolean? = null
        protected set

    @Column(length = 30)
    var microchipNumber: String? = null
        protected set

    @Column(length = 20)
    var registrationNumber: String? = null
        protected set

    @Column(length = 30)
    var weight: String? = null
        protected set

    var description: String? = null
        protected set

    var healthCheck: String? = null
        protected set

    var vaccinationStatus: String? = null
        protected set

    @Column(nullable = false)
    var noticeStartDate: LocalDate = noticeStartDate
        protected set

    @Column(nullable = false)
    var noticeEndDate: LocalDate = noticeEndDate
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var protectionStatus: ProtectionStatus = ProtectionStatus.PROTECTING
        protected set

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var endReason: EndReason? = null
        protected set

    var protectionEndedDttm: OffsetDateTime? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var adoptionStatus: AdoptionStatus = adoptionStatus
        protected set

    @Column(length = 200)
    var adoptionReason: String? = null
        protected set

    var adoptionMemo: String? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var regUsrSeq: Int? = regUsrSeq
        protected set

    var modUsrSeq: Int? = null
        protected set

    val status: AnimalStatus get() = AnimalStatus.of(protectionStatus, adoptionStatus)

    /** PROTECTING+AVAILABLE만 새 신청·제출을 받는다(Q437) */
    val isAdoptable: Boolean get() = !isDeleted && protectionStatus == ProtectionStatus.PROTECTING && adoptionStatus == AdoptionStatus.AVAILABLE

    init {
        applyBasics(basics)
    }

    fun updateBasics(basics: AnimalBasics, actorSeq: Int) {
        applyBasics(basics)
        modUsrSeq = actorSeq
    }

    private fun applyBasics(basics: AnimalBasics) {
        combinationSeq = basics.combinationSeq
        gender = basics.gender
        age = basics.age
        isNeutered = basics.isNeutered
        microchipNumber = basics.microchipNumber
        registrationNumber = basics.registrationNumber
        weight = basics.weight
        description = basics.description
        healthCheck = basics.healthCheck
        vaccinationStatus = basics.vaccinationStatus
    }

    fun changeNotice(start: LocalDate, end: LocalDate, actorSeq: Int) {
        noticeStartDate = start
        noticeEndDate = end
        modUsrSeq = actorSeq
    }

    // 아래 상태 전환은 DB CHECK(ck_shelter_status_pair·ck_shelter_protection_end)에 맞게 네 컬럼을 항상 함께 바꾼다

    /** 종료 상태에서 복귀해도 완료 FK·반환 행·입양 사유/비고는 이력으로 남긴다(Q479) */
    fun protect(adoptionStatus: AdoptionStatus, actorSeq: Int) {
        setStatus(ProtectionStatus.PROTECTING, adoptionStatus, null, null, actorSeq)
    }

    fun end(reason: EndReason, now: OffsetDateTime, actorSeq: Int) {
        setStatus(ProtectionStatus.ENDED, AdoptionStatus.UNAVAILABLE, reason, now, actorSeq)
    }

    fun markReturned(returnedDttm: OffsetDateTime, actorSeq: Int) {
        setStatus(ProtectionStatus.RETURNED, AdoptionStatus.UNAVAILABLE, EndReason.RETURNED, returnedDttm, actorSeq)
    }

    /** 11.6 외부 입양. completedApplicationSeq는 건드리지 않는다 */
    fun markAdoptedExternally(reason: String, memo: String?, now: OffsetDateTime, actorSeq: Int) {
        adoptionReason = reason
        adoptionMemo = memo
        setStatus(ProtectionStatus.ADOPTED, AdoptionStatus.ADOPTED, EndReason.ADOPTED, now, actorSeq)
    }

    /** 12.11 채택. 11.6으로 이미 ADOPTED면 상태·종료 시각은 그대로 두고 신청서만 연결한다 */
    fun completeAdoption(applicationSeq: Long, now: OffsetDateTime, actorSeq: Int) {
        completedApplicationSeq = applicationSeq
        if (protectionStatus == ProtectionStatus.ADOPTED) {
            modUsrSeq = actorSeq
            return
        }
        setStatus(ProtectionStatus.ADOPTED, AdoptionStatus.ADOPTED, EndReason.ADOPTED, now, actorSeq)
    }

    fun delete(actorSeq: Int) {
        isDeleted = true
        modUsrSeq = actorSeq
    }

    private fun setStatus(
        protectionStatus: ProtectionStatus,
        adoptionStatus: AdoptionStatus,
        endReason: EndReason?,
        endedDttm: OffsetDateTime?,
        actorSeq: Int,
    ) {
        this.protectionStatus = protectionStatus
        this.adoptionStatus = adoptionStatus
        this.endReason = endReason
        this.protectionEndedDttm = endedDttm
        this.modUsrSeq = actorSeq
    }
}

/** 11.4·11.5의 기초 정보(공고기간·상태 제외) */
data class AnimalBasics(
    val combinationSeq: Int,
    val gender: Gender,
    val age: String?,
    val isNeutered: Boolean?,
    val microchipNumber: String?,
    val registrationNumber: String?,
    val weight: String?,
    val description: String?,
    val healthCheck: String?,
    val vaccinationStatus: String?,
)

/** DB 명세 21. shelter_animal_images. 사진 수정은 전체 최종 배열로 받아 다시 만든다(Q424) */
@Entity
@Table(name = "shelter_animal_images")
class ShelterAnimalImage(
    animalSeq: Int,
    imageSeq: Long,
    displayOrder: Short,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var shelterAnimalImageSeq: Long? = null
        protected set

    @Column(nullable = false)
    var animalSeq: Int = animalSeq
        protected set

    @Column(nullable = false)
    var imageSeq: Long = imageSeq
        protected set

    @Column(nullable = false)
    var displayOrder: Short = displayOrder
        protected set
}

/** DB 명세 22. animal_returns. 동물당 1건, 재지정은 덮어쓰기(Q479). 소유권 검증 아님 */
@Entity
@Table(name = "animal_returns")
class AnimalReturn(
    animalSeq: Int,
    recipientName: String,
    recipientPhone: String,
    returnedUserSeq: Int?,
    regUsrSeq: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var returnSeq: Long? = null
        protected set

    @Column(nullable = false)
    var animalSeq: Int = animalSeq
        protected set

    var returnedUserSeq: Int? = returnedUserSeq
        protected set

    @Column(nullable = false, length = 100)
    var recipientName: String = recipientName
        protected set

    @Column(nullable = false, length = 30)
    var recipientPhone: String = recipientPhone
        protected set

    var regUsrSeq: Int? = regUsrSeq
        protected set

    var modUsrSeq: Int? = null
        protected set

    /** 반환 완료 시각: 재지정했으면 mod_dttm, 아니면 reg_dttm */
    val returnedDttm: OffsetDateTime? get() = modDttm ?: regDttm

    /** 값이 같아도 mod_dttm을 갱신해야 하므로(재지정 시각) 직접 찍어 UPDATE가 나가게 한다. flush 때 @PreUpdate가 다시 찍는다 */
    fun overwrite(recipientName: String, recipientPhone: String, returnedUserSeq: Int?, actorSeq: Int) {
        this.recipientName = recipientName
        this.recipientPhone = recipientPhone
        this.returnedUserSeq = returnedUserSeq
        this.modUsrSeq = actorSeq
        this.modDttm = OffsetDateTime.now()
    }
}
