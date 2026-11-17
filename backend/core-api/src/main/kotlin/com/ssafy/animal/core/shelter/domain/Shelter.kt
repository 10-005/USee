package com.ssafy.animal.core.shelter.domain

import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.http.HttpStatus

/**
 * DB 명세 04. shelters. 식별정보(이름·주소·좌표·전화·시군구)는 Flyway 시드로만 넣고(Q412),
 * SHELTER 계정은 운영정보·이미지만 바꾼다(9.4). 생성자는 시드와 같은 식별정보(테스트용)
 */
@Entity
@Table(name = "shelters")
class Shelter(
    shelterName: String,
    address: String,
    latitude: Double,
    longitude: Double,
    phone: String,
    districtCode: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var shelterSeq: Int? = null
        protected set

    @Column(nullable = false, length = 150)
    var shelterName: String = shelterName
        protected set

    /** 내부 적재 키. 응답에 노출하지 않는다(9.2) */
    @Column(length = 100)
    var externalShelterCode: String? = null
        protected set

    @Column(nullable = false, length = 255)
    var address: String = address
        protected set

    @Column(nullable = false)
    var latitude: Double = latitude
        protected set

    @Column(nullable = false)
    var longitude: Double = longitude
        protected set

    @Column(nullable = false, length = 30)
    var phone: String = phone
        protected set

    var description: String? = null
        protected set

    var operatingHours: String? = null
        protected set

    var closedDays: String? = null
        protected set

    @Column(length = 5)
    var districtCode: String? = districtCode
        protected set

    var profileImageSeq: Long? = null
        protected set

    var backgroundImageSeq: Long? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    var regUsrSeq: Int? = null
        protected set

    var modUsrSeq: Int? = null
        protected set

    /** 9.4. 검증(이미지 소유)은 서비스가 끝낸 최종값만 넘긴다 */
    fun updateOperation(
        description: String?,
        operatingHours: String?,
        closedDays: String?,
        profileImageSeq: Long?,
        backgroundImageSeq: Long?,
        modUsrSeq: Int,
    ) {
        this.description = description
        this.operatingHours = operatingHours
        this.closedDays = closedDays
        this.profileImageSeq = profileImageSeq
        this.backgroundImageSeq = backgroundImageSeq
        this.modUsrSeq = modUsrSeq
    }
}

/** API 명세 19장 SHELTER_ */
enum class ShelterErrorCode(
    override val status: HttpStatus,
    override val message: String,
) : ErrorCode {
    SHELTER_NOT_FOUND(HttpStatus.NOT_FOUND, "보호소를 찾을 수 없습니다."),
}
