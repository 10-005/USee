package com.ssafy.animal.core.shelter.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/** DB 명세 05. shelter_members. 보호소↔SHELTER 계정 1:1, Flyway 시드로만 만든다(소속 변경 API 없음). */
@Entity
@Immutable
@Table(name = "shelter_members")
class ShelterMember(
    shelterMemberSeq: Int,
    shelterSeq: Int,
    userSeq: Int,
) : BaseCreatedEntity() {

    @Id
    var shelterMemberSeq: Int = shelterMemberSeq
        protected set

    @Column(nullable = false, unique = true)
    var shelterSeq: Int = shelterSeq
        protected set

    @Column(nullable = false, unique = true)
    var userSeq: Int = userSeq
        protected set
}
