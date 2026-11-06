package com.ssafy.animal.core.image.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.out.ImageRepository
import com.ssafy.animal.core.image.application.port.out.ImageStorage
import com.ssafy.animal.core.image.application.port.out.PresignedPut
import com.ssafy.animal.core.image.application.port.out.StoredObject
import com.ssafy.animal.core.image.application.port.out.UploadContext
import com.ssafy.animal.core.image.application.port.out.UploadContextStore
import com.ssafy.animal.core.image.domain.Image
import com.ssafy.animal.core.image.domain.ImageErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S3·Redis·DB를 가짜로 바꿔 4.1·4.2 흐름과 순서를 검증한다. */
class ImageServiceTest {

    private val calls = mutableListOf<String>()
    private val storage = FakeStorage(calls)
    private val repository = FakeRepository(calls)
    private val contexts = FakeContextStore()
    private val service = ImageService(storage, repository, contexts)

    private val key = "images/2026/11/abc.jpg"
    private val mb = 1024L * 1024

    @AfterTest
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    // 4.1

    @Test
    fun `발급하면 서버 키와 10분 URL을 주고 발급 컨텍스트를 같은 TTL로 저장한다`() {
        val result = service.issueUploadUrl(IssueUploadUrlCommand(7, "dog.jpg", "image/jpeg", 5 * mb))

        assertTrue(Regex("""images/\d{4}/\d{2}/[0-9a-f-]{36}\.jpg""").matches(result.imageKey), result.imageKey)
        assertEquals(600, result.expiresInSeconds)
        assertEquals("https://upload/${result.imageKey}", result.uploadUrl)
        assertEquals(UploadContext(7, "image/jpeg", "dog.jpg") to Duration.ofMinutes(10), contexts.saved[result.imageKey])
    }

    @Test
    fun `PNG는 png 확장자, 정확히 10MB는 허용`() {
        assertTrue(service.issueUploadUrl(IssueUploadUrlCommand(7, null, "image/png", 10 * mb)).imageKey.endsWith(".png"))
    }

    @Test
    fun `발급 시 10MB 초과는 413, 허용하지 않는 MIME은 415`() {
        assertCode(ImageErrorCode.IMAGE_TOO_LARGE) { service.issueUploadUrl(IssueUploadUrlCommand(7, null, "image/jpeg", 10 * mb + 1)) }
        assertCode(ImageErrorCode.IMAGE_UNSUPPORTED_TYPE) { service.issueUploadUrl(IssueUploadUrlCommand(7, null, "image/gif", 1)) }
        assertTrue(contexts.saved.isEmpty())
    }

    // 4.2

    @Test
    fun `확정은 HEAD 재검증 → 태그 교체 → INSERT → 컨텍스트 삭제 순서`() {
        contexts.saved[key] = UploadContext(7, "image/jpeg", "dog.jpg") to Duration.ofMinutes(10)
        storage.objects[key] = StoredObject("image/jpeg", 3 * mb)

        val confirmed = service.confirm(7, key)

        assertEquals(listOf("head", "markConfirmed", "saveAndFlush"), calls)
        assertEquals("https://cdn/$key", confirmed.imageUrl)
        with(confirmed.image) {
            assertNotNull(imageSeq)
            assertEquals("image/jpeg", mimeType)
            assertEquals(3 * mb, byteSize) // 클라이언트 값이 아니라 실제 객체 크기
            assertEquals("dog.jpg", originalFileName)
            assertEquals(7, regUsrSeq)
        }
        assertNull(contexts.saved[key])
    }

