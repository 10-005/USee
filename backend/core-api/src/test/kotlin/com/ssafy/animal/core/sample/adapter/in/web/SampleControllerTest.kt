package com.ssafy.animal.core.sample.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.sample.application.port.`in`.CreateSampleCommand
import com.ssafy.animal.core.sample.adapter.out.persistence.SamplePersistenceAdapter
import com.ssafy.animal.core.sample.application.port.`in`.SampleUseCase
import com.ssafy.animal.core.sample.domain.Sample
import com.ssafy.animal.core.sample.domain.SampleErrorCode
import io.jsonwebtoken.Jwts
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.willAnswer
import org.mockito.BDDMockito.willThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.KeyPairGenerator
import java.time.OffsetDateTime
import java.util.Base64
import java.util.Date

/** common 모듈의 공통 응답 봉투·예외 처리·JWT 인증이 실제 컨트롤러에 적용되는지 확인한다. */
@WebMvcTest(SampleController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class SampleControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var sampleUseCase: SampleUseCase

    @Test
    fun `성공 응답은 봉투로 감싸지고 traceId를 이어받는다`() {
        val sample = sample(1L).also {
            ReflectionTestUtils.setField(it, "regDttm", OffsetDateTime.parse("2026-11-02T01:00:00Z"))
        }
        given(sampleUseCase.get(1L)).willReturn(sample)

        mockMvc.get("/api/core/v1/samples/1") {
            header(HttpHeaders.AUTHORIZATION, bearer())
            header("X-Trace-Id", "trace-123")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value(200) }
            jsonPath("$.code") { value("SUCCESS") }
            jsonPath("$.message") { value("요청이 성공했습니다.") }
            jsonPath("$.data.sampleSeq") { value(1) }
            jsonPath("$.data.regDttm") { value("2026-11-02T10:00:00+09:00") }
            jsonPath("$.errors") { isEmpty() }
            jsonPath("$.traceId") { value("trace-123") }
        }
    }

    @Test
    fun `201 응답은 바디 status도 201이고 SuccessMessage가 적용된다`() {
        given(sampleUseCase.create(CreateSampleCommand("이름", null))).willReturn(sample(1L))

        mockMvc.post("/api/core/v1/samples") {
            header(HttpHeaders.AUTHORIZATION, bearer())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"이름"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value(201) }
            jsonPath("$.message") { value("샘플이 생성되었습니다.") }
            jsonPath("$.data.description") { value(nullValue()) }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `데이터 없는 성공은 200과 data null`() {
        mockMvc.delete("/api/core/v1/samples/1") {
            header(HttpHeaders.AUTHORIZATION, bearer())
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value(200) }
            jsonPath("$.data") { value(nullValue()) }
        }
    }

    @Test
    fun `검증 실패는 400과 필드별 errors`() {
        mockMvc.post("/api/core/v1/samples") {
            header(HttpHeaders.AUTHORIZATION, bearer())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":" "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            jsonPath("$.data") { value(nullValue()) }
            jsonPath("$.errors[0].field") { value("name") }
        }
    }

    @Test
    fun `깨진 JSON은 COMMON_INVALID_JSON`() {
        mockMvc.post("/api/core/v1/samples") {
            header(HttpHeaders.AUTHORIZATION, bearer())
            contentType = MediaType.APPLICATION_JSON
            content = "{"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_JSON") }
        }
    }

    @Test
    fun `도메인 ErrorCode로 던진 BusinessException은 해당 상태와 코드로 응답`() {
        willThrow(BusinessException(SampleErrorCode.SAMPLE_NOT_FOUND)).given(sampleUseCase).get(99L)

        mockMvc.get("/api/core/v1/samples/99") {
            header(HttpHeaders.AUTHORIZATION, bearer())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.status") { value(404) }
            jsonPath("$.code") { value("SAMPLE_NOT_FOUND") }
            jsonPath("$.message") { value("샘플을 찾을 수 없습니다.") }
        }
    }

    @Test
    fun `지원하지 않는 메서드는 405`() {
        mockMvc.post("/api/core/v1/samples/1") {
            header(HttpHeaders.AUTHORIZATION, bearer())
        }.andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("COMMON_METHOD_NOT_ALLOWED") }
        }
    }

    @Test
    fun `목록 sort는 샘플 SortPolicy를 거쳐 허용 필드는 200, 허용 안 된 필드와 잘못된 방향은 400`() {
        // DB 없이 요청 sort → Pageable → SortPolicy 경로만 확인한다
        willAnswer {
            SamplePersistenceAdapter.SORT_POLICY.toOrderSpecifiers(it.getArgument<Pageable>(0).sort)
            PageImpl(emptyList<Sample>())
        }.given(sampleUseCase).getPage(any(Pageable::class.java) ?: Pageable.unpaged())

        listOf("", "?sort=regDttm,asc", "?sort=name,desc&sort=regDttm,asc").forEach { query ->
            mockMvc.get("/api/core/v1/samples$query") {
                header(HttpHeaders.AUTHORIZATION, bearer())
            }.andExpect { status { isOk() } }
        }
        listOf("?sort=sampleSeq,desc", "?sort=regDttm,sideways").forEach { query ->
            mockMvc.get("/api/core/v1/samples$query") {
                header(HttpHeaders.AUTHORIZATION, bearer())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
                jsonPath("$.errors[0].field") { value("sort") }
            }
        }
    }

    @Test
    fun `토큰이 없으면 401 AUTH_UNAUTHORIZED`() {
        mockMvc.get("/api/core/v1/samples/1").andExpect {
            status { isUnauthorized() }
            jsonPath("$.status") { value(401) }
            jsonPath("$.code") { value("AUTH_UNAUTHORIZED") }
            jsonPath("$.traceId") { isNotEmpty() }
        }
    }

    @Test
    fun `만료된 토큰은 AUTH_TOKEN_EXPIRED`() {
        mockMvc.get("/api/core/v1/samples/1") {
            header(HttpHeaders.AUTHORIZATION, bearer(expiresInMillis = -60_000))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_TOKEN_EXPIRED") }
        }
    }

    @Test
    fun `다른 키로 서명한 토큰은 AUTH_TOKEN_INVALID`() {
        val otherKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().private
        val token = Jwts.builder().claim("userSeq", 1).claim("role", "USER").signWith(otherKey, Jwts.SIG.RS256).compact()

        mockMvc.get("/api/core/v1/samples/1") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH_TOKEN_INVALID") }
        }
    }

    private fun sample(sampleSeq: Long) = Sample("이름", null).also {
        ReflectionTestUtils.setField(it, "sampleSeq", sampleSeq)
    }

    private fun bearer(expiresInMillis: Long = 60_000): String {
        val token = Jwts.builder()
            .claim("userSeq", 1)
            .claim("role", "USER")
            .expiration(Date(System.currentTimeMillis() + expiresInMillis))
            .signWith(keyPair.private, Jwts.SIG.RS256)
            .compact()
        return "Bearer $token"
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
