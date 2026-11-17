package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.notification.application.port.`in`.NotificationSettingResult
import com.ssafy.animal.notification.application.port.`in`.NotificationSettingUseCase
import com.ssafy.animal.notification.domain.NotificationSettingChange
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.time.LocalTime

@WebMvcTest(NotificationSettingController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class NotificationSettingControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var settingUseCase: NotificationSettingUseCase

    @MockitoBean
    lateinit var jwtVerifier: JwtVerifier

    private val defaults = NotificationSettingResult(true, true, true, true, true, false, "22:00", "08:00")

    @BeforeEach
    fun tokens() {
        given(jwtVerifier.verify("user")).willReturn(AuthUser(42, Role.USER, null))
        given(jwtVerifier.verify("shelter")).willReturn(AuthUser(77, Role.SHELTER, 7))
    }

    @Test
    fun `17_9는 설정을 HH_mm 시각으로 돌려준다`() {
        given(settingUseCase.get(42)).willReturn(defaults)

        mockMvc.get("/api/notification/v1/notifications/settings") { auth("user") }.andExpect {
            status { isOk() }
            jsonPath("$.data.simulationEnabled") { value(true) }
            jsonPath("$.data.aiMatchEnabled") { value(true) }
            jsonPath("$.data.quietEnabled") { value(false) }
            jsonPath("$.data.quietStart") { value("22:00") }
            jsonPath("$.data.quietEnd") { value("08:00") }
        }
    }

    @Test
    fun `17_10은 보낸 항목만 넘기고 생략한 항목은 null이다`() {
        val change = NotificationSettingChange(chatEnabled = false, quietEnabled = true, quietStart = LocalTime.of(23, 0))
        given(settingUseCase.update(42, change)).willReturn(defaults.copy(chatEnabled = false, quietEnabled = true, quietStart = "23:00"))

        mockMvc.patch("/api/notification/v1/notifications/settings") {
            auth("user")
            contentType = MediaType.APPLICATION_JSON
            content = """{"chatEnabled":false,"quietEnabled":true,"quietStart":"23:00"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.chatEnabled") { value(false) }
            jsonPath("$.data.quietStart") { value("23:00") }
        }
    }

    @Test
    fun `보호소 계정은 조회·변경 모두 403 AUTH_FORBIDDEN`() {
        mockMvc.get("/api/notification/v1/notifications/settings") { auth("shelter") }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        mockMvc.patch("/api/notification/v1/notifications/settings") {
            auth("shelter")
            contentType = MediaType.APPLICATION_JSON
            content = """{"chatEnabled":false}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(settingUseCase).should(never()).update(anyLong(), any(NotificationSettingChange::class.java) ?: NotificationSettingChange())
    }

    @Test
    fun `시각 형식이 틀리면 400 COMMON_INVALID_JSON, 토큰 없으면 401`() {
        mockMvc.patch("/api/notification/v1/notifications/settings") {
            auth("user")
            contentType = MediaType.APPLICATION_JSON
            content = """{"quietStart":"25:00"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_JSON") }
        }
        mockMvc.get("/api/notification/v1/notifications/settings").andExpect {
            status { isUnauthorized() }
        }
    }

    private fun MockHttpServletRequestDsl.auth(token: String) = header(HttpHeaders.AUTHORIZATION, "Bearer $token")
}
