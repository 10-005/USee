package com.ssafy.animal.core.user.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.config.CommonWebAutoConfiguration
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.user.application.port.`in`.MyProfile
import com.ssafy.animal.core.user.application.port.`in`.PublicProfile
import com.ssafy.animal.core.user.application.port.`in`.UpdateMyProfileCommand
import com.ssafy.animal.core.user.application.port.`in`.UserProfileUseCase
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.then
import org.mockito.Mockito.never
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
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
import java.time.LocalDate
import java.util.Base64
import java.util.Date
import java.util.Optional

@WebMvcTest(UserController::class)
@ImportAutoConfiguration(CommonWebAutoConfiguration::class)
class UserControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var userProfileUseCase: UserProfileUseCase

    private val me = User(Role.USER, "반려가족", "김가람", LocalDate.of(1997, 5, 12), "01012345678")
        .also { ReflectionTestUtils.setField(it, "userSeq", 42) }

    @Test
    fun `3_1은 내 프로필을 명세 필드대로 돌려준다`() {
        given(userProfileUseCase.getMe(42)).willReturn(MyProfile(me, ImageSummary(1301, "https://cdn/a.jpg")))

        mockMvc.get("/api/core/v1/users/me") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.userSeq") { value(42) }
            jsonPath("$.data.role") { value("USER") }
            jsonPath("$.data.nickname") { value("반려가족") }
            jsonPath("$.data.birthDate") { value("1997-05-12") }
            jsonPath("$.data.phone") { value("01012345678") }
            jsonPath("$.data.profileImage.imageSeq") { value(1301) }
            jsonPath("$.data.profileImage.imageUrl") { value("https://cdn/a.jpg") }
            jsonPath("$.data.accountStatus") { value("ACTIVE") }
        }
    }

    @Test
    fun `SHELTER가 3_1·3_2를 부르면 403 AUTH_FORBIDDEN이고 서비스는 호출하지 않는다`() {
        mockMvc.get("/api/core/v1/users/me") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        mockMvc.patch("/api/core/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"x"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH_FORBIDDEN") }
        }
        then(userProfileUseCase).should(never()).getMe(anyLong())
    }

    @Test
    fun `3_2는 미전달·명시적 null·값을 구분해 넘긴다`() {
        val expected = UpdateMyProfileCommand(
            nickname = Optional.of("새닉네임"),
            birthDate = Optional.of(LocalDate.of(2000, 1, 2)),
            phone = Optional.empty(),
            profileImageSeq = Optional.empty(),
        )
        given(userProfileUseCase.updateMe(42, expected)).willReturn(MyProfile(me, null))

        mockMvc.patch("/api/core/v1/users/me") {
            header(HttpHeaders.AUTHORIZATION, USER_TOKEN)
            contentType = MediaType.APPLICATION_JSON
            content = """{"nickname":"새닉네임","birthDate":"2000-01-02","phone":null,"profileImageSeq":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.profileImage") { value(null as Any?) }
        }
    }

    @Test
    fun `3_5는 공개 필드만 돌려주고 탈퇴 회원은 404 USER_NOT_FOUND`() {
        given(userProfileUseCase.getPublicProfile(18)).willReturn(PublicProfile(18, "발견도우미", Role.USER, null))
        given(userProfileUseCase.getPublicProfile(19)).willThrow(BusinessException(UserErrorCode.USER_NOT_FOUND))

        mockMvc.get("/api/core/v1/users/18/profile") { header(HttpHeaders.AUTHORIZATION, SHELTER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.userSeq") { value(18) }
            jsonPath("$.data.displayName") { value("발견도우미") }
            jsonPath("$.data.role") { value("USER") }
            jsonPath("$.data.profileImage") { value(null as Any?) }
            jsonPath("$.data.name") { doesNotExist() }
            jsonPath("$.data.phone") { doesNotExist() }
        }
        mockMvc.get("/api/core/v1/users/19/profile") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
        }
        // 정수 범위를 넘는 번호는 500이 아니라 400
        mockMvc.get("/api/core/v1/users/9999999999/profile") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON_INVALID_INPUT") }
        }
        // 프로필 사진은 1.4 ImageRef(displayOrder 포함)
        given(userProfileUseCase.getPublicProfile(20)).willReturn(PublicProfile(20, "사진있음", Role.USER, ImageSummary(1301, "https://cdn/1301.jpg")))
        mockMvc.get("/api/core/v1/users/20/profile") { header(HttpHeaders.AUTHORIZATION, USER_TOKEN) }.andExpect {
            status { isOk() }
            jsonPath("$.data.profileImage.imageSeq") { value(1301) }
            jsonPath("$.data.profileImage.displayOrder") { value(1) }
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
