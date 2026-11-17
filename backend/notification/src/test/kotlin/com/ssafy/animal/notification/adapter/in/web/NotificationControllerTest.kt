package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.event.NotificationType
import com.ssafy.animal.common.event.TargetType
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.notification.application.port.`in`.DeviceTokenResult
import com.ssafy.animal.notification.application.port.`in`.NotificationItem
import com.ssafy.animal.notification.application.port.`in`.NotificationUseCase
import com.ssafy.animal.notification.application.port.`in`.ReadAllResult
import com.ssafy.animal.notification.application.port.`in`.ReadResult
import com.ssafy.animal.notification.domain.NotificationErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.BDDMockito.willThrow
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.time.ZoneOffset

@WebMvcTest(NotificationController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class NotificationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var notificationUseCase: NotificationUseCase

    // notification 테스트 클래스패스에는 jjwt가 없어 검증기를 목으로 둔다(토큰 문자열 → 주체)
    @MockitoBean
    lateinit var jwtVerifier: JwtVerifier

    private val readAt = OffsetDateTime.of(2026, 11, 9, 9, 0, 0, 0, ZoneOffset.ofHours(9))

    @BeforeEach
    fun tokens() {
        given(jwtVerifier.verify("user")).willReturn(AuthUser(42, Role.USER, null))
        given(jwtVerifier.verify("shelter")).willReturn(AuthUser(77, Role.SHELTER, 7))
    }

    @Test
    fun `17_1은 필터를 넘기고 title을 포함한 Page를 돌려준다`() {
        val item = NotificationItem(
            806, NotificationType.COMMENT_REPLY, "회원님의 댓글에 답글이 달렸어요", "와 정말 다행이네요!",
            TargetType.MISSING, 101, 45, 44, null, false, null, readAt,
        )
        given(notificationUseCase.list(eq(42L), eq(false), eq(NotificationType.COMMENT_REPLY), any(Pageable::class.java) ?: Pageable.unpaged()))
            .willReturn(PageResponse(listOf(item), 0, 20, 1, 1, false))

        mockMvc.get("/api/notification/v1/notifications?isRead=false&notificationType=COMMENT_REPLY") { auth("user") }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].notificationSeq") { value(806) }
            jsonPath("$.data.content[0].title") { value("회원님의 댓글에 답글이 달렸어요") }
            jsonPath("$.data.content[0].commentSeq") { value(45) }
            jsonPath("$.data.content[0].parentCommentSeq") { value(44) }
            jsonPath("$.data.content[0].missingReportSeq") { value(null as Any?) }
            jsonPath("$.data.totalElements") { value(1) }
        }
    }

    @Test
    fun `17_1 허용 밖 notificationType은 400, 토큰 없으면 401`() {
        mockMvc.get("/api/notification/v1/notifications?notificationType=REPORT") { auth("user") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        mockMvc.get("/api/notification/v1/notifications").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
    }

    @Test
    fun `17_2 성공은 읽음 시각, 타인 알림은 404 NOTIFICATION_NOT_FOUND`() {
        given(notificationUseCase.read(42, 805)).willReturn(ReadResult(805, true, readAt))
        given(notificationUseCase.read(42, 900)).willThrow(BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND))

        mockMvc.patch("/api/notification/v1/notifications/805/read") { auth("user") }.andExpect {
            status { isOk() }
            jsonPath("$.data.notificationSeq") { value(805) }
            jsonPath("$.data.isRead") { value(true) }
            jsonPath("$.data.readDttm") { value("2026-11-09T09:00:00+09:00") }
        }
        mockMvc.patch("/api/notification/v1/notifications/900/read") { auth("user") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOTIFICATION_NOT_FOUND") }
        }
    }

    @Test
    fun `17_3은 갱신 건수를 돌려준다`() {
        given(notificationUseCase.readAll(77)).willReturn(ReadAllResult(5))

        mockMvc.patch("/api/notification/v1/notifications/read-all") { auth("shelter") }.andExpect {
            status { isOk() }
            jsonPath("$.data.updatedCount") { value(5) }
        }
    }

    @Test
    fun `17_4 성공은 204, 타인 알림은 404`() {
        willThrow(BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND)).given(notificationUseCase).delete(42, 900)

        mockMvc.delete("/api/notification/v1/notifications/805") { auth("user") }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(notificationUseCase).should().delete(42, 805)
        mockMvc.delete("/api/notification/v1/notifications/900") { auth("user") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOTIFICATION_NOT_FOUND") }
        }
    }

    @Test
    fun `17_5 전체 삭제는 204`() {
        mockMvc.delete("/api/notification/v1/notifications") { auth("user") }.andExpect {
            status { isNoContent() }
        }
        then(notificationUseCase).should().deleteAll(42)
    }

    @Test
    fun `17_6 upsert는 201이 아니라 200 active=true, 토큰 누락은 400`() {
        given(notificationUseCase.registerDeviceToken(42, "fcm-device-token")).willReturn(DeviceTokenResult(true))

        mockMvc.post("/api/notification/v1/notifications/device-tokens") {
            auth("user")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fcmToken":"fcm-device-token"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.active") { value(true) }
        }
        mockMvc.post("/api/notification/v1/notifications/device-tokens") {
            auth("user")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fcmToken":" "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        then(notificationUseCase).should(never()).registerDeviceToken(anyLong(), eq(" ") ?: " ")
    }

    @Test
    fun `17_7은 본문의 토큰을 비활성화하고 204`() {
        mockMvc.delete("/api/notification/v1/notifications/device-tokens/current") {
            auth("user")
            contentType = MediaType.APPLICATION_JSON
            content = """{"fcmToken":"fcm-device-token"}"""
        }.andExpect {
            status { isNoContent() }
        }
        then(notificationUseCase).should().deactivateDeviceToken(42, "fcm-device-token")
    }

    private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.auth(token: String) =
        header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
