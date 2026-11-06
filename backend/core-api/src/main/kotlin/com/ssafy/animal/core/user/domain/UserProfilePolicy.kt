package com.ssafy.animal.core.user.domain

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import java.time.LocalDate

/**
 * USER 필수 개인정보 4종 검증(DB CHECK, Q370 형식은 BE 검증).
 * 닉네임은 FE와 같은 규칙: 2~10자, 한글·영문·숫자와 `.` `_`만, 공백 불가. name·phone은 DB 길이까지만.
 */
object UserProfilePolicy {

    private val NICKNAME = Regex("^[가-힣a-zA-Z0-9._]{2,10}$")
    private const val NAME_MAX = 100
    private const val PHONE_MAX = 30

    /** 누락(null·공백)은 400 USER_REQUIRED_PROFILE_MISSING, 형식 오류는 400 COMMON_INVALID_INPUT */
    fun validate(nickname: String?, name: String?, birthDate: LocalDate?, phone: String?) {
        val missing = listOf("nickname" to nickname, "name" to name, "birthDate" to birthDate?.toString(), "phone" to phone)
            .filter { it.second.isNullOrBlank() }
            .map { ErrorDetail(it.first, "필수 입력값입니다.") }
        if (missing.isNotEmpty()) throw BusinessException(UserErrorCode.USER_REQUIRED_PROFILE_MISSING, errors = missing)

        val invalid = buildList {
            if (!NICKNAME.matches(nickname!!)) add(ErrorDetail("nickname", "닉네임은 2~10자의 한글·영문·숫자와 . _만 쓸 수 있습니다."))
            if (name!!.length > NAME_MAX) add(ErrorDetail("name", "이름은 ${NAME_MAX}자 이하입니다."))
            if (phone!!.length > PHONE_MAX) add(ErrorDetail("phone", "전화번호는 ${PHONE_MAX}자 이하입니다."))
        }
        if (invalid.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = invalid)
    }
}
