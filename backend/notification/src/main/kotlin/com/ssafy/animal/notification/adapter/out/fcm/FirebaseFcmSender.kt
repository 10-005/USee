package com.ssafy.animal.notification.adapter.out.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.ssafy.animal.notification.application.port.out.FcmMessage
import com.ssafy.animal.notification.application.port.out.FcmSender
import com.ssafy.animal.notification.application.port.out.InvalidFcmTokenException

/** Firebase Admin SDK(HTTP v1)로 한 기기에 보낸다. title·body가 없으면 data만 담은 메시지(채팅) */
class FirebaseFcmSender(private val messaging: FirebaseMessaging) : FcmSender {

    override fun send(message: FcmMessage) {
        val builder = Message.builder().setToken(message.token).putAllData(message.data)
        if (message.title != null || message.body != null) {
            builder.setNotification(Notification.builder().setTitle(message.title).setBody(message.body).build())
        }
        try {
            messaging.send(builder.build())
        } catch (e: FirebaseMessagingException) {
            // 앱 삭제·토큰 만료 등으로 더는 쓸 수 없는 토큰
            if (e.messagingErrorCode == MessagingErrorCode.UNREGISTERED) throw InvalidFcmTokenException(message.token, e)
            throw e
        }
    }
}
