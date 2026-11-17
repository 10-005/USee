package com.ssafy.animal.core.auth.adapter.out.redis

import com.ssafy.animal.core.auth.application.port.out.LoginAttemptStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration

/** 키 auth:login-fail:{loginId}. INCR과 EXPIRE를 한 스크립트로 묶어 TTL 없는 키(영구 잠금)가 남지 않게 한다. */
@Component
class RedisLoginAttemptStore(
    private val redis: StringRedisTemplate,
) : LoginAttemptStore {

    override fun record(loginId: String, maxAttempts: Int, window: Duration): Long =
        redis.execute(SCRIPT, listOf(key(loginId)), maxAttempts.toString(), window.seconds.toString())

    override fun clear(loginId: String) {
        redis.delete(key(loginId))
    }

    private fun key(loginId: String) = "auth:login-fail:$loginId"

    companion object {
        private val SCRIPT = RedisScript.of(
            """
            local n = redis.call('INCR', KEYS[1])
            if n <= tonumber(ARGV[1]) then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            return n
            """.trimIndent(),
            Long::class.java,
        )
    }
}
