package com.ssafy.animal.core.image.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime

/** 공통 이미지 메타데이터(DB 명세 14. images). 수정 시각 컬럼이 없어 BaseTimeEntity를 쓰지 않는다. */
@Entity
@Table(name = "images")
class Image(
    imageKey: String,
    mimeType: String,
    originalFileName: String?,
    byteSize: Long?,
    regUsrSeq: Int?,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var imageSeq: Long? = null
        protected set

    @Column(nullable = false, unique = true, length = 512)
    var imageKey: String = imageKey
        protected set

    @Column(nullable = false, length = 100)
    var mimeType: String = mimeType
        protected set

    @Column(length = 255)
    var originalFileName: String? = originalFileName
        protected set

    var byteSize: Long? = byteSize
        protected set

    // users.user_seq가 INTEGER라 Int로 둔다(Flyway 전환 후 validate 대비)
    var regUsrSeq: Int? = regUsrSeq
        protected set

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var regDttm: OffsetDateTime? = null
        protected set
}
