package com.ssafy.animal.notification.application.service

import com.ssafy.animal.common.event.ChatMessageSent
import com.ssafy.animal.common.event.CommentDeleted
import com.ssafy.animal.common.event.DeviceTokenDeactivateRequested
import com.ssafy.animal.common.event.NotificationCreated
import com.ssafy.animal.common.event.NotificationRequested
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.SimulationPushRequested
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.event.UserDeleted
import com.ssafy.animal.notification.application.port.`in`.NotificationEventUseCase
import com.ssafy.animal.notification.application.port.out.DeviceTokenRepository
import com.ssafy.animal.notification.application.port.out.FcmMessage
import com.ssafy.animal.notification.application.port.out.FcmSender
import com.ssafy.animal.notification.application.port.out.InvalidFcmTokenException
import com.ssafy.animal.notification.application.port.out.NotificationRepository
import com.ssafy.animal.notification.application.port.out.NotificationSettingRepository
import com.ssafy.animal.notification.domain.Notification
import com.ssafy.animal.notification.domain.NotificationSetting
import com.ssafy.animal.notification.domain.title
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime

@Service
class NotificationEventService(
    private val notificationRepository: NotificationRepository,
    private val deviceTokenRepository: DeviceTokenRepository,
    private val fcmSender: FcmSender,
    private val settingRepository: NotificationSettingRepository,
    private val clock: Clock,
) : NotificationEventUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    // @Transactional을 붙이지 않는다: save가 자체 트랜잭션으로 커밋된 뒤에 FCM을 보내야 한다(발송 실패와 무관하게 DB 보존)
    override fun onNotificationRequested(event: NotificationRequested) {
        val saved = notificationRepository.save(
            Notification(
                recipientUserSeq = Math.toIntExact(event.recipientUserSeq),
                notificationType = event.notificationType,
                message = event.message,
                targetType = event.targetType,
                targetSeq = event.targetSeq,
                commentSeq = event.commentSeq,
                parentCommentSeq = event.parentCommentSeq,
            ),
        )
        // 설정을 꺼도 알림함 저장은 그대로 하고 FCM만 생략한다(Q510)
        if (!settingOf(event.recipientUserSeq).allows(event.notificationType)) return
        push(
            event.recipientUserSeq, event.notificationType.title, event.message,
            storedData(saved.notificationSeq!!, event.notificationType, event.targetType, event.targetSeq, event.commentSeq, event.parentCommentSeq),
        )
    }

    override fun onNotificationCreated(event: NotificationCreated) {
        if (!settingOf(event.recipientUserSeq).allows(event.notificationType)) return
        push(
            event.recipientUserSeq, event.notificationType.title, event.message,
            storedData(event.notificationSeq, event.notificationType, event.targetType, event.targetSeq, null, null),
        )
    }

    @Transactional
    override fun onCommentDeleted(event: CommentDeleted) {
        notificationRepository.replaceCommentMessage(event.targetType, event.commentSeq, DELETED_COMMENT_MESSAGE)
    }

    /** 모의 입양을 껐거나 방해금지 시간이면 버린다(나중에 보내지 않음, Q514) */
    override fun onSimulationPushRequested(event: SimulationPushRequested) {
        if (!settingOf(event.userSeq).allowsSimulation(OffsetDateTime.now(clock))) return
        push(event.userSeq, event.title, event.body, mapOf("target" to "SIMULATION"))
    }

    @Transactional
    override fun onUserDeleted(event: UserDeleted) {
        deviceTokenRepository.deactivateUser(Math.toIntExact(event.userSeq), OffsetDateTime.now())
    }

    @Transactional
    override fun onDeviceTokenDeactivateRequested(event: DeviceTokenDeactivateRequested) {
        deviceTokenRepository.deactivateUserToken(Math.toIntExact(event.userSeq), event.fcmToken, OffsetDateTime.now())
    }

    override fun onChatMessageSent(event: ChatMessageSent) {
        // 저장형 알림 유형(NotificationType)이 아니라 FCM data에만 쓰는 구분값이다. notificationSeq 없음(Q516)
        val data = mapOf(
            "notificationType" to CHAT_PUSH_TYPE,
            "roomSeq" to event.chatRoomSeq.toString(),
            "messageSeq" to event.messageSeq.toString(),
            "senderDisplayName" to event.senderDisplayName,
            "preview" to event.preview,
        )
        // 보낸 사람은 chat이 이미 뺐지만 한 번 더 거른다
        val candidates = event.recipientUserSeqs.filter { it != event.senderUserSeq }.map(Math::toIntExact)
        // 채팅을 끈 수신자에게는 보내지 않는다(Q510). 행이 없으면 켜짐
        val chatOff = settingRepository.findAll(candidates).filterNot { it.chatEnabled }.map { it.userSeq }.toSet()
        val recipients = candidates.filterNot { it in chatOff }
        deviceTokenRepository.findActive(recipients)
            .forEach { send(FcmMessage(it.fcmToken, event.senderDisplayName, event.preview, data)) }
    }

    /** 행이 없으면 기본값(전부 켜짐, Q511). 보호소 계정은 행을 만들 수 없어 항상 기본값이다 */
    private fun settingOf(userSeq: Long): NotificationSetting {
        val user = Math.toIntExact(userSeq)
        return settingRepository.find(user) ?: NotificationSetting(user)
    }

    private fun push(userSeq: Long, title: String, body: String, data: Map<String, String>) {
        val token = deviceTokenRepository.findActive(Math.toIntExact(userSeq)) ?: return
        send(FcmMessage(token.fcmToken, title, body, data))
    }

    /** 실패해도 DB 알림은 보존하고 재시도하지 않는다(17.8). 만료된 토큰은 비활성화해 다음부터 보내지 않는다 */
    private fun send(message: FcmMessage) {
        try {
            fcmSender.send(message)
        } catch (e: InvalidFcmTokenException) {
            deviceTokenRepository.deactivateToken(e.token, OffsetDateTime.now())
            log.info("[fcm] 만료된 토큰 비활성화 token={}…", e.token.take(8))
        } catch (e: Exception) {
            log.warn("[fcm] 발송 실패 data={}", message.data, e)
        }
    }

    companion object {
        const val DELETED_COMMENT_MESSAGE = "삭제된 댓글입니다"
        const val CHAT_PUSH_TYPE = "CHAT"

        /** 17.8 저장형 알림 FCM data. 값이 없는 필드는 뺀다 */
        fun storedData(
            notificationSeq: Long,
            type: NotificationType,
            targetType: TargetType?,
            targetSeq: Long?,
            commentSeq: Long?,
            parentCommentSeq: Long?,
        ): Map<String, String> = listOfNotNull(
            "notificationSeq" to notificationSeq.toString(),
            "notificationType" to type.name,
            targetType?.let { "targetType" to it.name },
            targetSeq?.let { "targetSeq" to it.toString() },
            commentSeq?.let { "commentSeq" to it.toString() },
            parentCommentSeq?.let { "parentCommentSeq" to it.toString() },
        ).toMap()
    }
}
