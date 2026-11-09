package com.ssafy.animal.notification.application.service

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.notification.application.port.out.DeviceTokenRepository
import com.ssafy.animal.notification.application.port.out.FcmMessage
import com.ssafy.animal.notification.application.port.out.FcmSender
import com.ssafy.animal.notification.application.port.out.NotificationRepository
import com.ssafy.animal.notification.domain.DeviceToken
import com.ssafy.animal.notification.domain.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime

/** 서비스 테스트용 메모리 저장소. 일괄 UPDATE는 JPQL과 같은 조건으로 흉내 낸다 */
class FakeNotificationRepository : NotificationRepository {
    val rows = mutableListOf<Notification>()

    override fun save(notification: Notification) = notification.also {
        ReflectionTestUtils.setField(it, "notificationSeq", 800L + rows.size)
        rows += it
    }

    override fun findOwn(recipientUserSeq: Int, notificationSeq: Long) =
        rows.firstOrNull { it.notificationSeq == notificationSeq && it.recipientUserSeq == recipientUserSeq && !it.isDeleted }

    override fun findPage(recipientUserSeq: Int, isRead: Boolean?, notificationType: NotificationType?, pageable: Pageable): Page<Notification> {
        val list = rows.filter {
            it.recipientUserSeq == recipientUserSeq && !it.isDeleted &&
                (isRead == null || it.isRead == isRead) && (notificationType == null || it.notificationType == notificationType)
        }.sortedByDescending { it.notificationSeq }
        return PageImpl(list, pageable, list.size.toLong())
    }

    override fun markAllRead(recipientUserSeq: Int, now: OffsetDateTime) =
        rows.filter { it.recipientUserSeq == recipientUserSeq && !it.isDeleted && !it.isRead }.onEach { it.markRead(now) }.size

    override fun softDeleteAll(recipientUserSeq: Int) =
        rows.filter { it.recipientUserSeq == recipientUserSeq && !it.isDeleted }.onEach { it.softDelete() }.size

    override fun replaceCommentMessage(targetType: TargetType, commentSeq: Long, message: String) =
        rows.filter {
            it.targetType == targetType && it.commentSeq == commentSeq &&
                it.notificationType in listOf(NotificationType.COMMENT, NotificationType.COMMENT_REPLY)
        }.onEach { ReflectionTestUtils.setField(it, "message", message) }.size
}

class FakeDeviceTokenRepository : DeviceTokenRepository {
    val rows = mutableListOf<DeviceToken>()

    override fun findActive(userSeq: Int) = rows.firstOrNull { it.userSeq == userSeq && it.isActive }

    override fun findActive(userSeqs: Collection<Int>) = rows.filter { it.userSeq in userSeqs && it.isActive }

    override fun save(token: DeviceToken): DeviceToken {
        // 부분 UNIQUE 두 개를 흉내 낸다: 위반이면 서비스가 순서를 틀린 것
        check(rows.none { it.isActive && (it.userSeq == token.userSeq || it.fcmToken == token.fcmToken) }) { "uq_device_* 위반" }
        rows += token
        return token
    }

    override fun deactivateUser(userSeq: Int, now: OffsetDateTime) = deactivate { it.userSeq == userSeq }

    override fun deactivateToken(fcmToken: String, now: OffsetDateTime) = deactivate { it.fcmToken == fcmToken }

    override fun deactivateUserToken(userSeq: Int, fcmToken: String, now: OffsetDateTime) =
        deactivate { it.userSeq == userSeq && it.fcmToken == fcmToken }

    private fun deactivate(match: (DeviceToken) -> Boolean) =
        rows.filter { it.isActive && match(it) }.onEach { ReflectionTestUtils.setField(it, "isActive", false) }.size
}

class RecordingFcmSender : FcmSender {
    val sent = mutableListOf<FcmMessage>()
    var fail = false

    override fun send(message: FcmMessage) {
        if (fail) error("FCM down")
        sent += message
    }
}
