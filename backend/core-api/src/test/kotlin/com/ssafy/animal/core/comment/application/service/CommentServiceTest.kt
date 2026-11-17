package com.ssafy.animal.core.comment.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.CommentDeleted
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.comment.application.port.`in`.CreateCommentCommand
import com.ssafy.animal.core.comment.application.port.`in`.UpdateCommentCommand
import com.ssafy.animal.core.comment.application.port.out.AuthorRow
import com.ssafy.animal.core.comment.application.port.out.CommentRepository
import com.ssafy.animal.core.comment.domain.Comment
import com.ssafy.animal.core.comment.domain.CommentErrorCode
import com.ssafy.animal.core.comment.domain.CommentType
import com.ssafy.animal.core.comment.domain.FoundReportComment
import com.ssafy.animal.core.comment.domain.MissingReportComment
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.domain.ImageErrorCode
import org.springframework.test.util.ReflectionTestUtils
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentServiceTest {

    private val comments = mutableListOf<Comment>()
    private var nextSeq = 100L

    /** (type, postSeq) → 작성자. 없으면 게시물 없음·비노출 */
    private val posts = mutableMapOf(CommentType.MISSING_COMMENT to 101L to POST_AUTHOR, CommentType.FOUND_COMMENT to 201L to POST_AUTHOR)
    private val authors = mutableMapOf<Int, AuthorRow>()
    private val imageOwners = mutableMapOf<Long, Long>(1701L to ME.toLong())
    private val released = mutableListOf<Long>()
    private val events = mutableListOf<Any>()

    private val repository = object : CommentRepository {
        override fun saveAndFlush(comment: Comment): Comment {
            if (comment.commentSeq == null) {
                ReflectionTestUtils.setField(comment, "commentSeq", nextSeq++)
                comments += comment
            }
            return comment
        }
        override fun findById(type: CommentType, commentSeq: Long) = comments.firstOrNull { typeOf(it) == type && it.commentSeq == commentSeq }
        override fun findRoots(type: CommentType, postSeq: Long) =
            comments.filter { typeOf(it) == type && it.postSeq.toLong() == postSeq && it.isRoot }.sortedByDescending { it.commentSeq }
        override fun findReplies(type: CommentType, rootSeq: Long) =
            comments.filter { typeOf(it) == type && it.parentCommentSeq == rootSeq }.sortedBy { it.commentSeq }
        override fun countReplies(type: CommentType, rootSeqs: Collection<Long>) =
            comments.filter { typeOf(it) == type && it.parentCommentSeq in rootSeqs && !it.isDeleted }
                .groupingBy { it.parentCommentSeq!! }.eachCount().mapValues { it.value.toLong() }
        override fun findVisiblePostAuthor(type: CommentType, postSeq: Long) = posts[type to postSeq]
        override fun findAuthors(userSeqs: Collection<Int>) = authors.filterKeys { it in userSeqs }
    }

    private val imageUseCase = object : ImageUseCase {
        override fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary {
            val owner = imageOwners[imageSeq] ?: throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
            if (owner != userSeq) throw BusinessException(ImageErrorCode.IMAGE_NOT_OWNED)
            return ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        }
        override fun findSummary(imageSeq: Long) = ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
        override fun deleteIfUnreferenced(imageSeq: Long) {
            released += imageSeq
        }
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
    }

    private val service = CommentService(repository, imageUseCase, object : EventPublisher {
        override fun publish(event: Any) {
            events += event
        }
    })

    init {
        author(ME, "반려가족")
        author(POST_AUTHOR, "실종보호자")
        author(OTHER, "발견도우미")
        author(SHELTER_USER, null, role = "SHELTER", shelterName = "김천시 동물보호센터", shelterImage = 9001)
    }

    private fun author(seq: Int, nickname: String?, role: String = "USER", deleted: Boolean = false, shelterName: String? = null, shelterImage: Long? = null) {
        authors[seq] = object : AuthorRow {
            override val userSeq = seq
            override val role = role
            override val deleted = deleted
            override val nickname = nickname
            override val profileImageSeq: Long? = null
            override val shelterName = shelterName
            override val shelterImageSeq = shelterImage
        }
    }

    private fun typeOf(c: Comment) = if (c is MissingReportComment) CommentType.MISSING_COMMENT else CommentType.FOUND_COMMENT

    private fun seed(writer: Int, parent: Long? = null, body: String? = "본문", type: CommentType = CommentType.MISSING_COMMENT): Comment {
        val post = if (type == CommentType.MISSING_COMMENT) 101 else 201
        val comment = if (type == CommentType.MISSING_COMMENT) {
            MissingReportComment(post, writer, parent, body, null)
        } else {
            FoundReportComment(post, writer, parent, body, null)
        }
        return repository.saveAndFlush(comment)
    }

    private fun create(writer: Int = ME, target: TargetType = TargetType.MISSING, targetSeq: Long = 101, parent: Long? = null, body: String? = "근처에서 봤어요", image: Long? = null) =
        service.create(writer.toLong(), CreateCommentCommand(target, targetSeq, parent, body, image))

    private fun assertCode(code: ErrorCode, block: () -> Unit) = assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `루트 댓글은 게시물 작성자에게 COMMENT 알림을 요청한다`() {
        val created = create(body = "a".repeat(120))

        assertEquals(CommentType.MISSING_COMMENT, created.commentType)
        assertNull(created.parentCommentSeq)
        val event = events.single() as NotificationRequested
        assertEquals(POST_AUTHOR.toLong(), event.recipientUserSeq)
        assertEquals(NotificationType.COMMENT, event.notificationType)
        assertEquals(TargetType.MISSING, event.targetType)
        assertEquals(101L, event.targetSeq)
        assertEquals(created.commentSeq, event.commentSeq)
        assertEquals("a".repeat(100) + "…", event.message)
    }

    @Test
    fun `답글은 루트 작성자에게만 COMMENT_REPLY, 본인 글에는 알림이 없다`() {
        val root = seed(OTHER)
        val reply = create(parent = root.commentSeq, body = null, image = 1701)

        val event = events.single() as NotificationRequested
        assertEquals(OTHER.toLong(), event.recipientUserSeq)
        assertEquals(NotificationType.COMMENT_REPLY, event.notificationType)
        assertEquals(root.commentSeq, event.parentCommentSeq)
        assertEquals(reply.commentSeq, event.commentSeq)
        assertEquals("사진을 남겼어요", event.message)

        events.clear()
        create(writer = POST_AUTHOR)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `작성 검증 - 빈 댓글·게시물 없음·답글의 답글·삭제 루트·탈퇴 루트·남의 이미지·보호동물 대상`() {
        assertCode(CommentErrorCode.COMMENT_EMPTY) { create(body = "  ") }
        assertCode(CommentErrorCode.COMMENT_TARGET_NOT_FOUND) { create(targetSeq = 999) }

        val root = seed(OTHER)
        val reply = seed(POST_AUTHOR, parent = root.commentSeq)
        assertCode(CommentErrorCode.COMMENT_DEPTH_EXCEEDED) { create(parent = reply.commentSeq) }
        assertCode(CommentErrorCode.COMMENT_NOT_FOUND) { create(target = TargetType.FOUND, targetSeq = 201, parent = root.commentSeq) }

        val deletedRoot = seed(OTHER).also { it.softDelete(OTHER) }
        assertCode(CommentErrorCode.COMMENT_PARENT_UNAVAILABLE) { create(parent = deletedRoot.commentSeq) }

        author(55, "탈퇴자", deleted = true)
        val withdrawnRoot = seed(55)
        assertCode(CommentErrorCode.COMMENT_PARENT_UNAVAILABLE) { create(parent = withdrawnRoot.commentSeq) }

        imageOwners[1800] = OTHER.toLong()
        assertCode(ImageErrorCode.IMAGE_NOT_OWNED) { create(image = 1800) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { create(target = TargetType.SHELTER_ANIMAL) }
    }

    @Test
    fun `13_1은 삭제된 루트도 body null로 포함하고 비삭제 답글 수를 센다`() {
        val oldRoot = seed(OTHER)
        seed(ME, parent = oldRoot.commentSeq)
        seed(ME, parent = oldRoot.commentSeq).softDelete(ME)
        val deletedRoot = seed(ME).also { it.softDelete(ME) }

        val roots = service.listRoots(TargetType.MISSING, 101)

        assertEquals(listOf(deletedRoot.commentSeq, oldRoot.commentSeq), roots.map { it.commentSeq })
        assertTrue(roots[0].isDeleted)
        assertNull(roots[0].body)
        assertEquals(1L, roots[1].replyCount)
        assertEquals("발견도우미", roots[1].author.displayName)
        assertCode(CommentErrorCode.COMMENT_TARGET_NOT_FOUND) { service.listRoots(TargetType.FOUND, 999) }
    }

    @Test
    fun `작성자 표시 - SHELTER는 보호소명과 보호소 사진, 탈퇴자는 (탈퇴한 사용자)`() {
        seed(SHELTER_USER)
        author(56, "탈퇴자", deleted = true)
        seed(56)

        val roots = service.listRoots(TargetType.MISSING, 101)

        assertEquals("(탈퇴한 사용자)", roots[0].author.displayName)
        assertNull(roots[0].author.profileImage)
        assertEquals("김천시 동물보호센터", roots[1].author.displayName)
        assertEquals(Role.SHELTER, roots[1].author.role)
        assertEquals(9001L, roots[1].author.profileImage?.imageSeq)
    }

    @Test
    fun `13_2는 오래된 순, 답글을 루트로 지정하면 404`() {
        val root = seed(OTHER)
        val first = seed(ME, parent = root.commentSeq)
        val second = seed(POST_AUTHOR, parent = root.commentSeq)

        assertEquals(listOf(first.commentSeq, second.commentSeq), service.listReplies(TargetType.MISSING_COMMENT, root.commentSeq!!).map { it.commentSeq })
        assertCode(CommentErrorCode.COMMENT_NOT_FOUND) { service.listReplies(TargetType.MISSING_COMMENT, first.commentSeq!!) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.listReplies(TargetType.MISSING, root.commentSeq!!) }
    }

    @Test
    fun `13_6은 삭제된 댓글도 돌려주고 게시물이 비노출이면 404`() {
        val comment = seed(ME, type = CommentType.FOUND_COMMENT).also { it.softDelete(ME) }

        val detail = service.get(TargetType.FOUND_COMMENT, comment.commentSeq!!)
        assertTrue(detail.isDeleted)
        assertEquals(TargetType.FOUND, detail.targetType)

        posts.remove(CommentType.FOUND_COMMENT to 201L)
        assertCode(CommentErrorCode.COMMENT_NOT_FOUND) { service.get(TargetType.FOUND_COMMENT, comment.commentSeq!!) }
    }

    @Test
    fun `13_4는 작성자만, 이미지를 바꾸면 이전 이미지 정리를 요청한다`() {
        val comment = seed(ME)
        ReflectionTestUtils.setField(comment, "imageSeq", 1600L)

        val updated = service.update(ME.toLong(), TargetType.MISSING_COMMENT, comment.commentSeq!!, UpdateCommentCommand(imageSeq = Optional.of(1701)))

        assertEquals("본문", updated.body)
        assertEquals(1701L, updated.image?.imageSeq)
        assertEquals(listOf(1600L), released)
        assertCode(CommentErrorCode.COMMENT_NOT_FOUND) {
            service.update(OTHER.toLong(), TargetType.MISSING_COMMENT, comment.commentSeq!!, UpdateCommentCommand(body = Optional.of("x")))
        }
        assertCode(CommentErrorCode.COMMENT_EMPTY) {
            service.update(ME.toLong(), TargetType.MISSING_COMMENT, comment.commentSeq!!, UpdateCommentCommand(Optional.empty(), Optional.empty()))
        }
    }

    @Test
    fun `13_5는 소프트 삭제·이미지 해제 후 CommentDeleted를 발행하고 다시 지우면 404`() {
        val comment = seed(ME, type = CommentType.FOUND_COMMENT)
        ReflectionTestUtils.setField(comment, "imageSeq", 1600L)

        service.delete(ME.toLong(), TargetType.FOUND_COMMENT, comment.commentSeq!!)

        assertTrue(comment.isDeleted)
        assertNull(comment.imageSeq)
        assertEquals(listOf(1600L), released)
        assertEquals(CommentDeleted(TargetType.FOUND, comment.commentSeq!!), events.single())
        assertCode(CommentErrorCode.COMMENT_NOT_FOUND) { service.delete(ME.toLong(), TargetType.FOUND_COMMENT, comment.commentSeq!!) }
    }

    companion object {
        private const val ME = 42
        private const val POST_AUTHOR = 10
        private const val OTHER = 11
        private const val SHELTER_USER = 77
    }
}
