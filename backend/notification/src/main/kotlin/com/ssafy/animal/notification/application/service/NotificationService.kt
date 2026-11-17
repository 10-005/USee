package com.ssafy.animal.notification.application.service

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.notification.application.port.`in`.DeviceTokenResult
import com.ssafy.animal.notification.application.port.`in`.NotificationItem
import com.ssafy.animal.notification.application.port.`in`.NotificationUseCase
import com.ssafy.animal.notification.application.port.`in`.ReadAllResult
import com.ssafy.animal.notification.application.port.`in`.ReadResult
import com.ssafy.animal.notification.application.port.out.DeviceTokenRepository
import com.ssafy.animal.notification.application.port.out.NotificationRepository
import com.ssafy.animal.notification.domain.DeviceToken
import com.ssafy.animal.notification.domain.Notification
import com.ssafy.animal.notification.domain.NotificationErrorCode
import com.ssafy.animal.notification.domain.title
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
) : NotificationUseCase {

    @Transactional(readOnly = true)
    override fun list(userSeq: Long, isRead: Boolean?, notificationType: NotificationType?, pageable: Pageable) =
        PageResponse.of(notificationRepository.findPage(Math.toIntExact(userSeq), isRead, notificationType, pageable).map(::toItem))

    @Transactional
    override fun read(userSeq: Long, notificationSeq: Long): ReadResult {
        val notification = load(userSeq, notificationSeq)
        notification.markRead(OffsetDateTime.now())
        return ReadResult(notificationSeq, notification.isRead, notification.readDttm)
    }

    @Transactional
    override fun readAll(userSeq: Long) = ReadAllResult(notificationRepository.markAllRead(Math.toIntExact(userSeq), OffsetDateTime.now()))

    @Transactional
    override fun delete(userSeq: Long, notificationSeq: Long) {
        load(userSeq, notificationSeq).softDelete()
    }

    @Transactional
    override fun deleteAll(userSeq: Long) {
        notificationRepository.softDeleteAll(Math.toIntExact(userSeq))
    }

    // ponytail: 같은 토큰을 동시에 등록하면 부분 UNIQUE에 걸려 500이 날 수 있다. 실제로 겪으면 위반 시 1회 재시도
    @Transactional
    override fun registerDeviceToken(userSeq: Long, fcmToken: String): DeviceTokenResult {
        val user = Math.toIntExact(userSeq)
        if (deviceTokenRepository.findActive(user)?.fcmToken == fcmToken) return DeviceTokenResult(true)
        val now = OffsetDateTime.now()
        // 일괄 UPDATE는 바로 실행되므로 INSERT 전에 부분 UNIQUE 두 개가 비워진다
        deviceTokenRepository.deactivateUser(user, now) // 계정당 활성 1개: 이전 토큰 비활성화
        deviceTokenRepository.deactivateToken(fcmToken, now) // 다른 계정에서 활성이면 이 계정으로 양도
        deviceTokenRepository.save(DeviceToken(user, fcmToken))
        return DeviceTokenResult(true)
    }

    @Transactional
    override fun deactivateDeviceToken(userSeq: Long, fcmToken: String) {
        deviceTokenRepository.deactivateUserToken(Math.toIntExact(userSeq), fcmToken, OffsetDateTime.now())
    }

    private fun load(userSeq: Long, notificationSeq: Long): Notification =
        notificationRepository.findOwn(Math.toIntExact(userSeq), notificationSeq)
            ?: throw BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)

    private fun toItem(n: Notification) = NotificationItem(
        notificationSeq = n.notificationSeq!!,
        notificationType = n.notificationType,
        title = n.notificationType.title,
        message = n.message,
        targetType = n.targetType,
        targetSeq = n.targetSeq,
        commentSeq = n.commentSeq,
        parentCommentSeq = n.parentCommentSeq,
        missingReportSeq = n.missingReportSeq?.toLong(),
        isRead = n.isRead,
        readDttm = n.readDttm,
        regDttm = n.regDttm,
    )
}
