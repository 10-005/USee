package com.ssafy.animal.core.user.application.port.out

import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount

interface UserRepository {
    fun findById(userSeq: Int): User?

    /** 활성(is_deleted=false) 인증 계정을 회원과 함께 조회한다. */
    fun findActiveAccount(providerCode: String, loginIdentifier: String): UserAccount?

    /** 활성 USER 중 닉네임(대소문자 무관)이 같은 회원이 있는지 */
    fun existsActiveNickname(nickname: String): Boolean

    /** 활성 USER 중 전화번호가 같은 회원이 있는지 */
    fun existsActivePhone(phone: String): Boolean

    /**
     * 회원과 인증 계정을 함께 저장한다. 사전 조회를 지나 DB 부분 UNIQUE에 걸리면(경쟁 상태)
     * 사전 조회와 같은 409 BusinessException으로 바꿔 던진다.
     */
    fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String): User
}
