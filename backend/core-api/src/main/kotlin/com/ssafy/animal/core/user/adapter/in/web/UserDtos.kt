package com.ssafy.animal.core.user.adapter.`in`.web

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.OrderedImage
import com.ssafy.animal.core.user.application.port.`in`.MyProfile
import com.ssafy.animal.core.user.application.port.`in`.PublicProfile
import com.ssafy.animal.core.user.application.port.`in`.UpdateMyProfileCommand
import com.ssafy.animal.core.user.domain.AccountStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.Optional
import com.ssafy.animal.core.image.application.port.`in`.ordered

/** 3.2 부분 갱신. 필드 미전달은 null, 명시적 null은 Optional.empty()로 들어온다 */
data class UpdateMyProfileRequest(
    @field:Schema(description = "닉네임. 2~10자, 한글·영문·숫자와 . _만, 공백 불가. 활성 회원 간 중복 불가(대소문자 무시). 생략하면 유지, null·공백은 400", nullable = true, example = "새닉네임")
    val nickname: Optional<String>? = null,
    @field:Schema(description = "이름. 최대 100자. 생략하면 유지, null·공백은 400", nullable = true, example = "김가람")
    val name: Optional<String>? = null,
    @field:Schema(description = "생년월일(YYYY-MM-DD). 생략하면 유지, null은 400", nullable = true, example = "1997-05-12")
    val birthDate: Optional<LocalDate>? = null,
    @field:Schema(description = "전화번호. 최대 30자. 활성 회원 간 중복 불가. 생략하면 유지, null·공백은 400", nullable = true, example = "01012345678")
    val phone: Optional<String>? = null,
    @field:Schema(description = "프로필 사진 imageSeq(본인이 확정한 이미지만). 생략하면 유지, null이면 사진 해제", nullable = true, example = "1301")
    val profileImageSeq: Optional<Long>? = null,
) {
    fun toCommand() = UpdateMyProfileCommand(nickname, name, birthDate, phone, profileImageSeq)
}

data class MyProfileResponse(
    @field:Schema(description = "회원 번호", example = "17")
    val userSeq: Int,
    @field:Schema(description = "역할. 이 API는 항상 USER(일반 회원)", example = "USER")
    val role: Role,
    @field:Schema(description = "닉네임", example = "반려가족")
    val nickname: String?,
    @field:Schema(description = "이름", example = "김가람")
    val name: String?,
    @field:Schema(description = "생년월일(YYYY-MM-DD)", example = "1997-05-12")
    val birthDate: LocalDate?,
    @field:Schema(description = "전화번호", example = "01012345678")
    val phone: String?,
    @field:Schema(description = "프로필 사진. 없으면 null", nullable = true)
    val profileImage: ImageSummary?,
    @field:Schema(description = "계정 상태. ACTIVE: 정상, SUSPENDED: 정지(조회만 가능, 쓰기 요청은 403)", example = "ACTIVE")
    val accountStatus: AccountStatus,
) {
    companion object {
        fun from(profile: MyProfile) = with(profile.user) {
            MyProfileResponse(userSeq!!, role, nickname, name, birthDate, phone, profile.profileImage, accountStatus)
        }
    }
}

data class PublicProfileResponse(
    @field:Schema(description = "회원 번호", example = "18")
    val userSeq: Long,
    @field:Schema(description = "표시 이름. USER면 닉네임, SHELTER면 보호소명", example = "발견도우미")
    val displayName: String?,
    @field:Schema(description = "역할. USER: 일반 회원, SHELTER: 보호소 담당자", example = "USER")
    val role: Role,
    @field:Schema(description = "프로필 사진. USER면 회원 사진, SHELTER면 보호소 프로필 사진. 없으면 null", nullable = true)
    val profileImage: OrderedImage?,
) {
    companion object {
        fun from(profile: PublicProfile) = PublicProfileResponse(profile.userSeq, profile.displayName, profile.role, profile.profileImage?.ordered())
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
/** 닉네임 중복 확인 결과 */
data class NicknameAvailabilityResponse(
    @field:Schema(description = "확인한 닉네임(요청값 그대로)", example = "반려가족")
    val nickname: String,
    @field:Schema(description = "true면 사용 가능. false면 다른 회원이 쓰는 중(대소문자 구분 없음)", example = "true")
    val available: Boolean,
)

abstract class MyProfileSuccessSchema : SuccessResponseSchema<MyProfileResponse>()
abstract class NicknameAvailabilitySuccessSchema : SuccessResponseSchema<NicknameAvailabilityResponse>()
abstract class PublicProfileSuccessSchema : SuccessResponseSchema<PublicProfileResponse>()
