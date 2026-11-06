package com.ssafy.animal.core.auth.adapter.out.jwt

import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.core.auth.application.port.out.RefreshClaims
import com.ssafy.animal.core.auth.application.port.out.TokenIssuer
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * 개인 키는 Git에 두지 않고 환경 변수 JWT_PRIVATE_KEY(SealedSecret)로만 받는다.
 * 유효 시간은 명세 기본값(access 1시간·refresh 14일)이며 dev는 환경 변수로 덮어쓴다(예: JWT_ACCESS_TOKEN_TTL=P14D).
 */
@ConfigurationProperties("jwt")
data class JwtProperties(
    /** PKCS#8 PEM 또는 헤더 없는 Base64(DER) */
    val privateKey: String,
    /** X.509 PEM 또는 Base64(DER). common JwtVerifier와 같은 값 */
    val publicKey: String,
    val accessTokenTtl: Duration = Duration.ofHours(1),
    val refreshTokenTtl: Duration = Duration.ofDays(14),
)

@Component
@EnableConfigurationProperties(JwtProperties::class)
class JwtTokenIssuer(properties: JwtProperties) : TokenIssuer {

    private val privateKey = parsePrivateKey(properties.privateKey)
    private val parser = Jwts.parser().verifyWith(JwtVerifier.parsePublicKey(properties.publicKey)).build()

    override val accessTokenTtl: Duration = properties.accessTokenTtl
    override val refreshTokenTtl: Duration = properties.refreshTokenTtl

    override fun issueAccess(userSeq: Long, role: Role, shelterSeq: Long?): String =
        sign(accessTokenTtl) {
            claim("userSeq", userSeq).claim("role", role.name).claim("typ", "access")
            if (shelterSeq != null) claim("shelterSeq", shelterSeq)
        }

    override fun issueRefresh(userSeq: Long, jti: String): String =
        sign(refreshTokenTtl) { claim("userSeq", userSeq).id(jti).claim("typ", "refresh") }

    override fun parseRefresh(refreshToken: String): RefreshClaims? {
        val jws = try {
            parser.parseSignedClaims(refreshToken)
        } catch (e: JwtException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        val claims = jws.payload
        if (jws.header.algorithm != "RS256" || claims.expiration == null || claims["typ"] != "refresh") return null
        val userSeq = (claims["userSeq"] as? Number)?.toLong() ?: return null
        val jti = claims.id ?: return null
        return RefreshClaims(userSeq, jti)
    }

    private fun sign(ttl: Duration, body: io.jsonwebtoken.JwtBuilder.() -> Unit): String {
        val now = Instant.now()
        return Jwts.builder()
            .apply(body)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }

    companion object {
        fun parsePrivateKey(value: String): PrivateKey {
            val base64 = value.replace(Regex("-----(BEGIN|END) PRIVATE KEY-----|\\s"), "")
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)))
        }
    }
}
