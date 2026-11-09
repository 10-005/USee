package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.notification.application.port.`in`.NotificationUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** 17장 알림·FCM. 모두 로그인(USER·SHELTER), 수신자 본인 것만 */
@RestController
@RequestMapping("/api/notification/v1/notifications")
class NotificationController(
    private val notificationUseCase: NotificationUseCase,
) {

    /** 17.1 필터 isRead·notificationType, 정렬 regDttm만 */
    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthUser,
        @RequestParam(required = false) isRead: Boolean?,
        @RequestParam(required = false) notificationType: NotificationType?,
        pageable: Pageable,
    ) = notificationUseCase.list(user.userSeq, isRead, notificationType, pageable)

    /** 17.2 */
    @PatchMapping("/{notificationSeq}/read")
    fun read(@AuthenticationPrincipal user: AuthUser, @PathVariable notificationSeq: Long) =
        notificationUseCase.read(user.userSeq, notificationSeq)

    /** 17.3 */
    @PatchMapping("/read-all")
    fun readAll(@AuthenticationPrincipal user: AuthUser) = notificationUseCase.readAll(user.userSeq)

    /** 17.4 */
    @DeleteMapping("/{notificationSeq}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: AuthUser, @PathVariable notificationSeq: Long) {
        notificationUseCase.delete(user.userSeq, notificationSeq)
    }

    /** 17.5 */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteAll(@AuthenticationPrincipal user: AuthUser) {
        notificationUseCase.deleteAll(user.userSeq)
    }

    /** 17.6 upsert라 201이 아니라 200(Q415 예외) */
    @PostMapping("/device-tokens")
    fun registerDeviceToken(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: DeviceTokenRequest) =
        notificationUseCase.registerDeviceToken(user.userSeq, request.fcmToken!!)

    /** 17.7 */
    @DeleteMapping("/device-tokens/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deactivateDeviceToken(@AuthenticationPrincipal user: AuthUser, @Valid @RequestBody request: DeviceTokenRequest) {
        notificationUseCase.deactivateDeviceToken(user.userSeq, request.fcmToken!!)
    }
}

data class DeviceTokenRequest(
    @field:NotBlank
    val fcmToken: String?,
)
