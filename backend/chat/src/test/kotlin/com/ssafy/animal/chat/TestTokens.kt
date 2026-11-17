package com.ssafy.animal.chat

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** chat 테스트 클래스패스에는 jjwt API가 없어(common의 implementation) RS256 access 토큰을 직접 만든다 */
object TestTokens {
    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    fun access(userSeq: Long, role: String = "USER", shelterSeq: Long? = null, expEpochSecond: Long = System.currentTimeMillis() / 1000 + 3600): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256"}""".toByteArray())
        val shelter = shelterSeq?.let { ""","shelterSeq":$it""" }.orEmpty()
        val payload = encoder.encodeToString(
            """{"userSeq":$userSeq,"role":"$role"$shelter,"typ":"access","exp":$expEpochSecond}""".toByteArray(),
        )
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update("$header.$payload".toByteArray())
        }.sign()
        return "$header.$payload.${encoder.encodeToString(signature)}"
    }
}
