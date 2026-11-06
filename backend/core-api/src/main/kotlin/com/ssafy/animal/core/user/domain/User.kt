package com.ssafy.animal.core.user.domain

import com.ssafy.animal.common.auth.Role
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

enum class AccountStatus { ACTIVE, SUSPENDED }

/** DB 명세 01. users. ADMIN은 CHECK 예약값일 뿐 행을 만들지 않으므로(Q411) common Role(USER/SHELTER)로 매핑한다. */
@Entity
@Table(name = "users")
class User(
    role: Role,
    nickname: String?,
    name: String?,
    birthDate: LocalDate?,
    phone: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var userSeq: Int? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var role: Role = role
        protected set

    @Column(length = 50)
    var nickname: String? = nickname
        protected set

    @Column(length = 100)
    var name: String? = name
        protected set

    var birthDate: LocalDate? = birthDate
        protected set

    @Column(length = 30)
    var phone: String? = phone
        protected set

    var profileImageSeq: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var accountStatus: AccountStatus = AccountStatus.ACTIVE
        protected set

    var deletedDttm: OffsetDateTime? = null
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set

    /** 로그인·갱신 가능 여부. 탈퇴는 인증 실패, 정지는 403으로 따로 처리한다. */
    val isActive: Boolean get() = !isDeleted && accountStatus == AccountStatus.ACTIVE

    /** 3.2 프로필 수정. 검증(형식·중복·이미지 소유)은 서비스가 끝낸 값만 넘긴다 */
    fun updateProfile(nickname: String?, name: String?, birthDate: LocalDate?, phone: String?, profileImageSeq: Long?) {
        this.nickname = nickname
        this.name = name
        this.birthDate = birthDate
        this.phone = phone
        this.profileImageSeq = profileImageSeq
    }
}
