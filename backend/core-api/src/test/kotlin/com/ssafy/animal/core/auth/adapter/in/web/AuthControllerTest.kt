package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.core.auth.application.port.`in`.AuthUseCase
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.application.port.`in`.IssuedTokens
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterCommand
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterResult
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterUseCase
import com.ssafy.animal.core.auth.application.port.`in`.KakaoVerifyResult
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserErrorCode
import io.jsonwebtoken.Jwts
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.Base64
import java.util.Date

@WebMvcTest(AuthController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var authUseCase: AuthUseCase

    @MockitoBean
    lateinit var kakaoRegisterUseCase: KakaoRegisterUseCase

    private fun post(path: String, body: String, bearer: String? = null) = mockMvc.post("/api/core/v1/auth$path") {
        bearer?.let { header(HttpHeaders.AUTHORIZATION, it) }
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `2_1 미가입은 토큰 없이 200, requiresRegistration=true`() {
        given(authUseCase.verifyKakao("k")).willReturn(KakaoVerifyResult(null, null, "reg-token"))
        post("/kakao/verify", """{"kakaoAccessToken":"k"}""").andExpect {
            status { isOk() }
            jsonPath("$.data.requiresRegistration") { value(true) }
            jsonPath("$.data.registrationToken") { value("reg-token") }
            jsonPath("$.data.accessToken") { value(null) }
        }
    }

    @Test
    fun `2_1 토큰 누락은 400`() {
        post("/kakao/verify", """{"kakaoAccessToken":" "}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    @Test
    fun `2_3 refresh는 비인증으로 호출한다`() {
        given(authUseCase.refresh("r")).willReturn(IssuedTokens("a2", "r2", 3600))
        post("/refresh", """{"refreshToken":"r"}""").andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("a2") }
            jsonPath("$.data.tokenType") { value("Bearer") }
            jsonPath("$.data.expiresIn") { value(3600) }
        }
    }

    @Test
    fun `2_4 logout은 로그인이 필요하고 성공하면 204`() {
        post("/logout", """{"refreshToken":"r"}""").andExpect { status { isUnauthorized() } }

        post("/logout", """{"refreshToken":"r","fcmToken":"f"}""", BEARER).andExpect { status { isNoContent() } }
        then(authUseCase).should().logout(42, "r", "f")
    }

    @Test
    fun `2_5 가입은 비인증으로 호출하고 201, expiresIn은 없다`() {
        val command = KakaoRegisterCommand("reg", "반려가족", "김가람", LocalDate.of(1997, 5, 12), "01012345678")
        val user = User(Role.USER, "반려가족", "김가람", LocalDate.of(1997, 5, 12), "01012345678")
            .also { ReflectionTestUtils.setField(it, "userSeq", 17) }
        given(kakaoRegisterUseCase.register(command)).willReturn(KakaoRegisterResult(user, IssuedTokens("a", "r", 3600)))

        post(
            "/kakao/register",
            """{"registrationToken":"reg","nickname":"반려가족","name":"김가람","birthDate":"1997-05-12","phone":"01012345678"}""",
        ).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(201) }
            jsonPath("$.data.userSeq") { value(17) }
            jsonPath("$.data.role") { value("USER") }
            jsonPath("$.data.nickname") { value("반려가족") }
            jsonPath("$.data.accessToken") { value("a") }
            jsonPath("$.data.refreshToken") { value("r") }
            jsonPath("$.data.tokenType") { value("Bearer") }
            jsonPath("$.data.expiresIn") { doesNotExist() }
        }
    }

    @Test
    fun `2_5 서비스 오류는 도메인 코드로 응답한다`() {
        given(kakaoRegisterUseCase.register(KakaoRegisterCommand("reg", null, null, null, null))).willThrow(BusinessException(UserErrorCode.USER_DUPLICATE_NICKNAME))
        post("/kakao/register", """{"registrationToken":"reg"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USER_DUPLICATE_NICKNAME") }
        }
    }

    companion object {
        private val keyPair = Jwts.SIG.RS256.keyPair().build()
        private val BEARER = "Bearer " + Jwts.builder().claim("userSeq", 42).claim("role", "USER").claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
