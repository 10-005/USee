package com.ssafy.animal.core.adoption.domain

import com.ssafy.animal.common.persistence.BaseCreatedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

enum class QuestionType { TEXT, SINGLE_CHOICE, MULTI_CHOICE, DATE }

/** DB 명세 23. adoption_form_questions. 시드(S1)로만 넣고 읽기만 한다(Q412·Q458). questionSeq 1~20 고정 */
@Entity
@Immutable
@Table(name = "adoption_form_questions")
class AdoptionFormQuestion(
    questionSeq: Int,
    questionText: String,
    questionType: QuestionType,
    isRequired: Boolean,
    displayOrder: Int,
    optionsJsonb: List<String>? = null,
    conditionJsonb: Map<String, Any?>? = null,
) : BaseCreatedEntity() {

    @Id
    var questionSeq: Int = questionSeq
        protected set

    @Column(nullable = false)
    var questionText: String = questionText
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var questionType: QuestionType = questionType
        protected set

    @Column(nullable = false)
    var isRequired: Boolean = isRequired
        protected set

    @Column(nullable = false)
    var displayOrder: Int = displayOrder
        protected set

    /** 선택형 보기 문자열 배열 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var optionsJsonb: List<String>? = optionsJsonb
        protected set

    /** { depends_on, operator, value, required_when_matched } */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var conditionJsonb: Map<String, Any?>? = conditionJsonb
        protected set

    val key: String get() = questionSeq.toString()
}
