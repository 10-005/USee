package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.application.port.`in`.IssuedTokens
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginResult
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginUseCase
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.Base64

@WebMvcTest(LocalAuthController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class LocalAuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var localLoginUseCase: LocalLoginUseCase

    private fun login(body: String) = mockMvc.post("/api/core/v1/auth/local/login") {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    @Test
    fun `2_2 비인증으로 호출하고 명세의 data 필드를 돌려준다`() {
        given(localLoginUseCase.login("admin1", "pw")).willReturn(LocalLoginResult(IssuedTokens("a", "r", 3600), 55, 7))
        login("""{"loginId":"admin1","password":"pw"}""").andExpect {
            status { isOk() }
            jsonPath("$.data.accessToken") { value("a") }
            jsonPath("$.data.refreshToken") { value("r") }
            jsonPath("$.data.tokenType") { value("Bearer") }
            jsonPath("$.data.expiresIn") { value(3600) }
            jsonPath("$.data.role") { value("SHELTER") }
            jsonPath("$.data.userSeq") { value(55) }
            jsonPath("$.data.shelterSeq") { value(7) }
        }
    }

    @Test
    fun `2_2 실패 코드는 401과 429로 내려간다`() {
        given(localLoginUseCase.login("admin1", "bad")).willThrow(BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS))
        login("""{"loginId":"admin1","password":"bad"}""").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_INVALID_CREDENTIALS") }
        }
        given(localLoginUseCase.login("admin1", "pw")).willThrow(BusinessException(AuthErrorCode.AUTH_LOGIN_LOCKED))
        login("""{"loginId":"admin1","password":"pw"}""").andExpect {
            status { isTooManyRequests() }
            jsonPath("$.code") { value("AUTH_LOGIN_LOCKED") }
        }
    }

    @Test
    fun `2_2 빈 값은 400`() {
        login("""{"loginId":" ","password":""}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
    }

    companion object {
        private val keyPair = Jwts.SIG.RS256.keyPair().build()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
