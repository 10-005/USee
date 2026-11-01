package com.ssafy.animal.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime

@MappedSuperclass
abstract class BaseTimeEntity {

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var regDttm: OffsetDateTime? = null
        protected set

    @UpdateTimestamp
    @Column(nullable = false)
    var modDttm: OffsetDateTime? = null
        protected set
}
