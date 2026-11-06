package com.ssafy.animal.core.user.adapter.out.persistence

import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

interface UserJpaRepository : JpaRepository<User, Int>

interface UserAccountJpaRepository : JpaRepository<UserAccount, Int> {
    @Query(
        """
        select a from UserAccount a join fetch a.user join a.provider p
        where p.providerCode = :providerCode and a.loginIdentifier = :loginIdentifier and a.isDeleted = false
        """,
    )
    fun findActive(@Param("providerCode") providerCode: String, @Param("loginIdentifier") loginIdentifier: String): UserAccount?
}

@Repository
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val userAccountJpaRepository: UserAccountJpaRepository,
) : UserRepository {

    override fun findById(userSeq: Int): User? = userJpaRepository.findByIdOrNull(userSeq)

    override fun findActiveAccount(providerCode: String, loginIdentifier: String): UserAccount? =
        userAccountJpaRepository.findActive(providerCode, loginIdentifier)
}
