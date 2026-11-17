package com.ssafy.animal.core.catalog.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/** DB 명세 07. districts. 법정동 코드 앞 5자리(시·군·구) 마스터, Flyway 시드로만 넣는다(Q406-1) */
@Entity
@Immutable
@Table(name = "districts")
class District(
    districtCode: String,
    sidoName: String,
    sigunguName: String,
) : BaseCreatedEntity() {

    @Id
    @Column(length = 5)
    var districtCode: String = districtCode
        protected set

    @Column(nullable = false, length = 50)
    var sidoName: String = sidoName
        protected set

    @Column(nullable = false, length = 50)
    var sigunguName: String = sigunguName
        protected set
}
