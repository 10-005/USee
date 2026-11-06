package com.ssafy.animal.core.user.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/** DB 명세 03. user_accounts. 회원당 인증 수단 1개, KAKAO는 login_identifier에 카카오 회원번호를 둔다. */
@Entity
@Table(name = "user_accounts")
class UserAccount(
    user: User,
    provider: AuthProvider,
    loginIdentifier: String,
    passwordHash: String?,
) : BaseCreatedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var userAccountSeq: Int? = null
        protected set

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_seq", nullable = false, unique = true)
    var user: User = user
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_seq", nullable = false)
    var provider: AuthProvider = provider
        protected set

    @Column(nullable = false, length = 255)
    var loginIdentifier: String = loginIdentifier
        protected set

    @Column(length = 255)
    var passwordHash: String? = passwordHash
        protected set

    @Column(nullable = false)
    var isDeleted: Boolean = false
        protected set
}
