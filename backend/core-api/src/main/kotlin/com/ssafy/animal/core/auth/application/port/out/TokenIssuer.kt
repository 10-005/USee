package com.ssafy.animal.core.auth.application.port.out

import com.ssafy.animal.common.auth.Role
import java.time.Duration

/** access·refresh RS256 JWT 발급과 refresh 서명 검증 */
interface TokenIssuer {
    val accessTokenTtl: Duration
    val refreshTokenTtl: Duration
    fun issueAccess(userSeq: Long, role: Role, shelterSeq: Long?): String
    fun issueRefresh(userSeq: Long, jti: String): String

    /** 서명·만료·typ=refresh가 맞으면 클레임, 아니면 null */
    fun parseRefresh(refreshToken: String): RefreshClaims?
}

data class RefreshClaims(val userSeq: Long, val jti: String)
