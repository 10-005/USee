package com.ssafy.animal.core.user.application.port.`in`

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.user.domain.User
import java.time.LocalDate
import java.util.Optional

interface UserProfileUseCase {
    /** 3.1 내 프로필(USER 전용) */
    fun getMe(userSeq: Long): MyProfile

    /** 3.2 부분 갱신 후 3.1과 같은 프로필 */
    fun updateMe(userSeq: Long, command: UpdateMyProfileCommand): MyProfile

    /** 3.5 공개 프로필. 탈퇴 회원은 404 USER_NOT_FOUND */
    fun getPublicProfile(userSeq: Long): PublicProfile

    /**
     * 닉네임 사용 가능 여부(명세 외, 가입·프로필 수정 화면용). 형식이 틀리면 400 COMMON_INVALID_INPUT.
     * exceptUserSeq(로그인한 USER 본인)의 현재 닉네임은 사용 가능으로 본다(3.2와 같은 판정)
     */
    fun isNicknameAvailable(nickname: String, exceptUserSeq: Long?): Boolean
}

/** 필드별 null = 미전달(유지), Optional.empty() = 명시적 null, Optional.of(v) = 변경 */
data class UpdateMyProfileCommand(
    val nickname: Optional<String>? = null,
    val name: Optional<String>? = null,
    val birthDate: Optional<LocalDate>? = null,
    val phone: Optional<String>? = null,
    val profileImageSeq: Optional<Long>? = null,
)

data class MyProfile(
    val user: User,
    val profileImage: ImageSummary?,
)

data class PublicProfile(
    val userSeq: Long,
    val displayName: String?,
    val role: Role,
    val profileImage: ImageSummary?,
)
