package com.ssafy.animal.simulation.adapter.`in`.web

import io.jsonwebtoken.Jwts
import org.springframework.test.context.DynamicPropertyRegistry
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date

/**
 * 컨트롤러 테스트가 실제 JWT 필터를 거치도록 테스트용 키로 토큰을 서명한다.
 * 각 테스트 클래스의 @DynamicPropertySource에서 register()를 불러 공개키를 넣는다.
 */
object WebTestTokens {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
        .expiration(Date(System.currentTimeMillis() + 3_600_000))
        .signWith(keyPair.private, Jwts.SIG.RS256).compact()

    val USER = bearer(mapOf("userSeq" to 17, "role" to "USER"))
    val SHELTER = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
    }
}
