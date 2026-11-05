package com.ssafy.animal.core

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.core.sample.adapter.`in`.web.SampleController
import com.ssafy.animal.core.sample.application.port.`in`.SampleUseCase
import com.ssafy.animal.core.sample.domain.Sample
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.options
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date

private const val DEV_ORIGIN = "http://localhost:5173"

@WebMvcTest(SampleController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
abstract class CorsTestBase {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var sampleUseCase: SampleUseCase

    fun preflight(origin: String) = mockMvc.options("/api/core/v1/samples") {
        header(HttpHeaders.ORIGIN, origin)
        header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
    }

    companion object {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()!!

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}

/** dev 기본값(common.yml)은 웹 Vite 개발 서버 출처만 허용한다. */
@ActiveProfiles("dev")
class CorsDevTest : CorsTestBase() {

    @Test
    fun `허용된 출처의 preflight는 토큰 없이 200과 CORS 헤더`() {
        preflight(DEV_ORIGIN).andExpect {
            status { isOk() }
            header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN) }
            header { string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600") }
            header { doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS) }
        }
    }

    @Test
    fun `허용되지 않은 출처의 preflight는 403이고 CORS 헤더가 없다`() {
        preflight("https://evil.example.com").andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) }
        }
    }

    @Test
    fun `실제 GET 응답에 CORS 헤더와 X-Trace-Id 노출 헤더가 붙는다`() {
        given(sampleUseCase.get(1L)).willReturn(
            Sample("이름", null).also { ReflectionTestUtils.setField(it, "sampleSeq", 1L) },
        )
        val token = Jwts.builder().claim("userSeq", 1).claim("role", "USER")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        mockMvc.get("/api/core/v1/samples/1") {
            header(HttpHeaders.ORIGIN, DEV_ORIGIN)
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, DEV_ORIGIN) }
            header { string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Trace-Id") }
            header { exists("X-Trace-Id") }
        }
    }
}

/** prod 등 local·dev가 아닌 프로필은 기본 허용 출처가 없다. */
@ActiveProfiles("prod")
class CorsProdTest : CorsTestBase() {

    @Test
    fun `prod는 localhost 출처도 거부한다`() {
        preflight(DEV_ORIGIN).andExpect {
            status { isForbidden() }
            header { doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) }
        }
    }
}

class CorsWildcardTest {

    @Test
    fun `허용 출처에 와일드카드가 있으면 기동에 실패한다`() {
        assertThrows<IllegalArgumentException> {
            CommonWebAutoConfiguration().corsConfigurationSource(arrayOf(DEV_ORIGIN, "https://*.vercel.app"))
        }
        assertThrows<IllegalArgumentException> {
            CommonWebAutoConfiguration().corsConfigurationSource(arrayOf("*"))
        }
    }
}
