package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.auth.adapter.out.jwt.JwtProperties
import com.ssafy.animal.core.auth.adapter.out.jwt.JwtTokenIssuer
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterCommand
import com.ssafy.animal.core.auth.application.port.out.KakaoClient
import com.ssafy.animal.core.auth.application.port.out.RefreshSession
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.application.port.out.RegistrationContextStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount
import com.ssafy.animal.core.user.domain.UserErrorCode
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KakaoRegisterServiceTest {

    private val keyPair = Jwts.SIG.RS256.keyPair().build()
    private val issuer = JwtTokenIssuer(
        JwtProperties(
            privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded),
            publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
        ),
    )

    private val registrations = mutableMapOf<String, String>()
    private val refreshSessions = mutableMapOf<Pair<Long, String>, RefreshSession>()
    private val users = mutableListOf<User>()
    private val accounts = mutableMapOf<String, UserAccount>()

    private val registrationStore = object : RegistrationContextStore {
        override fun save(registrationToken: String, kakaoId: String, ttl: Duration) {
            registrations[registrationToken] = kakaoId
        }
        override fun find(registrationToken: String) = registrations[registrationToken]
        override fun delete(registrationToken: String) {
            registrations.remove(registrationToken)
        }
    }

    private val userRepository = object : UserRepository {
        override fun findById(userSeq: Int) = users.find { it.userSeq == userSeq }
        override fun findActiveAccount(providerCode: String, loginIdentifier: String) = accounts[loginIdentifier]
        override fun findActiveAccountByUserSeq(userSeq: Int) = error("탈퇴 테스트에서만 쓴다")
        override fun existsActiveNickname(nickname: String, exceptUserSeq: Int?) = users.any { it.nickname.equals(nickname, ignoreCase = true) }
        override fun existsActivePhone(phone: String, exceptUserSeq: Int?) = users.any { it.phone == phone }
        override fun saveAndFlush(user: User) = error("not used")
        override fun findShelterDisplay(userSeq: Int) = error("not used")
        override fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String): User {
            ReflectionTestUtils.setField(user, "userSeq", users.size + 1)
            users += user
            accounts[loginIdentifier] = UserAccount(user, AuthProvider(1, providerCode), loginIdentifier, null)
            return user
        }
    }

    private val authService = AuthService(
        kakaoClient = object : KakaoClient {
            override fun fetchKakaoId(kakaoAccessToken: String) = error("가입에서는 호출하지 않는다")
        },
        tokenIssuer = issuer,
        refreshTokenStore = object : RefreshTokenStore {
            override fun save(userSeq: Long, jti: String, session: RefreshSession, ttl: Duration) {
                refreshSessions[userSeq to jti] = session
            }
            override fun take(userSeq: Long, jti: String) = refreshSessions.remove(userSeq to jti)
            override fun delete(userSeq: Long, jti: String) {
                refreshSessions.remove(userSeq to jti)
            }
            override fun deleteAll(userSeq: Long) = Unit
        },
        registrationContextStore = registrationStore,
        userRepository = userRepository,
        eventPublisher = object : EventPublisher {
            override fun publish(event: Any) = Unit
        },
    )

    private val service = KakaoRegisterService(registrationStore, userRepository, authService)

    private fun command(
        token: String? = "reg",
        nickname: String? = "반려가족",
        name: String? = "김가람",
        birthDate: LocalDate? = LocalDate.of(1997, 5, 12),
        phone: String? = "01012345678",
    ) = KakaoRegisterCommand(token, nickname, name, birthDate, phone)

    private fun assertFails(code: ErrorCode, block: () -> Unit) =
        assertFailsWith<BusinessException> { block() }.also { assertEquals(code, it.errorCode) }

    @Test
    fun `가입 성공 시 USER와 KAKAO 계정을 만들고 토큰을 발급한 뒤 가입 컨텍스트를 지운다`() {
        registrations["reg"] = "kakao-1"
        val result = service.register(command())

        assertEquals(Role.USER, result.user.role)
        assertEquals(1, result.user.userSeq)
        assertEquals("kakao-1", accounts.keys.single())
        assertEquals(1L, issuer.parseRefresh(result.tokens.refreshToken)?.userSeq)
        assertEquals(1, refreshSessions.size)
        assertNull(registrations["reg"])
    }

    @Test
    fun `가입 컨텍스트가 없거나 만료되었거나 토큰이 비면 401`() {
        assertFails(AuthErrorCode.AUTH_INVALID_REGISTRATION_CONTEXT) { service.register(command(token = "expired")) }
        assertFails(AuthErrorCode.AUTH_INVALID_REGISTRATION_CONTEXT) { service.register(command(token = null)) }
        assertFails(AuthErrorCode.AUTH_INVALID_REGISTRATION_CONTEXT) { service.register(command(token = " ")) }
        assertTrue(users.isEmpty())
    }

    @Test
    fun `닉네임 중복은 대소문자 무관 409이고 가입 컨텍스트가 남아 다른 닉네임으로 다시 시도할 수 있다`() {
        userRepository.saveWithAccount(User(Role.USER, "Dog", "기존", LocalDate.of(1990, 1, 1), "01000000000"), AuthProvider.KAKAO, "kakao-0")
        registrations["reg"] = "kakao-1"

        assertFails(UserErrorCode.USER_DUPLICATE_NICKNAME) { service.register(command(nickname = "dOG")) }
        assertEquals("kakao-1", registrations["reg"])

        service.register(command(nickname = "Cat"))
        assertNull(registrations["reg"])
    }

    @Test
    fun `전화번호 중복은 409`() {
        userRepository.saveWithAccount(User(Role.USER, "기존", "기존", LocalDate.of(1990, 1, 1), "01012345678"), AuthProvider.KAKAO, "kakao-0")
        registrations["reg"] = "kakao-1"
        assertFails(UserErrorCode.USER_DUPLICATE_PHONE) { service.register(command()) }
    }

    @Test
    fun `이미 연결된 카카오 계정이면 409`() {
        registrations["reg"] = "kakao-1"
        service.register(command())
        registrations["reg2"] = "kakao-1"
        assertFails(AuthErrorCode.AUTH_KAKAO_ACCOUNT_CONFLICT) {
            service.register(command(token = "reg2", nickname = "다른닉", phone = "01099999999"))
        }
    }

    @Test
    fun `필수 4종 누락은 400 USER_REQUIRED_PROFILE_MISSING, 누락 필드를 errors에 담는다`() {
        registrations["reg"] = "kakao-1"
        val e = assertFails(UserErrorCode.USER_REQUIRED_PROFILE_MISSING) {
            service.register(command(nickname = " ", birthDate = null))
        }
        assertEquals(listOf("nickname", "birthDate"), e.errors.map { it.field })
    }

    @Test
    fun `닉네임 형식 위반과 길이 초과는 400 COMMON_INVALID_INPUT`() {
        registrations["reg"] = "kakao-1"
        listOf("가", "열한글자닉네임입니다요", "반려 가족", "반려-가족", "반려,가족", "😀😀").forEach { bad ->
            assertFails(CommonErrorCode.COMMON_INVALID_INPUT) { service.register(command(nickname = bad)) }
        }
        val e = assertFails(CommonErrorCode.COMMON_INVALID_INPUT) {
            service.register(command(name = "가".repeat(101), phone = "0".repeat(31)))
        }
        assertEquals(listOf("name", "phone"), e.errors.map { it.field })

        service.register(command(nickname = "a.b_C9"))
    }
}
