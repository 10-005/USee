package com.ssafy.animal.notification

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class NotificationConfig {

    /** 방해금지 판정 시각. 테스트에서 고정 시계로 바꾼다 */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
