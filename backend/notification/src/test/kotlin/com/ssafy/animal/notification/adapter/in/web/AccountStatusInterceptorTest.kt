package com.ssafy.animal.notification.adapter.`in`.web

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.JwtVerifier
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.notification.application.port.out.UserStatus
import com.ssafy.animal.notification.application.port.out.UserStatusReader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** 명세 1.1 Q433: notification 쓰기 요청 재확인 */
@WebMvcTest(AccountStatusInterceptorTest.ProbeController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
@Import(AccountStatusConfig::class, AccountStatusInterceptorTest.ProbeController::class)
class AccountStatusInterceptorTest {

    @RestController
    class ProbeController {
        @GetMapping("/api/notification/v1/probe")
        fun read() = "ok"

        @PostMapping("/api/notification/v1/probe")
        fun create() = "ok"

        @PatchMapping("/api/notification/v1/probe")
        fun update() = "ok"

        @DeleteMapping("/api/notification/v1/probe")
        fun remove() = "ok"
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var userStatusReader: UserStatusReader

    @MockitoBean
    lateinit var jwtVerifier: JwtVerifier

    @BeforeEach
    fun token() {
        given(jwtVerifier.verify("user")).willReturn(AuthUser(42, Role.USER, null))
    }

    private fun userStatus(deleted: Boolean, accountStatus: String) = object : UserStatus {
        override val deleted = deleted
        override val accountStatus = accountStatus
    }

    private val bearer = "Bearer user"

    @Test
    fun `활성 계정의 쓰기는 통과한다`() {
        given(userStatusReader.find(42)).willReturn(userStatus(false, "ACTIVE"))

        mockMvc.post("/api/notification/v1/probe") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect { status { isOk() } }
        mockMvc.patch("/api/notification/v1/probe") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect { status { isOk() } }
        mockMvc.delete("/api/notification/v1/probe") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect { status { isOk() } }
    }

    @Test
    fun `탈퇴 계정의 쓰기는 401 AUTH_TOKEN_INVALID`() {
        given(userStatusReader.find(42)).willReturn(userStatus(true, "ACTIVE"))

        mockMvc.post("/api/notification/v1/probe") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_TOKEN_INVALID") }
        }
    }

    @Test
    fun `정지 계정의 쓰기는 403 AUTH_ACCOUNT_UNAVAILABLE`() {
        given(userStatusReader.find(42)).willReturn(userStatus(false, "SUSPENDED"))

        mockMvc.delete("/api/notification/v1/probe") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_ACCOUNT_UNAVAILABLE") }
        }
    }

    @Test
    fun `GET은 재확인하지 않는다`() {
        mockMvc.get("/api/notification/v1/probe") { header(HttpHeaders.AUTHORIZATION, bearer) }.andExpect { status { isOk() } }
        then(userStatusReader).should(never()).find(anyLong())
    }
}
