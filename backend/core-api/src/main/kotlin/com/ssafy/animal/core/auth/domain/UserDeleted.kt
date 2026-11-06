package com.ssafy.animal.core.auth.domain

/**
 * 2.6 탈퇴 커밋 후 발행(API 명세 21.6). notification은 활성 device_tokens 비활성화, simulation은 진행 중 세션 ABANDONED.
 * 페이로드는 명세에 정해지지 않아 userSeq만 둔다.
 */
data class UserDeleted(
    val userSeq: Long,
)
