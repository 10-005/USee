package com.ssafy.animal.core.auth.adapter.out.redis

import com.ssafy.animal.core.auth.application.port.out.RefreshSession
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.application.port.out.RegistrationContextStore
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration

/** 키 auth:refresh:{userSeq}:{jti}, 값은 토큰 해시(+shelterSeq). TTL = refresh 수명(명세 2.3) */
@Component
class RedisRefreshTokenStore(
    private val redis: StringRedisTemplate,
    private val jsonMapper: JsonMapper,
) : RefreshTokenStore {

    override fun save(userSeq: Long, jti: String, session: RefreshSession, ttl: Duration) =
        redis.opsForValue().set(key(userSeq, jti), jsonMapper.writeValueAsString(session), ttl)

    // GETDEL: 같은 refresh로 동시에 갱신해도 한 요청만 성공한다
    override fun take(userSeq: Long, jti: String): RefreshSession? =
        redis.opsForValue().getAndDelete(key(userSeq, jti))?.let { jsonMapper.readValue(it) }

    override fun delete(userSeq: Long, jti: String) {
        redis.delete(key(userSeq, jti))
    }

    override fun deleteAll(userSeq: Long) {
        val keys = redis.scan(ScanOptions.scanOptions().match(key(userSeq, "*")).count(100).build()).use { it.asSequence().toList() }
        if (keys.isNotEmpty()) redis.delete(keys)
    }

    private fun key(userSeq: Long, jti: String) = "auth:refresh:$userSeq:$jti"
}

/** 키 auth:register:{token}, 값은 카카오 회원번호. TTL 10분(Q414 A) */
@Component
class RedisRegistrationContextStore(
    private val redis: StringRedisTemplate,
) : RegistrationContextStore {

    override fun save(registrationToken: String, kakaoId: String, ttl: Duration) =
        redis.opsForValue().set(key(registrationToken), kakaoId, ttl)

    override fun find(registrationToken: String): String? = redis.opsForValue().get(key(registrationToken))

    override fun delete(registrationToken: String) {
        redis.delete(key(registrationToken))
    }

    private fun key(registrationToken: String) = "auth:register:$registrationToken"
}
