package com.ssafy.animal.notification.application.service

import com.ssafy.animal.common.event.ChatMessageSent
import com.ssafy.animal.common.event.CommentDeleted
import com.ssafy.animal.common.event.DeviceTokenDeactivateRequested
import com.ssafy.animal.common.event.NotificationCreated
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.SimulationPushRequested
import com.ssafy.animal.common.event.SimulationPushType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.event.UserDeleted
import com.ssafy.animal.notification.domain.DeviceToken
import com.ssafy.animal.notification.domain.Notification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationEventServiceTest {

    private val notifications = FakeNotificationRepository()
    private val tokens = FakeDeviceTokenRepository().apply {
        rows += DeviceToken(17, "token-17")
        rows += DeviceToken(18, "token-18")
    }
    private val fcm = RecordingFcmSender()
    private val service = NotificationEventService(notifications, tokens, fcm)

    private val reply = NotificationRequested(17, NotificationType.COMMENT_REPLY, "와 정말 다행이네요!", TargetType.MISSING, 101, 45, 44)

    @Test
    fun `NotificationRequested는 저장 후 title·body·이동 data로 푸시한다`() {
        service.onNotificationRequested(reply)

        val row = notifications.rows.single()
        assertEquals(17, row.recipientUserSeq)
        assertEquals(45L, row.commentSeq)
        assertEquals(44L, row.parentCommentSeq)
        val push = fcm.sent.single()
        assertEquals("token-17", push.token)
        assertEquals("회원님의 댓글에 답글이 달렸어요", push.title)
        assertEquals("와 정말 다행이네요!", push.body)
        assertEquals(
            mapOf(
                "notificationSeq" to "800", "notificationType" to "COMMENT_REPLY", "targetType" to "MISSING",
                "targetSeq" to "101", "commentSeq" to "45", "parentCommentSeq" to "44",
            ),
            push.data,
        )
    }

    @Test
    fun `FCM이 실패해도 저장은 남고, 토큰이 없으면 저장만 한다`() {
        fcm.fail = true
        service.onNotificationRequested(reply)
        fcm.fail = false
        service.onNotificationRequested(reply.copy(recipientUserSeq = 99, notificationType = NotificationType.SHELTER, commentSeq = null, parentCommentSeq = null))

        assertEquals(2, notifications.rows.size)
        assertTrue(fcm.sent.isEmpty())
    }

    @Test
    fun `만료된 토큰이면 저장은 남기고 그 토큰을 비활성화한다`() {
        fcm.expiredToken = "token-17"
        service.onNotificationRequested(reply)

        assertEquals(1, notifications.rows.size)
        assertNull(tokens.findActive(17))
        assertEquals("token-18", tokens.findActive(18)?.fcmToken)
    }

    @Test
    fun `NotificationCreated는 저장 없이 그 message로 푸시하고 빈 필드는 data에서 뺀다`() {
        service.onNotificationCreated(NotificationCreated(805, 17, NotificationType.AI_MATCH, "근처에서 목격제보가 올라왔어요. 확인해보세요.", TargetType.FOUND, 210))

        assertTrue(notifications.rows.isEmpty())
        val push = fcm.sent.single()
        assertEquals("내 아이 관련 제보가 올라왔어요!", push.title)
        assertEquals("근처에서 목격제보가 올라왔어요. 확인해보세요.", push.body)
        assertEquals(mapOf("notificationSeq" to "805", "notificationType" to "AI_MATCH", "targetType" to "FOUND", "targetSeq" to "210"), push.data)
    }

    @Test
    fun `CommentDeleted는 같은 게시물 코드·commentSeq의 COMMENT 계열 message만 바꾼다`() {
        notifications.save(Notification(17, NotificationType.COMMENT_REPLY, "원문", TargetType.MISSING, 101, 45, 44))
        notifications.save(Notification(18, NotificationType.COMMENT, "다른 게시판", TargetType.FOUND, 101, 45))
        notifications.save(Notification(18, NotificationType.COMMENT, "다른 댓글", TargetType.MISSING, 101, 46))

        service.onCommentDeleted(CommentDeleted(TargetType.MISSING, 45))

        assertEquals(listOf("삭제된 댓글입니다", "다른 게시판", "다른 댓글"), notifications.rows.map { it.message })
    }

    @Test
    fun `SimulationPushRequested는 저장 없이 data target=SIMULATION으로 보낸다`() {
        service.onSimulationPushRequested(SimulationPushRequested(17, SimulationPushType.EVENT, "초코가 불러요", "확인해 보세요"))

        assertTrue(notifications.rows.isEmpty())
        assertEquals(mapOf("target" to "SIMULATION"), fcm.sent.single().data)
        assertEquals("초코가 불러요", fcm.sent.single().title)
    }

    @Test
    fun `UserDeleted는 활성 토큰을 끄고 DeviceTokenDeactivateRequested는 그 토큰만 끈다`() {
        service.onDeviceTokenDeactivateRequested(DeviceTokenDeactivateRequested(17, "token-18"))
        assertEquals("token-18", tokens.findActive(18)?.fcmToken)

        service.onDeviceTokenDeactivateRequested(DeviceTokenDeactivateRequested(18, "token-18"))
        service.onUserDeleted(UserDeleted(17))

        assertNull(tokens.findActive(17))
        assertNull(tokens.findActive(18))
    }

    @Test
    fun `ChatMessageSent는 보낸 사람을 뺀 수신자에게 데이터 메시지만 보낸다`() {
        service.onChatMessageSent(ChatMessageSent(3, 900, 18, "발견도우미", "안녕하세요", listOf(17, 18, 99)))

        val push = fcm.sent.single()
        assertEquals("token-17", push.token)
        assertNull(push.title)
        assertNull(push.body)
        assertEquals(mapOf("roomSeq" to "3", "messageSeq" to "900", "senderDisplayName" to "발견도우미", "preview" to "안녕하세요"), push.data)
        assertTrue(notifications.rows.isEmpty())
    }
}
