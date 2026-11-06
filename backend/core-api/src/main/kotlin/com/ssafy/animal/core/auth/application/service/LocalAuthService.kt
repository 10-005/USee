package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginResult
import com.ssafy.animal.core.auth.application.port.`in`.LocalLoginUseCase
import com.ssafy.animal.core.auth.application.port.out.LoginAttemptStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.shelter.application.port.out.ShelterMemberRepository
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AuthProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

/** 명세 Q445 [가안] 값. auth.local-login.* 로 바꾼다 */
@ConfigurationProperties("auth.local-login")
data class LocalLoginProperties(
    val maxFailures: Int = 5,
    val lockDuration: Duration = Duration.ofMinutes(15),
)

@Service
@Transactional(readOnly = true)
@EnableConfigurationProperties(LocalLoginProperties::class)
class LocalAuthService(
    private val userRepository: UserRepository,
    private val shelterMemberRepository: ShelterMemberRepository,
    private val loginAttemptStore: LoginAttemptStore,
    private val passwordEncoder: PasswordEncoder,
    private val authService: AuthService,
    private val properties: LocalLoginProperties,
) : LocalLoginUseCase {

    /** 없는 ID도 bcrypt 비교를 한 번 해서 응답 시간으로 계정 존재가 드러나지 않게 한다 */
    private val dummyHash = passwordEncoder.encode(UUID.randomUUID().toString())

    override fun login(loginId: String, password: String): LocalLoginResult {
        // 검증 전에 시도부터 센다: 동시 요청이 카운터를 읽고 함께 통과하는 경쟁을 막는다
        if (loginAttemptStore.record(loginId, properties.maxFailures, properties.lockDuration) > properties.maxFailures) {
            throw BusinessException(AuthErrorCode.AUTH_LOGIN_LOCKED)
        }
        val account = userRepository.findActiveAccount(AuthProvider.LOCAL, loginId)
        // bcrypt는 72바이트 초과 입력에 예외를 던진다. 그런 비밀번호는 저장될 수 없으니 틀린 것으로 본다
        val matches = password.toByteArray().size <= BCRYPT_MAX_BYTES &&
            passwordEncoder.matches(password, account?.passwordHash ?: dummyHash)
        val user = account?.user
        // 탈퇴·LOCAL에 묶인 USER도 없는 ID와 같게 취급(실패로 남김)
        if (user == null || !matches || user.isDeleted || user.role != Role.SHELTER) {
            throw BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS)
        }
        // 비밀번호가 맞았으니 대입 공격이 아니다. 아래 403도 실패 횟수에 넣지 않는다
        loginAttemptStore.clear(loginId)
        if (!user.isActive) throw BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
        val shelterSeq = shelterMemberRepository.findShelterSeq(user.userSeq!!)?.toLong()
            ?: throw BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE)
        return LocalLoginResult(authService.issueTokens(user, shelterSeq), user.userSeq!!.toLong(), shelterSeq)
    }

    private companion object {
        const val BCRYPT_MAX_BYTES = 72
    }
}
