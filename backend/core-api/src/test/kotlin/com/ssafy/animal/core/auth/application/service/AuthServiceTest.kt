package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.adapter.out.jwt.JwtProperties
import com.ssafy.animal.core.auth.adapter.out.jwt.JwtTokenIssuer
import com.ssafy.animal.core.auth.application.port.out.KakaoClient
import com.ssafy.animal.core.auth.application.port.out.RefreshSession
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.application.port.out.RegistrationContextStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.auth.domain.LogoutDeviceTokenEvent
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AccountStatus
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.Test
import org.springframework.test.util.ReflectionTestUtils
import java.time.Duration
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private val keyPair = Jwts.SIG.RS256.keyPair().build()
    private val issuer = JwtTokenIssuer(
        JwtProperties(
            privateKey = Base64.getEncoder().encodeToString(keyPair.private.encoded),
            publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded),
        ),
    )

    private val refreshStore = object : RefreshTokenStore {
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
    }
    private val registrations = mutableMapOf<String, String>()
    private val events = mutableListOf<Any>()
    private val users = mutableMapOf<Int, User>()
    private val kakaoAccounts = mutableMapOf<String, UserAccount>()

    private val service = AuthService(
        kakaoClient = object : KakaoClient {
            override fun fetchKakaoId(kakaoAccessToken: String) = "kakao-$kakaoAccessToken"
        },
        tokenIssuer = issuer,
        refreshTokenStore = refreshStore,
        registrationContextStore = object : RegistrationContextStore {
            override fun save(registrationToken: String, kakaoId: String, ttl: Duration) {
                registrations[registrationToken] = kakaoId
            }
            override fun find(registrationToken: String) = registrations[registrationToken]
            override fun delete(registrationToken: String) {
                registrations.remove(registrationToken)
            }
        },
        userRepository = object : UserRepository {
            override fun findById(userSeq: Int) = users[userSeq]
            override fun findActiveAccount(providerCode: String, loginIdentifier: String) = kakaoAccounts[loginIdentifier]
            override fun existsActiveNickname(nickname: String) = false
            override fun existsActivePhone(phone: String) = false
            override fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String) = user
        },
        eventPublisher = object : EventPublisher {
            override fun publish(event: Any) {
                events += event
            }
        },
    )

    private fun user(seq: Int, role: Role = Role.USER, status: AccountStatus = AccountStatus.ACTIVE, deleted: Boolean = false) =
        User(role, "반려가족", "김가람", LocalDate.of(1997, 5, 12), "01012345678").also {
            ReflectionTestUtils.setField(it, "userSeq", seq)
            ReflectionTestUtils.setField(it, "accountStatus", status)
            ReflectionTestUtils.setField(it, "isDeleted", deleted)
            users[seq] = it
        }

    private fun linkKakao(token: String, user: User) {
        kakaoAccounts["kakao-$token"] = UserAccount(user, AuthProvider(1, AuthProvider.KAKAO), "kakao-$token", null)
    }

    private fun assertFails(code: AuthErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)

    @Test
    fun `기존 회원은 토큰을 받고 refresh가 저장된다`() {
        linkKakao("t", user(17))
        val result = service.verifyKakao("t")
        val tokens = assertNotNull(result.tokens)
        assertEquals(17, result.user?.userSeq)
        assertNull(result.registrationToken)
        assertEquals(3600, tokens.expiresIn)
        assertEquals(1, refreshStore.sessions.size)
    }

    @Test
    fun `미가입 카카오 계정은 행 없이 가입 컨텍스트만 저장한다`() {
        val result = service.verifyKakao("new")
        assertNull(result.tokens)
        assertEquals("kakao-new", registrations[assertNotNull(result.registrationToken)])
        assertTrue(refreshStore.sessions.isEmpty())
    }

    @Test
    fun `정지 회원은 403, 일반회원이 아닌 계정은 409`() {
        linkKakao("s", user(1, status = AccountStatus.SUSPENDED))
        assertFails(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE) { service.verifyKakao("s") }
        linkKakao("h", user(2, role = Role.SHELTER))
        assertFails(AuthErrorCode.AUTH_KAKAO_ACCOUNT_CONFLICT) { service.verifyKakao("h") }
    }

    @Test
    fun `refresh는 rotation되고 이전 토큰 재사용은 해당 사용자 refresh 전부 폐기`() {
        linkKakao("t", user(17))
        val first = assertNotNull(service.verifyKakao("t").tokens)
        val otherDevice = assertNotNull(service.verifyKakao("t").tokens)

        val second = service.refresh(first.refreshToken)
        assertEquals(2, refreshStore.sessions.size) // first 폐기, second 추가, otherDevice 유지

        assertFails(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN) { service.refresh(first.refreshToken) }
        assertTrue(refreshStore.sessions.isEmpty())
        assertFails(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN) { service.refresh(second.refreshToken) }
        assertFails(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN) { service.refresh(otherDevice.refreshToken) }
    }

    @Test
    fun `탈퇴 회원 refresh는 401, 정지 회원은 403`() {
        val u = user(17)
        linkKakao("t", u)
        val tokens = assertNotNull(service.verifyKakao("t").tokens)
        ReflectionTestUtils.setField(u, "isDeleted", true)
        assertFails(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN) { service.refresh(tokens.refreshToken) }

        val s = user(18)
        linkKakao("s", s)
        val sTokens = assertNotNull(service.verifyKakao("s").tokens)
        ReflectionTestUtils.setField(s, "accountStatus", AccountStatus.SUSPENDED)
        assertFails(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE) { service.refresh(sTokens.refreshToken) }
    }

    @Test
    fun `logout은 본인 refresh만 폐기하고 fcmToken이 있으면 이벤트를 발행한다`() {
        linkKakao("t", user(17))
        val tokens = assertNotNull(service.verifyKakao("t").tokens)

        service.logout(99, tokens.refreshToken, null)
        assertEquals(1, refreshStore.sessions.size)
        assertTrue(events.isEmpty())

        service.logout(17, tokens.refreshToken, "fcm-1")
        assertTrue(refreshStore.sessions.isEmpty())
        assertEquals(listOf<Any>(LogoutDeviceTokenEvent(17, "fcm-1")), events)
    }
}
