package com.ssafy.animal.core.image.application.port.out

import java.time.Duration

/** 이미지 객체 저장소(S3) 포트 */
interface ImageStorage {
    /** 버킷·공개 주소가 비어 있으면 503 COMMON_SERVICE_UNAVAILABLE. 앱 기동은 막지 않고 이미지 API만 막는다 */
    fun ensureConfigured()

    /** 서명 헤더: Content-Type, Content-Length, x-amz-tagging(upload=pending), If-None-Match(*) */
    fun presignPut(key: String, contentType: String, byteSize: Long): PresignedPut

    /** 객체가 없으면 null */
    fun head(key: String): StoredObject?

    /** 태그를 upload=confirmed로 교체해 수명주기 삭제 대상(upload=pending)에서 뺀다 */
    fun markConfirmed(key: String)

    fun delete(key: String)

    /** {public-base-url}/{key} (Q443) */
    fun publicUrl(key: String): String
}

data class PresignedPut(
    val url: String,
    val expiresIn: Duration,
)

data class StoredObject(
    val contentType: String?,
    val byteSize: Long,
)
