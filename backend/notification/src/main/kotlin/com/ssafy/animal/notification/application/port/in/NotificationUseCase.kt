package com.ssafy.animal.notification.application.port.`in`

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.response.PageResponse
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

/** 17.1~17.7 REST */
interface NotificationUseCase {
    fun list(userSeq: Long, isRead: Boolean?, notificationType: NotificationType?, pageable: Pageable): PageResponse<NotificationItem>

    /** 17.2 타인·삭제된 알림은 404 NOTIFICATION_NOT_FOUND */
    fun read(userSeq: Long, notificationSeq: Long): ReadResult

    /** 17.3 읽지 않은 비삭제 알림 전부 */
    fun readAll(userSeq: Long): ReadAllResult

    /** 17.4 소프트 삭제. 타인·삭제된 알림은 404 */
    fun delete(userSeq: Long, notificationSeq: Long)

    /** 17.5 전체 소프트 삭제 */
    fun deleteAll(userSeq: Long)

    /** 17.6 upsert(Q415 예외·Q439 양도). 409 없음 */
    fun registerDeviceToken(userSeq: Long, fcmToken: String): DeviceTokenResult

    /** 17.7 이 계정의 그 토큰만 비활성화. 없어도 성공 */
    fun deactivateDeviceToken(userSeq: Long, fcmToken: String)
}

data class NotificationItem(
    val notificationSeq: Long,
    val notificationType: NotificationType,
    val title: String,
    val message: String,
    val targetType: TargetType?,
    val targetSeq: Long?,
    val commentSeq: Long?,
    val parentCommentSeq: Long?,
    val missingReportSeq: Long?,
    val isRead: Boolean,
    val readDttm: OffsetDateTime?,
    val regDttm: OffsetDateTime?,
)

data class ReadResult(
    val notificationSeq: Long,
    val isRead: Boolean,
    val readDttm: OffsetDateTime?,
)

data class ReadAllResult(
    val updatedCount: Int,
)

data class DeviceTokenResult(
    val active: Boolean,
)
