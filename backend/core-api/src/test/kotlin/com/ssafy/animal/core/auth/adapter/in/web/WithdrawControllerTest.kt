package com.ssafy.animal.core.auth.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.core.auth.application.port.`in`.WithdrawUseCase
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.mockito.ArgumentMatchers.anyLong
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import java.util.Base64
import java.util.Date

@WebMvcTest(WithdrawController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class WithdrawControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var withdrawUseCase: WithdrawUseCase

    private fun withdraw(bearer: String?) = mockMvc.delete("/api/core/v1/users/me") {
        bearer?.let { header(HttpHeaders.AUTHORIZATION, it) }
    }

    @Test
    fun `USER는 204이고 본문이 없다`() {
        withdraw(bearer("USER")).andExpect {
            status { isNoContent() }
            content { string("") }
        }
        then(withdrawUseCase).should().withdraw(42)
    }

    @Test
    fun `SHELTER는 403 AUTH_FORBIDDEN 봉투로 거절하고 탈퇴하지 않는다`() {
        withdraw(bearer("SHELTER", shelterSeq = 7)).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
            jsonPath("$.traceId") { exists() }
        }
        then(withdrawUseCase).should(never()).withdraw(anyLong())
    }

    @Test
    fun `토큰이 없으면 401`() {
        withdraw(null).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
        }
    }

    companion object {
        private val keyPair = Jwts.SIG.RS256.keyPair().build()

        private fun bearer(role: String, shelterSeq: Long? = null) = "Bearer " + Jwts.builder()
            .claim("userSeq", 42).claim("role", role).claim("typ", "access")
            .apply { shelterSeq?.let { claim("shelterSeq", it) } }
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
