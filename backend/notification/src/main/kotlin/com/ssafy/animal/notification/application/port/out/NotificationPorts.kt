package com.ssafy.animal.notification.application.port.out

import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.notification.domain.DeviceToken
import com.ssafy.animal.notification.domain.Notification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

interface NotificationRepository {
    fun save(notification: Notification): Notification

    /** 수신자의 비삭제 알림 */
    fun findOwn(recipientUserSeq: Int, notificationSeq: Long): Notification?

    /** 17.1 비삭제만. 정렬 regDttm만(동률 notificationSeq desc) */
    fun findPage(recipientUserSeq: Int, isRead: Boolean?, notificationType: NotificationType?, pageable: Pageable): Page<Notification>

    fun markAllRead(recipientUserSeq: Int, now: OffsetDateTime): Int

    fun softDeleteAll(recipientUserSeq: Int): Int

    /** COMMENT·COMMENT_REPLY 중 (targetType, commentSeq) 일치 행의 message 교체 */
    fun replaceCommentMessage(targetType: TargetType, commentSeq: Long, message: String): Int
}

interface DeviceTokenRepository {
    fun findActive(userSeq: Int): DeviceToken?

    fun findActive(userSeqs: Collection<Int>): List<DeviceToken>

    fun save(token: DeviceToken): DeviceToken

    /** 이 계정의 활성 토큰 전부 비활성화 */
    fun deactivateUser(userSeq: Int, now: OffsetDateTime): Int

    /** 어느 계정이든 이 토큰이 활성이면 비활성화(기기 양도) */
    fun deactivateToken(fcmToken: String, now: OffsetDateTime): Int

    /** 이 계정의 이 토큰만 비활성화 */
    fun deactivateUserToken(userSeq: Int, fcmToken: String, now: OffsetDateTime): Int
}

/** Q433 쓰기 재확인용 users 읽기(core 스키마 읽기 전용) */
interface UserStatusReader {
    /** 없으면 null */
    fun find(userSeq: Long): UserStatus?
}

interface UserStatus {
    val deleted: Boolean
    val accountStatus: String
}

/** FCM 발송 포트. 키가 있으면 Firebase Admin SDK, 없으면 로깅 어댑터(FcmConfig) */
interface FcmSender {
    fun send(message: FcmMessage)
}

/** title·body가 null이면 데이터 메시지(채팅) */
data class FcmMessage(
    val token: String,
    val title: String?,
    val body: String?,
    val data: Map<String, String>,
)

/** 더는 쓸 수 없는 토큰(FCM UNREGISTERED). 받은 쪽이 그 토큰을 비활성화한다 */
class InvalidFcmTokenException(val token: String, cause: Throwable? = null) : RuntimeException("만료된 FCM 토큰", cause)
