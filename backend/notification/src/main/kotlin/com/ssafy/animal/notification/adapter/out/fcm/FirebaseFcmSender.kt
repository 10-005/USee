package com.ssafy.animal.notification.adapter.out.fcm

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.ssafy.animal.notification.application.port.out.FcmMessage
import com.ssafy.animal.notification.application.port.out.FcmSender
import com.ssafy.animal.notification.application.port.out.InvalidFcmTokenException

/** Firebase Admin SDK(HTTP v1)로 한 기기에 보낸다. 사용자에게 보이는 알림이라 Android는 high priority(절전 중 지연 방지) */
class FirebaseFcmSender(private val messaging: FirebaseMessaging) : FcmSender {

    override fun send(message: FcmMessage) {
        val built = Message.builder().setToken(message.token).putAllData(message.data)
            .setNotification(Notification.builder().setTitle(message.title).setBody(message.body).build())
            .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
            .build()
        try {
            messaging.send(built)
        } catch (e: FirebaseMessagingException) {
            // 앱 삭제·토큰 만료 등으로 더는 쓸 수 없는 토큰
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) throw InvalidFcmTokenException(message.token, e)
            throw e
        }
    }
}
