package com.ssafy.animal.notification.adapter.out.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.ssafy.animal.notification.application.port.out.FcmSender
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FcmConfig {

    /** fcm.credentials(서비스 계정 키 JSON 원문)가 있으면 실제 발송, 없으면 로그만 */
    @Bean
    fun fcmSender(@Value("\${fcm.credentials:}") credentials: String): FcmSender {
        if (credentials.isBlank()) return LoggingFcmSender()
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(credentials.byteInputStream()))
            .build()
        return FirebaseFcmSender(FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options)))
    }
}
