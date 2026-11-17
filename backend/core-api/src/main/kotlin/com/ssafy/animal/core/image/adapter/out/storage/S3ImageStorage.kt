package com.ssafy.animal.core.image.adapter.out.storage

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.image.application.port.out.ImageStorage
import com.ssafy.animal.core.image.application.port.out.PresignedPut
import com.ssafy.animal.core.image.application.port.out.StoredObject
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.Tag
import software.amazon.awssdk.services.s3.model.Tagging
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

@Component
class S3ImageStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: StorageProperties,
) : ImageStorage {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun ensureConfigured() {
        if (properties.bucket.isBlank() || properties.publicBaseUrl.isBlank()) {
            log.warn("[image] storage not configured: bucket='{}', publicBaseUrl='{}'", properties.bucket, properties.publicBaseUrl)
            throw BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)
        }
    }

    override fun presignPut(key: String, contentType: String, byteSize: Long): PresignedPut {
        val putRequest = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(byteSize)
            .tagging("$TAG_KEY=$TAG_PENDING") // 수명주기가 미확정 객체만 지우도록
            .ifNoneMatch("*") // 확정 뒤 같은 URL로 다시 PUT해 태그가 pending으로 돌아가는 것을 막는다
            .build()
        val presigned = s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder().signatureDuration(properties.putExpiry).putObjectRequest(putRequest).build(),
        )
        return PresignedPut(presigned.url().toString(), properties.putExpiry)
    }

    override fun head(key: String): StoredObject? = try {
        val head = s3Client.headObject { it.bucket(properties.bucket).key(key) }
        StoredObject(head.contentType(), head.contentLength())
    } catch (e: S3Exception) {
        // 없는 키는 404. s3:ListBucket 권한이 없으면 403이 오므로 그 경우는 그대로 실패시킨다
        if (e.statusCode() == 404) null else throw e
    }

    override fun markConfirmed(key: String) {
        s3Client.putObjectTagging {
            it.bucket(properties.bucket).key(key)
                .tagging(Tagging.builder().tagSet(Tag.builder().key(TAG_KEY).value(TAG_CONFIRMED).build()).build())
        }
    }

    override fun delete(key: String) {
        s3Client.deleteObject { it.bucket(properties.bucket).key(key) }
    }

    override fun publicUrl(key: String) = "${properties.publicBaseUrl.trimEnd('/')}/$key"

    companion object {
        const val TAG_KEY = "upload"
        const val TAG_PENDING = "pending"
        const val TAG_CONFIRMED = "confirmed"
    }
}
