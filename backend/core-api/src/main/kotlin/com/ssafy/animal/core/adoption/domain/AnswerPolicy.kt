package com.ssafy.animal.core.adoption.domain

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.response.ErrorDetail
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 입양 신청 답변 검증(12.5·12.6, Q458). 답변은 문자열 questionSeq 키의 단일 객체.
 * - 형식: TEXT·DATE는 문자열(DATE는 YYYY-MM-DD), SINGLE_CHOICE는 보기 문자열, MULTI_CHOICE는 보기 문자열 배열 [초안]
 * - 조건부 문항: condition { depends_on, operator(EQ/NE/IN), value, required_when_matched }. 조건이 맞지 않으면 숨김 → 답변 제거
 */
object AnswerPolicy {

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /**
     * 키·형식을 검증하고 null 값과 숨김 문항 답변을 뺀다. DRAFT 저장(12.5)·제출(12.6) 공통.
     * 위반은 400 ADOPTION_INVALID_ANSWER(errors[].field = answers.{questionSeq})
     */
    fun clean(answers: Map<String, Any?>, questions: List<AdoptionFormQuestion>): Map<String, Any> {
        val byKey = questions.associateBy { it.key }
        val errors = mutableListOf<ErrorDetail>()
        val cleaned = LinkedHashMap<String, Any>()
        answers.forEach { (key, value) ->
            val question = byKey[key]
            when {
                question == null -> errors += ErrorDetail("answers.$key", "없는 문항입니다.")
                value == null -> Unit
                !isValid(question, value) -> errors += ErrorDetail("answers.$key", "답변 형식이 올바르지 않습니다.")
                else -> cleaned[key] = value
            }
        }
        if (errors.isNotEmpty()) throw BusinessException(AdoptionErrorCode.ADOPTION_INVALID_ANSWER, errors = errors)

        // 화면 순서대로 보며 숨김 문항을 지운다(상위 문항이 먼저 정리된다)
        questions.sortedBy { it.displayOrder }.forEach { if (!isVisible(it, cleaned)) cleaned.remove(it.key) }
        return cleaned
    }

    /** 제출(12.6): clean을 거친 답변의 필수 검증과 고정 필드 검증 */
    fun requireComplete(form: ApplicationForm, questions: List<AdoptionFormQuestion>, today: LocalDate) {
        val missing = questions.sortedBy { it.displayOrder }
            .filter { isVisible(it, form.answers) && isRequired(it) && isBlank(form.answers[it.key]) }
            .map { ErrorDetail("answers.${it.key}", "필수 문항입니다.") }
            .toMutableList()
        if (form.careAddress.isNullOrBlank()) missing += ErrorDetail("careAddress", "필수 입력값입니다.")
        if (form.visitHopeDate == null) missing += ErrorDetail("visitHopeDate", "필수 입력값입니다.")
        if (missing.isNotEmpty()) throw BusinessException(AdoptionErrorCode.ADOPTION_REQUIRED_ANSWER_MISSING, errors = missing)

        val invalid = buildList {
            if (form.visitHopeDate!!.isBefore(today)) add(ErrorDetail("visitHopeDate", "방문 희망일은 오늘 이후여야 합니다."))
            if (!form.email.isNullOrBlank() && !EMAIL.matches(form.email)) add(ErrorDetail("email", "이메일 형식이 아닙니다."))
        }
        if (invalid.isNotEmpty()) throw BusinessException(AdoptionErrorCode.ADOPTION_INVALID_ANSWER, errors = invalid)
    }

    private fun isValid(question: AdoptionFormQuestion, value: Any): Boolean {
        val options = question.optionsJsonb
        return when (question.questionType) {
            QuestionType.TEXT -> value is String
            QuestionType.DATE -> value is String && isDate(value)
            QuestionType.SINGLE_CHOICE -> value is String && (options == null || value in options)
            QuestionType.MULTI_CHOICE -> value is List<*> && value.all { it is String && (options == null || it in options) }
        }
    }

    private fun isDate(value: String) = try {
        LocalDate.parse(value)
        true
    } catch (e: DateTimeParseException) {
        false
    }

    private fun isVisible(question: AdoptionFormQuestion, answers: Map<String, Any>): Boolean {
        val condition = question.conditionJsonb ?: return true
        val dependsOn = (condition["depends_on"] as? Number)?.toInt() ?: return true
        val answer = answers[dependsOn.toString()] ?: return false
        val expected = condition["value"]
        val equals = if (answer is List<*>) expected in answer else answer == expected
        return when (condition["operator"]?.toString()?.uppercase()) {
            "EQ", null -> equals
            "NE" -> !equals
            "IN" -> expected is List<*> && (if (answer is List<*>) answer.any { it in expected } else answer in expected)
            else -> false
        }
    }

    /** 조건부 문항은 조건이 맞을 때 required_when_matched, 없으면 is_required */
    private fun isRequired(question: AdoptionFormQuestion): Boolean {
        val condition = question.conditionJsonb ?: return question.isRequired
        return condition["required_when_matched"] as? Boolean ?: question.isRequired
    }

    private fun isBlank(value: Any?) = when (value) {
        null -> true
        is String -> value.isBlank()
        is List<*> -> value.isEmpty()
        else -> false
    }
}
