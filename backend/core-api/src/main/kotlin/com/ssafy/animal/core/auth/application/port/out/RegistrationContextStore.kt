package com.ssafy.animal.core.auth.application.port.out

import java.time.Duration

/** Redis auth:register:{token} → 카카오 회원번호. 2.1에서 저장하고 2.5에서 조회, 가입 성공 시 삭제한다. */
interface RegistrationContextStore {
    fun save(registrationToken: String, kakaoId: String, ttl: Duration)

    /** 없거나 만료면 null */
    fun find(registrationToken: String): String?

    fun delete(registrationToken: String)
}
