package com.ssafy.animal.notification.adapter.out.fcm

import com.ssafy.animal.notification.application.port.out.FcmMessage
import com.ssafy.animal.notification.application.port.out.FcmSender
import org.slf4j.LoggerFactory

/** FCM 키(fcm.credentials)가 없는 로컬·테스트용. 실제로 보내지 않고 로그만 남긴다. 토큰은 앞 8자만 찍는다 */
class LoggingFcmSender : FcmSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(message: FcmMessage) {
        log.info("[fcm] token={}… title={} body={} data={}", message.token.take(8), message.title, message.body, message.data)
    }
}
