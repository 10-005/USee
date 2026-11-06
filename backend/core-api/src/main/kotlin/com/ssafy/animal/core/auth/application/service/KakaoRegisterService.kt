package com.ssafy.animal.core.auth.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterCommand
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterResult
import com.ssafy.animal.core.auth.application.port.`in`.KakaoRegisterUseCase
import com.ssafy.animal.core.auth.application.port.out.RegistrationContextStore
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserErrorCode
import com.ssafy.animal.core.user.domain.UserProfilePolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class KakaoRegisterService(
    private val registrationContextStore: RegistrationContextStore,
    private val userRepository: UserRepository,
    private val authService: AuthService,
) : KakaoRegisterUseCase {

    /**
     * 가입 컨텍스트는 조회만 하고 성공한 뒤에 지운다. 409·400으로 실패해도 TTL(10분) 안에 다시 시도할 수 있다.
     * 같은 토큰 동시 요청은 user_accounts 부분 UNIQUE가 막고, 예외로 트랜잭션이 롤백되어 미완성 행이 남지 않는다.
     */
    @Transactional
    override fun register(command: KakaoRegisterCommand): KakaoRegisterResult {
        val registrationToken = command.registrationToken?.takeIf { it.isNotBlank() }
        val kakaoId = registrationToken?.let(registrationContextStore::find)
            ?: throw BusinessException(AuthErrorCode.AUTH_INVALID_REGISTRATION_CONTEXT)

        UserProfilePolicy.validate(command.nickname, command.name, command.birthDate, command.phone)
        val nickname = command.nickname!!
        val phone = command.phone!!

        if (userRepository.findActiveAccount(AuthProvider.KAKAO, kakaoId) != null) {
            throw BusinessException(AuthErrorCode.AUTH_KAKAO_ACCOUNT_CONFLICT)
        }
        if (userRepository.existsActiveNickname(nickname)) throw BusinessException(UserErrorCode.USER_DUPLICATE_NICKNAME)
        if (userRepository.existsActivePhone(phone)) throw BusinessException(UserErrorCode.USER_DUPLICATE_PHONE)

        val user = userRepository.saveWithAccount(
            User(Role.USER, nickname, command.name, command.birthDate, phone),
            AuthProvider.KAKAO,
            kakaoId,
        )
        val tokens = authService.issueTokens(user, shelterSeq = null)
        registrationContextStore.delete(registrationToken)
        return KakaoRegisterResult(user, tokens)
    }
}
