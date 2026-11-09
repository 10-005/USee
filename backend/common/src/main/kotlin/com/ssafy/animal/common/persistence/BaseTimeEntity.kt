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

    /** 연결 테이블(사진 등)만 바뀌어 이 엔티티의 컬럼은 그대로일 때 호출한다. 엔티티를 변경 상태로 만들어 UPDATE와 mod_dttm 갱신이 일어나게 한다 */
    fun markModified() {
        modDttm = OffsetDateTime.now()
    }
}
