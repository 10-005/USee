package com.ssafy.animal.core.shelter.adapter.`in`.web

import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.catalog.domain.CatalogErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterUseCase
import com.ssafy.animal.core.shelter.application.port.`in`.ShelterView
import com.ssafy.animal.core.shelter.application.port.`in`.UpdateShelterCommand
import com.ssafy.animal.core.shelter.domain.Shelter
import com.ssafy.animal.core.shelter.domain.ShelterErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.Date
import java.util.Optional

@WebMvcTest(ShelterController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class ShelterControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var shelterUseCase: ShelterUseCase

    private val shelter = Shelter("구미시 동물보호센터", "경상북도 구미시 옥성면 선상동로 1358", 36.34, 128.27, "054-481-5809", "47190")
        .also {
            ReflectionTestUtils.setField(it, "shelterSeq", 7)
            it.updateOperation("센터 소개", "09:00~18:00", "일요일", 1701, 1702, 77)
        }
    private val view = ShelterView(shelter, ImageSummary(1701, "https://cdn/p.jpg"), ImageSummary(1702, "https://cdn/b.jpg"))

    @Test
    fun `9_1은 목록 필드만 내리고 기본 정렬 없이 페이지를 넘긴다`() {
        given(shelterUseCase.getPage("구미", "47190", PageRequest.of(0, 20)))
            .willReturn(PageImpl(listOf(view), PageRequest.of(0, 20), 1))

        mockMvc.get("/api/core/v1/shelters?keyword=구미&districtCode=47190") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].shelterSeq") { value(7) }
            jsonPath("$.data.content[0].shelterName") { value("구미시 동물보호센터") }
            jsonPath("$.data.content[0].profileImage.imageUrl") { value("https://cdn/p.jpg") }
            jsonPath("$.data.content[0].description") { doesNotExist() }
            jsonPath("$.data.content[0].backgroundImage") { doesNotExist() }
            jsonPath("$.data.totalElements") { value(1) }
        }
    }

    @Test
    fun `9_1 없는 districtCode는 400 CATALOG_INVALID_DISTRICT_CODE`() {
        given(shelterUseCase.getPage(null, "99999", PageRequest.of(0, 20, Sort.by("shelterName"))))
            .willThrow(BusinessException(CatalogErrorCode.CATALOG_INVALID_DISTRICT_CODE))

        mockMvc.get("/api/core/v1/shelters?districtCode=99999&sort=shelterName,asc") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("CATALOG_INVALID_DISTRICT_CODE") }
        }
    }

    @Test
    fun `9_2는 상세를 내리고 externalShelterCode는 없다, 없는 보호소는 404`() {
        given(shelterUseCase.get(7)).willReturn(view)
        given(shelterUseCase.get(8)).willThrow(BusinessException(ShelterErrorCode.SHELTER_NOT_FOUND))

        mockMvc.get("/api/core/v1/shelters/7") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.operatingHours") { value("09:00~18:00") }
            jsonPath("$.data.backgroundImage.imageSeq") { value(1702) }
            jsonPath("$.data.externalShelterCode") { doesNotExist() }
        }
        mockMvc.get("/api/core/v1/shelters/8") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SHELTER_NOT_FOUND") }
        }
    }

    @Test
    fun `9_3은 토큰 shelterSeq로 조회하고 USER는 403`() {
        given(shelterUseCase.get(7)).willReturn(view)

        mockMvc.get("/api/core/v1/shelters/me") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.shelterSeq") { value(7) }
        }
        mockMvc.get("/api/core/v1/shelters/me") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
    }

    @Test
    fun `9_4는 미전달·null·값을 구분해 넘긴다`() {
        val expected = UpdateShelterCommand(operatingHours = Optional.of("10:00~17:00"), profileImageSeq = Optional.empty())
        given(shelterUseCase.updateMine(77, 7, expected)).willReturn(view)

        mockMvc.patch("/api/core/v1/shelters/me") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"operatingHours":"10:00~17:00","profileImageSeq":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.shelterSeq") { value(7) }
        }
    }

    @Test
    fun `9_4에 식별정보가 있으면 null이어도 400 COMMON_INVALID_INPUT`() {
        mockMvc.patch("/api/core/v1/shelters/me") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"description":"소개","shelterName":"새 이름","phone":null}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
            jsonPath("$.errors.length()") { value(2) }
        }
        verifyNoInteractions(shelterUseCase)
    }

    @Test
    fun `9_4 남의 이미지는 403 IMAGE_NOT_OWNED`() {
        given(shelterUseCase.updateMine(77, 7, UpdateShelterCommand(backgroundImageSeq = Optional.of(999))))
            .willThrow(BusinessException(ImageErrorCode.IMAGE_NOT_OWNED))

        mockMvc.patch("/api/core/v1/shelters/me") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"backgroundImageSeq":999}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("IMAGE_NOT_OWNED") }
        }
    }

    companion object {
        private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        private fun bearer(claims: Map<String, Any>) = "Bearer " + Jwts.builder().claims(claims).claim("typ", "access")
            .expiration(Date(System.currentTimeMillis() + 3_600_000))
            .signWith(keyPair.private, Jwts.SIG.RS256).compact()

        private val USER_TOKEN = bearer(mapOf("userSeq" to 42, "role" to "USER"))
        private val SHELTER_TOKEN = bearer(mapOf("userSeq" to 77, "role" to "SHELTER", "shelterSeq" to 7))

        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.public-key") { Base64.getEncoder().encodeToString(keyPair.public.encoded) }
        }
    }
}
