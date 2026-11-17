package com.ssafy.animal.common.auth

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JwtVerifierTest {

    private val keyPair = Jwts.SIG.RS256.keyPair().build()
    private val verifier = JwtVerifier(Base64.getEncoder().encodeToString(keyPair.public.encoded))

    private fun token(claims: Map<String, Any>, expiresInMillis: Long? = 60_000): String =
        Jwts.builder().claims(claims)
            .apply { expiresInMillis?.let { expiration(Date(System.currentTimeMillis() + it)) } }
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

    private val access = mapOf("userSeq" to 1, "role" to "USER", "typ" to "access")

    private fun assertRejected(code: CommonErrorCode, token: String) =
        assertEquals(code, assertFailsWith<BusinessException> { verifier.verify(token) }.errorCode)

    @Test
    fun `exp와 typ=access가 있는 정상 토큰은 통과한다`() {
        assertEquals(AuthUser(1, Role.USER, null), verifier.verify(token(access)))
    }

    @Test
    fun `exp가 없으면 AUTH_TOKEN_INVALID`() {
        assertRejected(CommonErrorCode.AUTH_TOKEN_INVALID, token(access, expiresInMillis = null))
    }

    @Test
    fun `만료되면 AUTH_TOKEN_EXPIRED`() {
        assertRejected(CommonErrorCode.AUTH_TOKEN_EXPIRED, token(access, expiresInMillis = -60_000))
    }

    @Test
    fun `typ이 없으면 AUTH_TOKEN_INVALID`() {
        assertRejected(CommonErrorCode.AUTH_TOKEN_INVALID, token(access - "typ"))
    }

    @Test
    fun `refresh 토큰은 AUTH_TOKEN_INVALID`() {
        assertRejected(CommonErrorCode.AUTH_TOKEN_INVALID, token(mapOf("userSeq" to 1, "jti" to "j-1", "typ" to "refresh")))
    }

    @Test
    fun `role이 들어 있어도 typ=refresh면 AUTH_TOKEN_INVALID`() {
        assertRejected(CommonErrorCode.AUTH_TOKEN_INVALID, token(access + ("typ" to "refresh")))
    }
}
