package com.ssafy.animal.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

// 역할 검사는 메서드 단위 @PreAuthorize(명세 1.1, 17.9~17.10 USER 전용). 거부는 GlobalExceptionHandler가 403 AUTH_FORBIDDEN으로 바꾼다
@EnableMethodSecurity
@SpringBootApplication
class NotificationApplication

fun main(args: Array<String>) {
    runApplication<NotificationApplication>(*args)
}
