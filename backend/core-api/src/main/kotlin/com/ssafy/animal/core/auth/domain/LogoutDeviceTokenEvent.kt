package com.ssafy.animal.core.auth.domain

/** 2.4 로그아웃 때 fcmToken이 오면 발행. device_tokens는 notification 소유라 그쪽이 이 기기 토큰만 비활성화한다(Q419 A). */
data class LogoutDeviceTokenEvent(
    val userSeq: Long,
    val fcmToken: String,
)
