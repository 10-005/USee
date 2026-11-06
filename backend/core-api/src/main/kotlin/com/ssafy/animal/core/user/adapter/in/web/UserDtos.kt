package com.ssafy.animal.core.user.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.user.application.port.`in`.MyProfile
import com.ssafy.animal.core.user.application.port.`in`.PublicProfile
import com.ssafy.animal.core.user.application.port.`in`.UpdateMyProfileCommand
import com.ssafy.animal.core.user.domain.AccountStatus
import java.time.LocalDate
import java.util.Optional

/** 3.2 부분 갱신. 필드 미전달은 null, 명시적 null은 Optional.empty()로 들어온다 */
data class UpdateMyProfileRequest(
    val nickname: Optional<String>? = null,
    val name: Optional<String>? = null,
    val birthDate: Optional<LocalDate>? = null,
    val phone: Optional<String>? = null,
    val profileImageSeq: Optional<Long>? = null,
) {
    fun toCommand() = UpdateMyProfileCommand(nickname, name, birthDate, phone, profileImageSeq)
}

data class MyProfileResponse(
    val userSeq: Int,
    val role: Role,
    val nickname: String?,
    val name: String?,
    val birthDate: LocalDate?,
    val phone: String?,
    val profileImage: ImageSummary?,
    val accountStatus: AccountStatus,
) {
    companion object {
        fun from(profile: MyProfile) = with(profile.user) {
            MyProfileResponse(userSeq!!, role, nickname, name, birthDate, phone, profile.profileImage, accountStatus)
        }
    }
}

data class PublicProfileResponse(
    val userSeq: Long,
    val displayName: String?,
    val role: Role,
    val profileImage: ImageSummary?,
) {
    companion object {
        fun from(profile: PublicProfile) = PublicProfileResponse(profile.userSeq, profile.displayName, profile.role, profile.profileImage)
    }
}
