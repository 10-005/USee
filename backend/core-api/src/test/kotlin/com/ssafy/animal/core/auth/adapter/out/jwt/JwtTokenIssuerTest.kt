package com.ssafy.animal.core.auth.adapter.out.jwt

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.application.port.out.RefreshClaims
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JwtTokenIssuerTest {

    private val keyPair = Jwts.SIG.RS256.keyPair().build()
    private val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    private val issuer = JwtTokenIssuer(
        JwtProperties(
            privateKey = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(keyPair.private.encoded) + "\n-----END PRIVATE KEY-----",
            publicKey = publicKey,
            accessTokenTtl = Duration.ofHours(1),
            refreshTokenTtl = Duration.ofDays(14),
        ),
    )
    private val verifier = JwtVerifier(publicKey)

    @Test
    fun `access는 common JwtVerifier를 통과한다`() {
        assertEquals(AuthUser(7, Role.USER, null), verifier.verify(issuer.issueAccess(7, Role.USER, null)))
        assertEquals(AuthUser(55, Role.SHELTER, 3), verifier.verify(issuer.issueAccess(55, Role.SHELTER, 3)))
    }

    @Test
    fun `refresh는 API 인증에 쓸 수 없고 parseRefresh로만 읽힌다`() {
        val refresh = issuer.issueRefresh(7, "jti-1")
        assertFailsWith<BusinessException> { verifier.verify(refresh) }
        assertEquals(RefreshClaims(7, "jti-1"), issuer.parseRefresh(refresh))
    }

    @Test
    fun `access·위조·다른 키 토큰은 refresh로 인정하지 않는다`() {
        assertNull(issuer.parseRefresh(issuer.issueAccess(7, Role.USER, null)))
        assertNull(issuer.parseRefresh("not-a-jwt"))
        val otherKey = Jwts.SIG.RS256.keyPair().build().private
        val forged = Jwts.builder().claim("userSeq", 7).id("j").claim("typ", "refresh")
            .expiration(java.util.Date(System.currentTimeMillis() + 60_000)).signWith(otherKey, Jwts.SIG.RS256).compact()
        assertNull(issuer.parseRefresh(forged))
    }
}
