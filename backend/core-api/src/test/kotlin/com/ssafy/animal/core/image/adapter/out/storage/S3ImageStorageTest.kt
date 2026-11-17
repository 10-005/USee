package com.ssafy.animal.core.image.adapter.out.storage

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import org.mockito.AdditionalAnswers
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** presigned URL 서명은 네트워크 없이 만들어지므로 실제 서명 결과를 검증한다. */
class S3ImageStorageTest {

    private val credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDTEST", "secret"))
    private val properties = StorageProperties(bucket = "test-bucket", publicBaseUrl = "https://cdn.example.com/")
    private val storage = S3ImageStorage(
        S3Client.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build(),
        S3Presigner.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build(),
        properties,
    )

    @Test
    fun `presigned PUT은 10분이고 클라이언트가 보낼 헤더 4개를 서명한다`() {
        val key = "images/2026/11/abc.jpg"
        val presigned = storage.presignPut(key, "image/jpeg", 1_234_567)
        val url = java.net.URI(presigned.url)

        assertEquals("test-bucket.s3.ap-northeast-2.amazonaws.com", url.host)
        assertEquals("/$key", url.path)
        assertEquals(600, presigned.expiresIn.seconds)
        val query = url.query.split("&").associate { it.substringBefore("=") to it.substringAfter("=") }
        assertEquals("600", query["X-Amz-Expires"])
        // 태그는 쿼리 파라미터가 아니라 서명 헤더로 들어간다 → 클라이언트가 직접 보내야 한다
        assertNull(query["x-amz-tagging"])
        assertEquals(
            setOf("content-length", "content-type", "host", "if-none-match", "x-amz-tagging"),
            query.getValue("X-Amz-SignedHeaders").split(";").toSet(),
        )
    }

    @Test
    fun `서명 헤더 값은 Content-Type, Content-Length, upload=pending 태그, If-None-Match 별표`() {
        // 어댑터가 presigner에 넘긴 요청을 붙잡아, 그 요청이 실제로 서명하는 헤더 값을 확인한다
        val realPresigner = S3Presigner.builder().region(Region.AP_NORTHEAST_2).credentialsProvider(credentials).build()
        val presigner = mock(S3Presigner::class.java, AdditionalAnswers.delegatesTo<Any>(realPresigner))
        S3ImageStorage(mock(S3Client::class.java), presigner, properties).presignPut("k.png", "image/png", 10)

        val captor = ArgumentCaptor.forClass(PutObjectPresignRequest::class.java)
        verify(presigner).presignPutObject(captor.capture())
        val signedHeaders = realPresigner.presignPutObject(captor.value).signedHeaders()
        assertEquals(listOf("image/png"), signedHeaders["content-type"])
        assertEquals(listOf("10"), signedHeaders["content-length"])
        assertEquals(listOf("upload=pending"), signedHeaders["x-amz-tagging"])
        assertEquals(listOf("*"), signedHeaders["if-none-match"])
    }

    @Test
    fun `imageUrl은 public base URL과 키를 슬래시 하나로 잇는다`() {
        assertEquals("https://cdn.example.com/images/2026/11/abc.jpg", storage.publicUrl("images/2026/11/abc.jpg"))
    }

    @Test
    fun `버킷이나 공개 주소가 비어 있으면 503, 둘 다 있으면 통과`() {
        storage.ensureConfigured()
        listOf(StorageProperties(publicBaseUrl = "https://cdn"), StorageProperties(bucket = "b")).forEach { props ->
            val unconfigured = S3ImageStorage(mock(S3Client::class.java), mock(S3Presigner::class.java), props)
            assertEquals(
                CommonErrorCode.COMMON_SERVICE_UNAVAILABLE,
                assertFailsWith<BusinessException> { unconfigured.ensureConfigured() }.errorCode,
            )
        }
    }

    @Test
    fun `버킷·주소·자격 증명이 비어 있어도 S3 빈 구성은 실패하지 않는다`() {
        // 로컬·CI는 S3 키 없이 앱을 띄운다. 자격 증명은 호출 시점에만 읽는다
        ApplicationContextRunner()
            .withUserConfiguration(S3StorageConfig::class.java, S3ImageStorage::class.java)
            .run { context ->
                assertNull(context.startupFailure)
                assertTrue(context.getBean(S3ImageStorage::class.java).publicUrl("k").endsWith("/k"))
            }
    }
}
