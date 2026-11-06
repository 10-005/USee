package com.ssafy.animal.core.user.adapter.out.persistence

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.core.auth.domain.AuthErrorCode
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.AuthProvider
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount
import com.ssafy.animal.core.user.domain.UserErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface UserJpaRepository : JpaRepository<User, Int> {
    @Query("select count(u) > 0 from User u where u.role = :role and u.isDeleted = false and lower(u.nickname) = lower(:nickname)")
    fun existsActiveNickname(@Param("role") role: Role, @Param("nickname") nickname: String): Boolean

    @Query("select count(u) > 0 from User u where u.role = :role and u.isDeleted = false and u.phone = :phone")
    fun existsActivePhone(@Param("role") role: Role, @Param("phone") phone: String): Boolean
}

interface UserAccountJpaRepository : JpaRepository<UserAccount, Int> {
    @Query(
        """
        select a from UserAccount a join fetch a.user join a.provider p
        where p.providerCode = :providerCode and a.loginIdentifier = :loginIdentifier and a.isDeleted = false
        """,
    )
    fun findActive(@Param("providerCode") providerCode: String, @Param("loginIdentifier") loginIdentifier: String): UserAccount?
}

interface AuthProviderJpaRepository : JpaRepository<AuthProvider, Int> {
    fun findByProviderCode(providerCode: String): AuthProvider?
}

@Repository
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val authProviderJpaRepository: AuthProviderJpaRepository,
) : UserRepository {

    override fun findById(userSeq: Int): User? = userJpaRepository.findByIdOrNull(userSeq)

    override fun findActiveAccount(providerCode: String, loginIdentifier: String): UserAccount? =
        userAccountJpaRepository.findActive(providerCode, loginIdentifier)

    // 부분 UNIQUE가 role=USER 활성 행만 대상이므로 사전 조회도 같은 범위로 본다
    override fun existsActiveNickname(nickname: String) = userJpaRepository.existsActiveNickname(Role.USER, nickname)

    override fun existsActivePhone(phone: String) = userJpaRepository.existsActivePhone(Role.USER, phone)

    override fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String): User {
        val provider = checkNotNull(authProviderJpaRepository.findByProviderCode(providerCode)) { "auth_providers 시드 없음: $providerCode" }
        try {
            // IDENTITY라 save 시점에 INSERT가 나가므로 UNIQUE 위반도 여기서 잡힌다
            userJpaRepository.save(user)
            userAccountJpaRepository.save(UserAccount(user, provider, loginIdentifier, passwordHash = null))
            return user
        } catch (e: DataIntegrityViolationException) {
            throw translate(e) ?: e
        }
    }

    companion object {
        /** 부분 UNIQUE 인덱스 이름(V1) → 사전 조회와 같은 오류. PostgreSQL 오류 메시지에 인덱스 이름이 들어 있다 */
        fun translate(e: DataIntegrityViolationException): BusinessException? {
            val message = e.mostSpecificCause.message.orEmpty()
            return when {
                "uq_users_active_nickname" in message -> BusinessException(UserErrorCode.USER_DUPLICATE_NICKNAME)
                "uq_users_active_phone" in message -> BusinessException(UserErrorCode.USER_DUPLICATE_PHONE)
                "uq_user_accounts_active_login" in message -> BusinessException(AuthErrorCode.AUTH_KAKAO_ACCOUNT_CONFLICT)
                else -> null
            }
        }
    }
}
