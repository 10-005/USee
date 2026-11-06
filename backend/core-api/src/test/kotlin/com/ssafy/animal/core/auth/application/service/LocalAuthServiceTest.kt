package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.adapter.out.jwt.JwtProperties
import com.ssafy.animal.core.auth.adapter.out.jwt.JwtTokenIssuer
import com.ssafy.animal.core.auth.application.port.out.KakaoClient
import com.ssafy.animal.core.auth.application.port.out.LoginAttemptStore
import com.ssafy.animal.core.auth.application.port.out.RefreshSession
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.application.port.out.RegistrationContextStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.shelter.application.port.out.ShelterMemberRepository
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AccountStatus
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalAuthServiceTest {

    private val keyPair = Jwts.SIG.RS256.keyPair().build()
    private val issuer = JwtTokenIssuer(
        JwtProperties(
            privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded),
            publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
        ),
    )
    private val encoder = BCryptPasswordEncoder(4)

    private val users = mutableMapOf<Int, User>()
    private val accounts = mutableMapOf<String, UserAccount>()
    private val members = mutableMapOf<Int, Int>()

    /** Redis 스크립트와 같은 규칙: 누적이 max 이하일 때만 TTL 갱신(여기선 횟수만 본다) */
    private val attempts = object : LoginAttemptStore {
        val counts = mutableMapOf<String, Long>()
        override fun record(loginId: String, maxAttempts: Int, window: Duration) = counts.merge(loginId, 1, Long::plus)!!
        override fun clear(loginId: String) {
            counts.remove(loginId)
        }
    }

    private val userRepository = object : UserRepository {
        override fun findById(userSeq: Int) = users[userSeq]
        override fun findActiveAccount(providerCode: String, loginIdentifier: String) =
            accounts[loginIdentifier]?.takeIf { providerCode == AuthProvider.LOCAL }
        override fun existsActiveNickname(nickname: String, exceptUserSeq: Int?) = error("not used")
        override fun existsActivePhone(phone: String, exceptUserSeq: Int?) = error("not used")
        override fun saveAndFlush(user: User) = error("not used")
        override fun findShelterDisplay(userSeq: Int) = error("not used")
        override fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String) = error("not used")
    }

    private val authService = AuthService(
        kakaoClient = object : KakaoClient {
            override fun fetchKakaoId(kakaoAccessToken: String) = error("not used")
        },
        tokenIssuer = issuer,
        refreshTokenStore = object : RefreshTokenStore {
            val sessions = mutableMapOf<Pair<Long, String>, RefreshSession>()
            override fun save(userSeq: Long, jti: String, session: RefreshSession, ttl: Duration) {
                sessions[userSeq to jti] = session
            }
            override fun take(userSeq: Long, jti: String) = sessions.remove(userSeq to jti)
            override fun delete(userSeq: Long, jti: String) {
                sessions.remove(userSeq to jti)
            }
            override fun deleteAll(userSeq: Long) {
                sessions.keys.removeIf { it.first == userSeq }
            }
        },
        registrationContextStore = object : RegistrationContextStore {
            override fun save(registrationToken: String, kakaoId: String, ttl: Duration) = Unit
            override fun find(registrationToken: String) = error("not used")
            override fun delete(registrationToken: String) = error("not used")
        },
        userRepository = userRepository,
        eventPublisher = object : EventPublisher {
            override fun publish(event: Any) = Unit
        },
    )

    private val service = LocalAuthService(
        userRepository = userRepository,
        shelterMemberRepository = object : ShelterMemberRepository {
            override fun findShelterSeq(userSeq: Int) = members[userSeq]
        },
        loginAttemptStore = attempts,
        passwordEncoder = encoder,
        authService = authService,
        properties = LocalLoginProperties(),
    )

    private fun account(
        loginId: String,
        seq: Int,
        shelterSeq: Int? = 7,
        role: Role = Role.SHELTER,
        status: AccountStatus = AccountStatus.ACTIVE,
    ) {
        val user = User(role, null, null, null, null).also {
            ReflectionTestUtils.setField(it, "userSeq", seq)
            ReflectionTestUtils.setField(it, "accountStatus", status)
            users[seq] = it
        }
        accounts[loginId] = UserAccount(user, AuthProvider(2, AuthProvider.LOCAL), loginId, encoder.encode(PASSWORD))
        shelterSeq?.let { members[seq] = it }
    }

    private fun assertFails(code: AuthErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    private fun accessClaims(token: String) = Jwts.parser().verifyWith(keyPair.public).build().parseSignedClaims(token).payload

    @Test
    fun `성공하면 shelterSeq를 담은 토큰을 받고 refresh 후에도 유지된다`() {
        account("admin1", 55, shelterSeq = 7)

        val result = service.login("admin1", PASSWORD)
        assertEquals(55, result.userSeq)
        assertEquals(7, result.shelterSeq)
        assertEquals(3600, result.tokens.expiresIn)
        assertEquals(7, accessClaims(result.tokens.accessToken)["shelterSeq"].toString().toInt())
        assertEquals("SHELTER", accessClaims(result.tokens.accessToken)["role"])

        val refreshed = authService.refresh(result.tokens.refreshToken)
        assertEquals(7, accessClaims(refreshed.accessToken)["shelterSeq"].toString().toInt())
    }

    @Test
    fun `틀린 비밀번호와 없는 ID는 같은 401이고 둘 다 실패로 센다`() {
        account("admin1", 55)
        assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("admin1", "wrong") }
        assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("nobody", PASSWORD) }
        assertEquals(1, attempts.counts["admin1"])
        assertEquals(1, attempts.counts["nobody"])
    }

    @Test
    fun `5회 실패하면 잠기고 잠금 중에는 맞는 비밀번호도 429`() {
        account("admin1", 55)
        repeat(5) { assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("admin1", "wrong") } }
        assertFails(AuthErrorCode.AUTH_LOGIN_LOCKED) { service.login("admin1", PASSWORD) }
    }

    @Test
    fun `없는 ID도 5회 실패하면 잠긴다`() {
        repeat(5) { assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("nobody", "x") } }
        assertFails(AuthErrorCode.AUTH_LOGIN_LOCKED) { service.login("nobody", "x") }
    }

    @Test
    fun `성공하면 실패 카운터를 지운다`() {
        account("admin1", 55)
        repeat(4) { assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("admin1", "wrong") } }
        service.login("admin1", PASSWORD)
        assertNull(attempts.counts["admin1"])
    }

    @Test
    fun `shelter_members가 없거나 정지된 SHELTER는 403이고 실패로 세지 않는다`() {
        account("orphan", 1, shelterSeq = null)
        assertFails(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE) { service.login("orphan", PASSWORD) }
        assertNull(attempts.counts["orphan"])

        account("suspended", 2, status = AccountStatus.SUSPENDED)
        assertFails(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE) { service.login("suspended", PASSWORD) }
    }

    @Test
    fun `LOCAL 계정이라도 SHELTER가 아니면 없는 ID와 같게 401`() {
        account("user", 3, role = Role.USER)
        assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("user", PASSWORD) }
    }

    @Test
    fun `bcrypt 한도를 넘는 비밀번호는 500이 아니라 401`() {
        account("admin1", 55)
        assertFails(AuthErrorCode.AUTH_INVALID_CREDENTIALS) { service.login("admin1", "가".repeat(30)) }
    }

    companion object {
        private const val PASSWORD = "test-password"
    }
}
