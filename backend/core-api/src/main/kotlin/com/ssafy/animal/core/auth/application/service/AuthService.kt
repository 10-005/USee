package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.application.port.`in`.AuthUseCase
import com.ssafy.animal.core.auth.application.port.`in`.IssuedTokens
import com.ssafy.animal.core.auth.application.port.`in`.KakaoVerifyResult
import com.ssafy.animal.core.auth.application.port.out.KakaoClient
import com.ssafy.animal.core.auth.application.port.out.RefreshSession
import com.ssafy.animal.core.auth.application.port.out.RefreshTokenStore
import com.ssafy.animal.core.auth.application.port.out.RegistrationContextStore
import com.ssafy.animal.core.auth.application.port.out.TokenIssuer
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.common.event.DeviceTokenDeactivateRequested
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

@Service
@Transactional(readOnly = true)
class AuthService(
    private val kakaoClient: KakaoClient,
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val registrationContextStore: RegistrationContextStore,
    private val userRepository: UserRepository,
    private val eventPublisher: EventPublisher,
) : AuthUseCase {

    override fun verifyKakao(kakaoAccessToken: String): KakaoVerifyResult {
        val kakaoId = kakaoClient.fetchKakaoId(kakaoAccessToken)
        val account = userRepository.findActiveAccount(AuthProvider.KAKAO, kakaoId)
        if (account == null) {
            // USER 필수 개인정보가 없어 행을 미리 만들지 않는다. 2.5에서 이 토큰으로 카카오 id를 꺼내 가입
            val registrationToken = newRegistrationToken()
            registrationContextStore.save(registrationToken, kakaoId, REGISTRATION_TTL)
            return KakaoVerifyResult(tokens = null, user = null, registrationToken = registrationToken)
        }
        val user = account.user
        if (user.role != Role.USER) throw BusinessException(AuthErrorCode.AUTH_KAKAO_ACCOUNT_CONFLICT)
        if (!user.isActive) throw BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
        return KakaoVerifyResult(tokens = issueTokens(user, shelterSeq = null), user = user, registrationToken = null)
    }

    override fun refresh(refreshToken: String): IssuedTokens {
        val claims = tokenIssuer.parseRefresh(refreshToken) ?: throw invalidRefresh()
        val session = refreshTokenStore.take(claims.userSeq, claims.jti)
        if (session == null || session.tokenHash != sha256(refreshToken)) {
            // 서명은 맞는데 키가 없다 = 이미 쓰인 jti 재사용. 탈취로 보고 이 사용자의 refresh를 모두 폐기(명세 2.3)
            refreshTokenStore.deleteAll(claims.userSeq)
            throw invalidRefresh()
        }
        val user = userRepository.findById(claims.userSeq.toInt())
        if (user == null || user.isDeleted) {
            refreshTokenStore.deleteAll(claims.userSeq)
            throw invalidRefresh()
        }
        if (!user.isActive) throw BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
        return issueTokens(user, session.shelterSeq)
    }

    override fun logout(userSeq: Long, refreshToken: String, fcmToken: String?) {
        // 이미 폐기·만료된 토큰이어도 결과는 같으므로 204로 끝낸다(멱등). 다른 사람 토큰은 건드리지 않는다
        tokenIssuer.parseRefresh(refreshToken)
            ?.takeIf { it.userSeq == userSeq }
            ?.let { refreshTokenStore.delete(it.userSeq, it.jti) }
        fcmToken?.takeIf { it.isNotBlank() }?.let { eventPublisher.publish(DeviceTokenDeactivateRequested(userSeq, it)) }
    }

    /** 로그인마다 새 jti → 기기마다 refresh 키 1개(다중 기기 허용). ID/PW 로그인(67)도 이 메서드로 발급한다. */
    fun issueTokens(user: User, shelterSeq: Long?): IssuedTokens {
        val userSeq = user.userSeq!!.toLong()
        val jti = UUID.randomUUID().toString()
        val refreshToken = tokenIssuer.issueRefresh(userSeq, jti)
        refreshTokenStore.save(userSeq, jti, RefreshSession(sha256(refreshToken), shelterSeq), tokenIssuer.refreshTokenTtl)
        return IssuedTokens(
            accessToken = tokenIssuer.issueAccess(userSeq, user.role, shelterSeq),
            refreshToken = refreshToken,
            expiresIn = tokenIssuer.accessTokenTtl.seconds,
        )
    }

    private fun invalidRefresh() = BusinessException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN)

    private fun newRegistrationToken(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun sha256(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    companion object {
        /** 명세 Q414 A: 가입 컨텍스트 10분 */
        val REGISTRATION_TTL: Duration = Duration.ofMinutes(10)
        private val random = SecureRandom()
    }
}
