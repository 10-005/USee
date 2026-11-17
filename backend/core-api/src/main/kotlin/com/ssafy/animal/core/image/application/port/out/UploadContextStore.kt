package com.ssafy.animal.core.image.application.port.out

import java.time.Duration

/** 4.1 발급 컨텍스트 임시 저장소(Redis upload:image:{imageKey}) */
interface UploadContextStore {
    fun save(imageKey: String, context: UploadContext, ttl: Duration)
    fun find(imageKey: String): UploadContext?
    fun delete(imageKey: String)
}

data class UploadContext(
    val userSeq: Long,
    val contentType: String,
    val fileName: String?,
)
