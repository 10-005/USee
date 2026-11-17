package com.ssafy.animal.notification.application.service

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.notification.domain.DeviceToken
import com.ssafy.animal.notification.domain.Notification
import com.ssafy.animal.notification.domain.NotificationErrorCode
import org.springframework.data.domain.PageRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NotificationServiceTest {

    private val notifications = FakeNotificationRepository()
    private val tokens = FakeDeviceTokenRepository()
    private val service = NotificationService(notifications, tokens)

    private fun seed(recipient: Int, type: NotificationType = NotificationType.SHELTER) =
        notifications.save(Notification(recipient, type, "메시지", TargetType.FOUND, 210))

    private fun assertNotFound(block: () -> Unit) =
        assertEquals(NotificationErrorCode.NOTIFICATION_NOT_FOUND, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `17_1은 수신자의 비삭제 알림만, 유형별 title을 붙인다`() {
        seed(42, NotificationType.AI_MATCH)
        seed(42).softDelete()
        seed(7)

        val page = service.list(42, null, null, PageRequest.of(0, 20))

        assertEquals(1, page.totalElements)
        assertEquals("내 아이 관련 제보가 올라왔어요!", page.content.single().title)
    }

    @Test
    fun `17_2는 처음 읽은 시각을 유지하고 타인·삭제 알림은 404`() {
        val mine = seed(42)
        val first = service.read(42, mine.notificationSeq!!)
        val second = service.read(42, mine.notificationSeq!!)

        assertTrue(first.isRead)
        assertEquals(first.readDttm, second.readDttm)
        assertNotFound { service.read(7, mine.notificationSeq!!) }
        val deleted = seed(42).also { it.softDelete() }
        assertNotFound { service.read(42, deleted.notificationSeq!!) }
    }

    @Test
    fun `17_3·17_4·17_5`() {
        seed(42)
        seed(42)
        val other = seed(7)

        assertEquals(2, service.readAll(42).updatedCount)
        service.delete(42, notifications.rows[0].notificationSeq!!)
        assertTrue(notifications.rows[0].isDeleted)
        assertNotFound { service.delete(42, other.notificationSeq!!) }
        service.deleteAll(42)
        assertTrue(notifications.rows.filter { it.recipientUserSeq == 42 }.all { it.isDeleted })
        assertTrue(!other.isDeleted)
    }

    @Test
    fun `17_6 같은 토큰 재등록은 그대로, 새 토큰은 이전 것을 끄고, 남의 활성 토큰은 양도한다`() {
        service.registerDeviceToken(42, "a")
        service.registerDeviceToken(42, "a")
        assertEquals(1, tokens.rows.size)

        service.registerDeviceToken(42, "b")
        assertEquals(listOf("b"), tokens.rows.filter { it.isActive }.map { it.fcmToken })

        tokens.rows += DeviceToken(7, "c")
        val result = service.registerDeviceToken(42, "c")

        assertTrue(result.active)
        assertEquals(null, tokens.findActive(7))
        assertEquals("c", tokens.findActive(42)?.fcmToken)
    }

    @Test
    fun `17_7은 이 계정의 그 토큰만 끈다`() {
        tokens.rows += DeviceToken(42, "a")
        tokens.rows += DeviceToken(7, "b")

        service.deactivateDeviceToken(42, "b")
        assertEquals("a", tokens.findActive(42)?.fcmToken)
        service.deactivateDeviceToken(42, "a")
        assertEquals(null, tokens.findActive(42))
        assertEquals("b", tokens.findActive(7)?.fcmToken)
    }
}
