package com.ssafy.animal.core

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.core.auth.application.port.`in`.AuthUseCase
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterUseCase
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginUseCase
import com.ssafy.animal.core.auth.application.port.`in`.WithdrawUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.CatalogQueryUseCase
import com.ssafy.animal.core.catalog.application.port.`in`.GeocodeUseCase
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.missing.application.port.`in`.MissingReportUseCase
import com.ssafy.animal.core.pet.application.port.`in`.PetUseCase
import com.ssafy.animal.core.sample.application.port.`in`.SampleUseCase
import com.ssafy.animal.core.user.application.port.`in`.UserProfileUseCase
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.configuration.SpringDocPageableConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiOAuthProperties
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration
import org.springdoc.webmvc.ui.SwaggerConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.Pageable
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.KeyPairGenerator
import java.util.Base64

/** springdoc-common.yml의 프로필별 문서 노출 규칙을 확인한다. 모든 요청은 토큰 없이 보낸다. */
@ImportAutoConfiguration(
    CommonWebAutoConfiguration::class,
    SpringDocConfiguration::class,
    SpringDocPageableConfiguration::class,
    SpringDocConfigProperties::class,
    SpringDocWebMvcConfiguration::class,
    SwaggerConfig::class,
    SwaggerUiConfigProperties::class,
    SwaggerUiOAuthProperties::class,
)
abstract class SwaggerDocsTestBase {

    @Autowired
    lateinit var mockMvc: MockMvc

    // 문서에 실제 컨트롤러가 잡히도록 컨트롤러는 띄우고 유스케이스만 목으로 둔다
    @MockitoBean
    lateinit var petUseCase: PetUseCase

    @MockitoBean
    lateinit var missingReportUseCase: MissingReportUseCase

    @MockitoBean
    lateinit var sampleUseCase: SampleUseCase

    @MockitoBean
    lateinit var imageUseCase: ImageUseCase

    @MockitoBean
    lateinit var authUseCase: AuthUseCase

    @MockitoBean
    lateinit var kakaoRegisterUseCase: KakaoRegisterUseCase

    @MockitoBean
    lateinit var localLoginUseCase: LocalLoginUseCase

    @MockitoBean
    lateinit var userProfileUseCase: UserProfileUseCase

    @MockitoBean
    lateinit var withdrawUseCase: WithdrawUseCase

    @MockitoBean
    lateinit var catalogQueryUseCase: CatalogQueryUseCase

    @MockitoBean
    lateinit var geocodeUseCase: GeocodeUseCase

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            val publicKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(publicKey.encoded) }
        }
    }
}

@WebMvcTest
@ActiveProfiles("dev")
class SwaggerDocsDevHubTest : SwaggerDocsTestBase() {

    @Test
    fun `dev의 core-api는 통합 Swagger UI를 띄운다`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }

    @Test
    fun `swagger-config에 4개 서비스 문서가 드롭다운으로 묶인다`() {
        mockMvc.get("/api/core/v3/api-docs/swagger-config").andExpect {
            status { isOk() }
            jsonPath("$.configUrl") { value("/api/core/v3/api-docs/swagger-config") }
            jsonPath("$['urls.primaryName']") { value("core-api") }
            jsonPath("$.urls[*].url") {
                value(
                    containsInAnyOrder(
                        "/api/core/v3/api-docs",
                        "/api/chat/v3/api-docs",
                        "/api/notification/v3/api-docs",
                        "/api/simulation/v3/api-docs",
                    ),
                )
            }
        }
    }

    @Test
    fun `api-docs에 Bearer JWT 인증 정의가 있다`() {
        mockMvc.get("/api/core/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.securitySchemes.bearerAuth.type") { value("http") }
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat") { value("JWT") }
            jsonPath("$.security[0].bearerAuth") { isArray() }
        }
    }

    @Test
    fun `목록 API의 Pageable은 page·size·sort 개별 쿼리 파라미터로 문서화된다`() {
        val params = "$.paths['/api/core/v1/samples'].get.parameters"
        mockMvc.get("/api/core/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$params[*].name") { value(containsInAnyOrder("page", "size", "sort")) }
            jsonPath("$params[*].in") { value(containsInAnyOrder("query", "query", "query")) }
            jsonPath("$params[?(@.name == 'pageable')]") { value(empty<Any>()) }
            jsonPath("$.components.schemas.Pageable") { doesNotExist() }
        }
    }

    @Test
    fun `AuthenticationPrincipal AuthUser는 쿼리 파라미터로 문서화되지 않는다`() {
        val params = "$.paths['$AUTH_USER_DOCS_PATH'].get.parameters"
        mockMvc.get("/api/core/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$params[*].name") { value(containsInAnyOrder("page", "size", "sort")) }
            jsonPath("$params[?(@.name in ['userSeq', 'role', 'shelterSeq'])]") { value(empty<Any>()) }
        }
    }
}

private const val AUTH_USER_DOCS_PATH = "/api/core/v1/test-docs/auth-user"

/** 문서 검증 전용. AuthUser와 Pageable을 함께 받는 목록 API 모양을 흉내 낸다. */
@RestController
class AuthUserDocsTestController {
    @GetMapping(AUTH_USER_DOCS_PATH)
    fun list(@AuthenticationPrincipal user: AuthUser, pageable: Pageable) = Unit
}

/** core-api 외 서비스(chat 등)는 api.docs-hub가 없으므로 같은 설정에서 이렇게 동작한다. */
@WebMvcTest(properties = ["api.prefix=/api/chat", "api.docs-hub=false"])
@ActiveProfiles("dev")
class SwaggerDocsDevOtherServiceTest : SwaggerDocsTestBase() {

    @Test
    fun `dev의 다른 서비스는 Swagger UI 없이 api-docs만 제공한다`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isNotFound() } }
        mockMvc.get("/api/chat/v3/api-docs").andExpect { status { isOk() } }
    }
}

@WebMvcTest
@ActiveProfiles("prod")
class SwaggerDocsProdTest : SwaggerDocsTestBase() {

    @Test
    fun `prod에서는 Swagger UI와 api-docs 모두 404`() {
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isNotFound() } }
        mockMvc.get("/api/core/v3/api-docs").andExpect { status { isNotFound() } }
    }
}
