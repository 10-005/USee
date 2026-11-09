package com.ssafy.animal.core.missing.domain

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

enum class MissingGender { MALE, FEMALE, UNKNOWN }

enum class MissingStatus { ACTIVE, CLOSED }

/** DB 명세 15. missing_reports. name·pet_seq는 없다(Q465·Q466). CLOSED는 읽기 전용(Q425) */
@Entity
@Table(name = "missing_reports")
class MissingReport(
    userSeq: Int,
    combinationSeq: Int,
    gender: MissingGender,
    age: String?,
    weight: String?,
    isNeutered: Boolean?,
    microchipNumber: String?,
    description: String?,
    address: String,
    latitude: Double,
    longitude: Double,
    occurredDttm: OffsetDateTime,
    districtCode: String?,
    contactPhone: String,
    isPhonePublic: Boolean,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var missingReportSeq: Int? = null
        protected set

    @Column(nullable = false)
    var userSeq: Int = userSeq
        protected set

    @Column(nullable = false)
    var combinationSeq: Int = combinationSeq

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var gender: MissingGender = gender

    @Column(length = 30)
    var age: String? = age

    @Column(length = 30)
    var weight: String? = weight

    var isNeutered: Boolean? = isNeutered

    @Column(length = 30)
    var microchipNumber: String? = microchipNumber

    @Column(columnDefinition = "text")
    var description: String? = description

    @Column(nullable = false, length = 255)
    var address: String = address

    @Column(nullable = false)
    var latitude: Double = latitude

    @Column(nullable = false)
    var longitude: Double = longitude

    @Column(nullable = false)
    var occurredDttm: OffsetDateTime = occurredDttm

    @Column(length = 5)
    var districtCode: String? = districtCode

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var status: MissingStatus = MissingStatus.ACTIVE
        protected set

    @Column(nullable = false, length = 30)
    var contactPhone: String = contactPhone

    @Column(nullable = false)
    var isPhonePublic: Boolean = isPhonePublic

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var modUsrSeq: Int? = null
        protected set

    fun touchedBy(userSeq: Int) {
        modUsrSeq = userSeq
    }

    fun changeStatus(status: MissingStatus, userSeq: Int) {
        this.status = status
        modUsrSeq = userSeq
    }

    fun delete(userSeq: Int) {
        isDeleted = true
        modUsrSeq = userSeq
    }
}

/** DB 명세 16. missing_report_images. display_order 1이 대표 */
@Entity
@Table(name = "missing_report_images")
class MissingReportImage(
    missingReportSeq: Int,
    imageSeq: Long,
    displayOrder: Short,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var missingReportImageSeq: Long? = null
        protected set

    @Column(nullable = false)
    var missingReportSeq: Int = missingReportSeq
        protected set

    @Column(nullable = false)
    var imageSeq: Long = imageSeq
        protected set

    @Column(nullable = false)
    var displayOrder: Short = displayOrder
        protected set
}
