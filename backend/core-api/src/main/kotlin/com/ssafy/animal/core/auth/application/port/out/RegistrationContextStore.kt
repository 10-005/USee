package com.ssafy.animal.core.auth.application.port.out

import java.time.Duration

/** Redis auth:register:{token} → 카카오 회원번호. 2.5 가입(S15P31D106-65)에서 꺼내 쓴다. */
interface RegistrationContextStore {
    fun save(registrationToken: String, kakaoId: String, ttl: Duration)
}
