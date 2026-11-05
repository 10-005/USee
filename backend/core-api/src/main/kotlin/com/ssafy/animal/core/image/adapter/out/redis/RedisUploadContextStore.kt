package com.ssafy.animal.core.image.adapter.out.redis

import com.ssafy.animal.core.image.application.port.out.UploadContext
import com.ssafy.animal.core.image.application.port.out.UploadContextStore
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration

@Component
class RedisUploadContextStore(
    private val redis: StringRedisTemplate,
    private val jsonMapper: JsonMapper,
) : UploadContextStore {

    override fun save(imageKey: String, context: UploadContext, ttl: Duration) =
        redis.opsForValue().set(key(imageKey), jsonMapper.writeValueAsString(context), ttl)

    override fun find(imageKey: String): UploadContext? =
        redis.opsForValue().get(key(imageKey))?.let { jsonMapper.readValue(it) }

    override fun delete(imageKey: String) {
        redis.delete(key(imageKey))
    }

    private fun key(imageKey: String) = "upload:image:$imageKey"
}
