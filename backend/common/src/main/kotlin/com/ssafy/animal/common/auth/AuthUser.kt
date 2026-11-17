package com.ssafy.animal.common.auth

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority

enum class Role { USER, SHELTER }

/** JWT에서 꺼낸 인증 주체. 컨트롤러에서 @AuthenticationPrincipal AuthUser로 받는다. */
data class AuthUser(
    val userSeq: Long,
    val role: Role,
    val shelterSeq: Long?,
) {
    fun toAuthentication(): Authentication =
        UsernamePasswordAuthenticationToken.authenticated(this, null, listOf(SimpleGrantedAuthority("ROLE_${role.name}")))
}
