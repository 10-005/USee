package com.ssafy.animal.core.user.application.port.out

import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserAccount

interface UserRepository {
    fun findById(userSeq: Int): User?

    /** 활성(is_deleted=false) 인증 계정을 회원과 함께 조회한다. */
    fun findActiveAccount(providerCode: String, loginIdentifier: String): UserAccount?

    /** 회원의 활성 인증 계정을 회원과 함께 조회한다. 탈퇴했으면 null */
    fun findActiveAccountByUserSeq(userSeq: Int): UserAccount?

    /** 활성 USER 중 닉네임(대소문자 무관)이 같은 회원이 있는지. 수정할 때는 exceptUserSeq로 본인을 뺀다 */
    fun existsActiveNickname(nickname: String, exceptUserSeq: Int? = null): Boolean

    /** 활성 USER 중 전화번호가 같은 회원이 있는지. 수정할 때는 exceptUserSeq로 본인을 뺀다 */
    fun existsActivePhone(phone: String, exceptUserSeq: Int? = null): Boolean

    /** 변경을 바로 반영한다. 부분 UNIQUE 위반은 saveWithAccount와 같은 409로 바꿔 던진다 */
    fun saveAndFlush(user: User): User

    /** SHELTER 계정의 소속 보호소 표시 정보(삭제되지 않은 보호소만). 없으면 null */
    fun findShelterDisplay(userSeq: Int): ShelterDisplay?

    /**
     * 회원과 인증 계정을 함께 저장한다. 사전 조회를 지나 DB 부분 UNIQUE에 걸리면(경쟁 상태)
     * 사전 조회와 같은 409 BusinessException으로 바꿔 던진다.
     */
    fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String): User
}

/** 3.5 SHELTER 공개 프로필용. Shelter 엔티티가 생기기 전까지 네이티브 조회 결과를 담는다 */
interface ShelterDisplay {
    val shelterName: String
    val profileImageSeq: Long?
}
