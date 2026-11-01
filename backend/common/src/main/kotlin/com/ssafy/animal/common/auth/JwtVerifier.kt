package com.ssafy.animal.common.auth

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.ErrorCode
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** RS256 access token 검증 전용. 발급은 core-api에서 private key로 한다. */
class JwtVerifier(publicKey: String) {

    private val parser = Jwts.parser().verifyWith(parsePublicKey(publicKey)).build()

    fun verify(token: String): AuthUser {
        val jws = try {
            parser.parseSignedClaims(token)
        } catch (e: ExpiredJwtException) {
            throw BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED)
        } catch (e: JwtException) {
            throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)
        } catch (e: IllegalArgumentException) {
            throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)
        }
        if (jws.header.algorithm != "RS256") throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)

        val claims = jws.payload
        val user = try {
            AuthUser(
                userSeq = (claims["userSeq"] as Number).toLong(),
                role = Role.valueOf(claims["role"] as String),
                shelterSeq = (claims["shelterSeq"] as Number?)?.toLong(),
            )
        } catch (e: RuntimeException) {
            throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)
        }
        if (user.role == Role.SHELTER && user.shelterSeq == null) throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)
        return user
    }

    companion object {
        /** PEM 또는 헤더 없는 Base64(X.509 DER) 공개키를 받는다. */
        fun parsePublicKey(value: String): RSAPublicKey {
            val base64 = value.replace(Regex("-----(BEGIN|END) PUBLIC KEY-----|\\s"), "")
            val spec = X509EncodedKeySpec(Base64.getDecoder().decode(base64))
            return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
        }
    }
}
