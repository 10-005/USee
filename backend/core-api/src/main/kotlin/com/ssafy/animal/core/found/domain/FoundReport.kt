package com.ssafy.animal.core.found.domain

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
import java.time.OffsetDateTime

enum class Gender { MALE, FEMALE, UNKNOWN }

enum class FoundStatus { ACTIVE, CLOSED }

/** 동물 속성·위치·연락처. 등록과 수정이 같은 묶음을 쓴다 */
data class FoundReportContent(
    val combinationSeq: Int,
    val gender: Gender,
    val age: String?,
    val weight: String?,
    val isNeutered: Boolean?,
    val microchipNumber: String?,
    val description: String?,
    val reporterPhone: String,
    val isPhonePublic: Boolean,
    val landmarkDescription: String?,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val occurredDttm: OffsetDateTime,
)

/**
 * DB 명세 found_reports. 담당 보호소는 등록 때 서버가 정하고 바꾸지 않는다(수동 재배정 API 없음, 0.2-2).
 * 이 테이블에는 reg_usr_seq가 없다(작성자는 user_seq).
 */
@Entity
@Table(name = "found_reports")
class FoundReport(
    userSeq: Int,
    shelterSeq: Int,
    content: FoundReportContent,
    districtCode: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var foundReportSeq: Int? = null
        protected set

    @Column(nullable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false)
    var shelterSeq: Int = shelterSeq
        protected set

    @Column(nullable = false)
    var combinationSeq: Int = content.combinationSeq
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var gender: Gender = content.gender
        protected set

    @Column(length = 30)
    var age: String? = content.age
        protected set

    @Column(length = 30)
    var weight: String? = content.weight
        protected set

    var isNeutered: Boolean? = content.isNeutered
        protected set

    @Column(length = 30)
    var microchipNumber: String? = content.microchipNumber
        protected set

    var description: String? = content.description
        protected set

    @Column(nullable = false, length = 30)
    var reporterPhone: String = content.reporterPhone
        protected set

    @Column(nullable = false)
    var isPhonePublic: Boolean = content.isPhonePublic
        protected set

    var landmarkDescription: String? = content.landmarkDescription
        protected set

    @Column(nullable = false, length = 255)
    var address: String = content.address
        protected set

    @Column(nullable = false)
    var latitude: Double = content.latitude
        protected set

    @Column(nullable = false)
    var longitude: Double = content.longitude
        protected set

    @Column(nullable = false)
    var occurredDttm: OffsetDateTime = content.occurredDttm
        protected set

    @Column(length = 5)
    var districtCode: String? = districtCode
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var status: FoundStatus = FoundStatus.ACTIVE
        protected set

    var closedReason: String? = null
        protected set

    var closedDttm: OffsetDateTime? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var modUsrSeq: Int? = null
        protected set

    val content: FoundReportContent
        get() = FoundReportContent(
            combinationSeq, gender, age, weight, isNeutered, microchipNumber, description, reporterPhone,
            isPhonePublic, landmarkDescription, address, latitude, longitude, occurredDttm,
        )

    /** 8.5. CLOSED 검사는 서비스가 먼저 한다(Q425) */
    fun update(content: FoundReportContent, districtCode: String?, modUsrSeq: Int) {
        combinationSeq = content.combinationSeq
        gender = content.gender
        age = content.age
        weight = content.weight
        isNeutered = content.isNeutered
        microchipNumber = content.microchipNumber
        description = content.description
        reporterPhone = content.reporterPhone
        isPhonePublic = content.isPhonePublic
        landmarkDescription = content.landmarkDescription
        address = content.address
        latitude = content.latitude
        longitude = content.longitude
        occurredDttm = content.occurredDttm
        this.districtCode = districtCode
        this.modUsrSeq = modUsrSeq
    }

    /** 8.6. CLOSED는 종료 시각 필수·사유 선택, ACTIVE 재개 시 둘 다 NULL(DB CHECK ck_found_status_closure) */
    fun changeStatus(status: FoundStatus, closedReason: String?, now: OffsetDateTime, modUsrSeq: Int) {
        this.status = status
        this.closedReason = if (status == FoundStatus.CLOSED) closedReason else null
        this.closedDttm = if (status == FoundStatus.CLOSED) now else null
        this.modUsrSeq = modUsrSeq
    }

    /** 8.7 논리 삭제. 연결된 구조 기록은 건드리지 않는다 */
    fun delete(modUsrSeq: Int) {
        isDeleted = true
        this.modUsrSeq = modUsrSeq
    }
}

/** found_report_images. 사진 1~3장, 순서는 display_order(0부터) */
@Entity
@Table(name = "found_report_images")
class FoundReportImage(
    foundReportSeq: Int,
    imageSeq: Long,
    displayOrder: Short,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var foundReportImageSeq: Long? = null
        protected set

    @Column(nullable = false)
    var foundReportSeq: Int = foundReportSeq
        protected set

    @Column(nullable = false)
    var imageSeq: Long = imageSeq
        protected set

    @Column(nullable = false)
    var displayOrder: Short = displayOrder
        protected set
}
