package com.ssafy.animal.notification.adapter.out.fcm

import com.ssafy.animal.notification.application.port.out.FcmMessage
import com.ssafy.animal.notification.application.port.out.FcmSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * ponytail: Firebase Admin SDK 의존성이 아직 없어 로그만 남긴다(코디네이터 요청). SDK가 들어오면
 * FirebaseMessaging으로 보내는 어댑터로 이 클래스를 바꾼다. 토큰은 앞 8자만 찍는다
 */
@Component
class LoggingFcmSender : FcmSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: FcmMessage) {
        log.info("[fcm] token={}… title={} body={} data={}", message.token.take(8), message.title, message.body, message.data)
    }
}
