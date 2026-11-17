package com.ssafy.animal.core.user.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable

/** DB 명세 02. auth_providers. 값(KAKAO·LOCAL)은 Flyway 시드로만 넣고 앱은 읽기만 한다. */
@Entity
@Immutable
@Table(name = "auth_providers")
class AuthProvider(
    providerSeq: Int,
    providerCode: String,
) : BaseCreatedEntity() {

    @Id
    var providerSeq: Int = providerSeq
        protected set

    @Column(nullable = false, length = 20)
    var providerCode: String = providerCode
        protected set

    @Column(length = 50)
    var providerName: String? = null
        protected set

    companion object {
        const val KAKAO = "KAKAO"
        const val LOCAL = "LOCAL"
    }
}
