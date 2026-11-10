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
import com.ssafy.animal.notification.domain.NotificationSetting
import com.ssafy.animal.notification.domain.NotificationSettingChange
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    private val settings = FakeNotificationSettingRepository()

    // 서울 2026-11-10 23:30
    private val clock = Clock.fixed(Instant.parse("2026-11-10T14:30:00Z"), ZoneId.of("Asia/Seoul"))
    private val service = NotificationEventService(notifications, tokens, fcm, settings, clock)

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

    private fun setting(userSeq: Int, change: NotificationSettingChange) {
        settings.rows[userSeq] = NotificationSetting(userSeq).apply { change(change) }
    }

    @Test
    fun `댓글 알림을 끄면 알림함에는 저장하고 FCM만 보내지 않는다`() {
        setting(17, NotificationSettingChange(commentEnabled = false))

        service.onNotificationRequested(reply)

        assertEquals(1, notifications.rows.size)
        assertTrue(fcm.sent.isEmpty())
    }

    @Test
    fun `종류별 설정은 자기 종류만 막고, 설정 행이 없으면 전부 보낸다`() {
        setting(17, NotificationSettingChange(adoptionEnabled = false, aiMatchEnabled = false))

        service.onNotificationRequested(reply.copy(notificationType = NotificationType.ADOPTION_APPROVED, commentSeq = null, parentCommentSeq = null))
        service.onNotificationCreated(NotificationCreated(805, 17, NotificationType.AI_MATCH, "제보", TargetType.FOUND, 210))
        service.onNotificationRequested(reply)
        service.onNotificationRequested(reply.copy(recipientUserSeq = 18))

        assertEquals(listOf("token-17", "token-18"), fcm.sent.map { it.token })
        assertEquals(listOf("COMMENT_REPLY", "COMMENT_REPLY"), fcm.sent.map { it.data["notificationType"] })
    }

    @Test
    fun `SHELTER 알림은 설정과 상관없이 보낸다`() {
        setting(17, NotificationSettingChange(false, false, false, false, false))

        service.onNotificationRequested(reply.copy(notificationType = NotificationType.SHELTER, commentSeq = null, parentCommentSeq = null))

        assertEquals("token-17", fcm.sent.single().token)
    }

    @Test
    fun `모의 입양 알림을 끄면 버린다`() {
        setting(17, NotificationSettingChange(simulationEnabled = false))

        service.onSimulationPushRequested(SimulationPushRequested(17, SimulationPushType.EVENT, "초코가 불러요", "확인해 보세요"))

        assertTrue(fcm.sent.isEmpty())
    }

    @Test
    fun `방해금지 시간의 모의 입양 알림은 버리고, 꺼져 있거나 구간 밖이면 보낸다`() {
        val push = SimulationPushRequested(17, SimulationPushType.SESSION_END, "끝", "리포트를 확인해 보세요")

        // 23:30은 22:00~08:00 안
        setting(17, NotificationSettingChange(quietEnabled = true))
        service.onSimulationPushRequested(push)
        assertTrue(fcm.sent.isEmpty())

        // 방해금지 꺼짐
        setting(17, NotificationSettingChange(quietEnabled = false))
        service.onSimulationPushRequested(push)
        // 23:30은 13:00~18:00 밖
        setting(17, NotificationSettingChange(quietEnabled = true, quietStart = LocalTime.of(13, 0), quietEnd = LocalTime.of(18, 0)))
        service.onSimulationPushRequested(push)

        assertEquals(2, fcm.sent.size)
    }

    @Test
    fun `방해금지는 모의 입양에만 적용한다`() {
        setting(17, NotificationSettingChange(quietEnabled = true))

        service.onNotificationRequested(reply)
        service.onChatMessageSent(ChatMessageSent(3, 900, 18, "발견도우미", "안녕하세요", listOf(17, 18)))

        assertEquals(2, fcm.sent.size)
    }

    @Test
    fun `채팅 알림을 끈 수신자에게는 데이터 메시지도 보내지 않는다`() {
        tokens.rows += DeviceToken(19, "token-19")
        setting(17, NotificationSettingChange(chatEnabled = false))

        service.onChatMessageSent(ChatMessageSent(3, 900, 18, "발견도우미", "안녕하세요", listOf(17, 18, 19)))

        assertEquals(listOf("token-19"), fcm.sent.map { it.token })
    }
}