    @Test
    fun `컨텍스트가 없거나 발급자가 다르면 404`() {
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.confirm(7, key) }
        contexts.saved[key] = UploadContext(8, "image/jpeg", null) to Duration.ofMinutes(10)
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.confirm(7, key) }
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `객체가 없으면 404, 실제 크기 초과는 413, 실제 MIME이 다르면 415이고 태그·INSERT는 하지 않는다`() {
        contexts.saved[key] = UploadContext(7, "image/jpeg", null) to Duration.ofMinutes(10)
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.confirm(7, key) }

        storage.objects[key] = StoredObject("image/jpeg", 10 * mb + 1)
        assertCode(ImageErrorCode.IMAGE_TOO_LARGE) { service.confirm(7, key) }

        storage.objects[key] = StoredObject("image/png", mb) // 허용 MIME이지만 발급 때와 다름
        assertCode(ImageErrorCode.IMAGE_UNSUPPORTED_TYPE) { service.confirm(7, key) }

        storage.objects[key] = StoredObject(null, mb)
        assertCode(ImageErrorCode.IMAGE_UNSUPPORTED_TYPE) { service.confirm(7, key) }

        assertEquals(listOf("head", "head", "head", "head"), calls)
        assertNotNull(contexts.saved[key]) // 실패 시 컨텍스트는 남겨 재시도 가능
    }

    @Test
    fun `태그 교체가 실패하면 INSERT하지 않는다`() {
        contexts.saved[key] = UploadContext(7, "image/jpeg", null) to Duration.ofMinutes(10)
        storage.objects[key] = StoredObject("image/jpeg", mb)
        storage.failTagging = true

        assertFailsWith<IllegalStateException> { service.confirm(7, key) }
        assertEquals(listOf("head", "markConfirmed"), calls)
    }

    @Test
    fun `같은 키 동시 확정에서 늦은 INSERT의 UNIQUE 위반은 404이고 컨텍스트는 먼저 끝난 요청이 지운다`() {
        // 두 요청이 모두 컨텍스트·HEAD 검증을 통과했고, 먼저 끝난 요청이 이미 행을 넣은 상황을 재현
        contexts.saved[key] = UploadContext(7, "image/jpeg", null) to Duration.ofMinutes(10)
        storage.objects[key] = StoredObject("image/jpeg", mb)
        repository.saveAndFlush(Image(key, "image/jpeg", null, mb, 7))
        calls.clear()

        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.confirm(7, key) }
        assertEquals(listOf("head", "markConfirmed", "saveAndFlush"), calls)
    }

    @Test
    fun `userSeq가 Int 범위를 넘으면 INSERT 전에 실패한다`() {
        val big = Int.MAX_VALUE + 1L
        contexts.saved[key] = UploadContext(big, "image/jpeg", null) to Duration.ofMinutes(10)
        storage.objects[key] = StoredObject("image/jpeg", mb)

        assertFailsWith<ArithmeticException> { service.confirm(big, key) }
        assertEquals(0, repository.rows.size)
    }

    @Test
    fun `저장소 설정이 없으면 4_1과 4_2 모두 Redis·S3를 건드리기 전에 503`() {
        storage.configured = false
        contexts.saved[key] = UploadContext(7, "image/jpeg", null) to Duration.ofMinutes(10)

        val unavailable = CommonErrorCode.COMMON_SERVICE_UNAVAILABLE
        assertEquals(unavailable, assertFailsWith<BusinessException> { service.issueUploadUrl(IssueUploadUrlCommand(7, null, "image/jpeg", mb)) }.errorCode)
        assertEquals(unavailable, assertFailsWith<BusinessException> { service.confirm(7, key) }.errorCode)
        assertEquals(1, contexts.saved.size)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `INSERT 후 컨텍스트 삭제가 실패해도 확정은 성공한다`() {
        contexts.saved[key] = UploadContext(7, "image/jpeg", null) to Duration.ofMinutes(10)
        storage.objects[key] = StoredObject("image/jpeg", mb)
        contexts.failDelete = true

        assertNotNull(service.confirm(7, key).image.imageSeq)
        assertEquals(1, repository.rows.size)
    }

    // 삭제

    @Test
    fun `참조가 없으면 행을 지우고 커밋 후에만 S3 객체를 지우며 S3 실패는 삼킨다`() {
        val image = repository.saveAndFlush(Image(key, "image/jpeg", null, mb, 7))
        storage.failDelete = true
        TransactionSynchronizationManager.initSynchronization()

        service.deleteIfUnreferenced(image.imageSeq!!)
        assertEquals(0, repository.rows.size)
        assertTrue("delete" !in calls) // 커밋 전에는 S3를 건드리지 않는다

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        assertEquals("delete", calls.last())
    }

    @Test
    fun `없는 이미지 삭제는 아무것도 하지 않는다`() {
        TransactionSynchronizationManager.initSynchronization()
        service.deleteIfUnreferenced(999)
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty())
    }

    @Test
    fun `다른 참조가 남아 있으면 행도 S3 객체도 지우지 않는다`() {
        val image = repository.saveAndFlush(Image(key, "image/jpeg", null, mb, 7))
        repository.referenced += image.imageSeq!!
        TransactionSynchronizationManager.initSynchronization()

        service.deleteIfUnreferenced(image.imageSeq!!)
        assertEquals(1, repository.rows.size)
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty())
    }

    // 연결 검증(Q407)

    @Test
    fun `본인이 올린 이미지는 URL과 함께 돌려주고, 남의 것·시드(NULL)는 403, 없으면 404`() {
        val mine = repository.saveAndFlush(Image(key, "image/jpeg", null, mb, 7))
        val others = repository.saveAndFlush(Image("images/2026/11/other.jpg", "image/jpeg", null, mb, 8))
        val seed = repository.saveAndFlush(Image("images/2026/11/seed.jpg", "image/jpeg", null, mb, null))

        assertEquals("https://cdn/$key", service.requireOwned(7, mine.imageSeq!!).imageUrl)
        assertCode(ImageErrorCode.IMAGE_NOT_OWNED) { service.requireOwned(7, others.imageSeq!!) }
        assertCode(ImageErrorCode.IMAGE_NOT_OWNED) { service.requireOwned(7, seed.imageSeq!!) }
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.requireOwned(7, 999) }
    }

    private fun assertCode(code: ImageErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    private class FakeStorage(private val calls: MutableList<String>) : ImageStorage {
        val objects = mutableMapOf<String, StoredObject>()
        var configured = true
        var failTagging = false
        var failDelete = false

        override fun ensureConfigured() {
            if (!configured) throw BusinessException(CommonErrorCode.COMMON_SERVICE_UNAVAILABLE)
        }

        override fun presignPut(key: String, contentType: String, byteSize: Long) =
            PresignedPut("https://upload/$key", Duration.ofMinutes(10))

        override fun head(key: String): StoredObject? = objects[key].also { calls += "head" }

        override fun markConfirmed(key: String) {
            calls += "markConfirmed"
            check(!failTagging) { "tagging failed" }
        }

        override fun delete(key: String) {
            calls += "delete"
            check(!failDelete) { "delete failed" }
        }

        override fun publicUrl(key: String) = "https://cdn/$key"
    }

    private class FakeRepository(private val calls: MutableList<String>) : ImageRepository {
        val rows = mutableMapOf<Long, Image>()
        private var seq = 0L

        override fun saveAndFlush(image: Image): Image {
            calls += "saveAndFlush"
            // image_key UNIQUE 제약 흉내
            if (rows.values.any { it.imageKey == image.imageKey }) throw DataIntegrityViolationException("duplicate image_key")
            ReflectionTestUtils.setField(image, "imageSeq", ++seq)
            rows[seq] = image
            return image
        }

        override fun findById(imageSeq: Long) = rows[imageSeq]

        override fun delete(image: Image) {
            rows.remove(image.imageSeq)
        }

        val referenced = mutableSetOf<Long>()

        override fun isReferenced(imageSeq: Long) = imageSeq in referenced
    }

    private class FakeContextStore : UploadContextStore {
        val saved = mutableMapOf<String, Pair<UploadContext, Duration>>()
        var failDelete = false
        override fun save(imageKey: String, context: UploadContext, ttl: Duration) {
            saved[imageKey] = context to ttl
        }
        override fun find(imageKey: String) = saved[imageKey]?.first
        override fun delete(imageKey: String) {
            check(!failDelete) { "redis down" }
            saved.remove(imageKey)
        }
    }
}
