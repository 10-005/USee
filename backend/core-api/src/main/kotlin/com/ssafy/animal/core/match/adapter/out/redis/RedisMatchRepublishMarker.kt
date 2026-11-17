package com.ssafy.animal.core.match.adapter.out.redis

import com.ssafy.animal.core.match.application.port.out.MatchRepublishMarker
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime

@Component
class RedisMatchRepublishMarker(
    private val redis: StringRedisTemplate,
) : MatchRepublishMarker {

    // SET NX EX: 먼저 넣은 인스턴스만 true. 90일 [가안] 뒤 사라지므로 그 뒤 같은 이름이면 다시 실행된다
    override fun tryMark(runName: String): Boolean =
        redis.opsForValue().setIfAbsent("match:republish:$runName", OffsetDateTime.now().toString(), TTL) == true

    companion object {
        private val TTL = Duration.ofDays(90)
    }
}
