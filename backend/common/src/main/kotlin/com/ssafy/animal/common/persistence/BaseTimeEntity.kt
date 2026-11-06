package com.ssafy.animal.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PreUpdate
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime

/** reg_dttm만 있는 테이블(images·auth_providers·user_accounts 등)용. DB 명세 Q430 */
@MappedSuperclass
abstract class BaseCreatedEntity {

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var regDttm: OffsetDateTime? = null
        protected set
}

/** mod_dttm은 INSERT 때 NULL, UPDATE 때만 채운다(@UpdateTimestamp는 INSERT에도 값을 넣어 쓰지 않는다). DB 명세 Q430 */
@MappedSuperclass
abstract class BaseTimeEntity : BaseCreatedEntity() {

    var modDttm: OffsetDateTime? = null
        protected set

    @PreUpdate
    protected fun touchModDttm() {
        modDttm = OffsetDateTime.now()
    }
}
