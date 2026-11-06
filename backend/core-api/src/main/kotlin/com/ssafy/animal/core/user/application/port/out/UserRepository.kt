package com.ssafy.animal.core.user.application.port.out

import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount

interface UserRepository {
    fun findById(userSeq: Int): User?

    /** 활성(is_deleted=false) 인증 계정을 회원과 함께 조회한다. */
    fun findActiveAccount(providerCode: String, loginIdentifier: String): UserAccount?
}
