package com.ssafy.animal.core.sample.domain

import com.ssafy.animal.common.persistence.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** JPA 엔티티를 도메인 모델로 그대로 쓴다(별도 도메인 클래스·매퍼 없음). */
@Entity
@Table(name = "sample")
class Sample(
    name: String,
    description: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var sampleSeq: Long? = null
        protected set

    @Column(nullable = false, length = 50)
    var name: String = name
        protected set

    @Column(length = 500)
    var description: String? = description
        protected set
}
