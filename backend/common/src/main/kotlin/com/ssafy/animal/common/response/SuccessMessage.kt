package com.ssafy.animal.common.response

/** 성공 응답의 기본 메시지("요청이 성공했습니다.")를 바꿀 API에만 붙인다. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuccessMessage(val value: String)
