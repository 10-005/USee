package com.ssafy.animal.core.user.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.user.application.port.`in`.MyProfile
import com.ssafy.animal.core.user.application.port.`in`.PublicProfile
import com.ssafy.animal.core.user.application.port.`in`.UpdateMyProfileCommand
import com.ssafy.animal.core.user.application.port.`in`.UserProfileUseCase
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserErrorCode
import com.ssafy.animal.core.user.domain.UserProfilePolicy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Service
class UserProfileService(
    private val userRepository: UserRepository,
    private val imageUseCase: ImageUseCase,
) : UserProfileUseCase {

    @Transactional(readOnly = true)
    override fun getMe(userSeq: Long) = myProfile(load(userSeq))

    @Transactional(readOnly = true)
    override fun isNicknameAvailable(nickname: String, exceptUserSeq: Long?): Boolean {
        if (!UserProfilePolicy.isValidNickname(nickname)) {
            throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = listOf(ErrorDetail("nickname", UserProfilePolicy.NICKNAME_RULE)))
        }
        return !userRepository.existsActiveNickname(nickname, exceptUserSeq?.let(Math::toIntExact))
    }

    @Transactional
    override fun updateMe(userSeq: Long, command: UpdateMyProfileCommand): MyProfile {
        val user = load(userSeq)
        val seq = user.userSeq!!

        // 3.2: 필수 4종을 null(공백 포함)로 바꾸는 요청은 400 COMMON_INVALID_INPUT
        val cleared = listOf("nickname" to command.nickname, "name" to command.name, "birthDate" to command.birthDate, "phone" to command.phone)
            .filter { (_, value) -> value != null && value.map { it.toString().isBlank() }.orElse(true) }
            .map { (field, _) -> ErrorDetail(field, "필수 입력값입니다.") }
        if (cleared.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = cleared)

        val nickname = command.nickname.orKeep(user.nickname)
        val name = command.name.orKeep(user.name)
        val birthDate = command.birthDate.orKeep(user.birthDate)
        val phone = command.phone.orKeep(user.phone)
        UserProfilePolicy.validate(nickname, name, birthDate, phone)

        // 본인은 빼고 본다: 같은 닉네임·전화를 그대로 다시 보내도 409가 나지 않게
        if (command.nickname != null && userRepository.existsActiveNickname(nickname!!, seq)) {
            throw BusinessException(UserErrorCode.USER_DUPLICATE_NICKNAME)
        }
        if (command.phone != null && userRepository.existsActivePhone(phone!!, seq)) {
            throw BusinessException(UserErrorCode.USER_DUPLICATE_PHONE)
        }

        val oldImageSeq = user.profileImageSeq
        val newImageSeq = command.profileImageSeq.orKeep(oldImageSeq)
        if (newImageSeq != null && newImageSeq != oldImageSeq) imageUseCase.requireOwned(userSeq, newImageSeq)

        user.updateProfile(nickname, name, birthDate, phone, newImageSeq)
        userRepository.saveAndFlush(user)

        // 참조를 잃은 이전 이미지는 다른 참조가 없을 때만 삭제(Q383). flush 뒤라 이 회원의 참조는 이미 빠져 있다
        if (oldImageSeq != null && oldImageSeq != newImageSeq) imageUseCase.deleteIfUnreferenced(oldImageSeq)
        return myProfile(user)
    }

    @Transactional(readOnly = true)
    override fun getPublicProfile(userSeq: Long): PublicProfile {
        val user = userRepository.findById(Math.toIntExact(userSeq))?.takeUnless { it.isDeleted } ?: throw notFound()
        return when (user.role) {
            Role.USER -> PublicProfile(userSeq, user.nickname, user.role, user.profileImageSeq?.let(imageUseCase::findSummary))
            // SHELTER 표시 이름은 보호소명, 사진은 보호소 프로필(명세 1.4 ActorSummary)
            Role.SHELTER -> {
                val shelter = userRepository.findShelterDisplay(user.userSeq!!) ?: throw notFound()
                PublicProfile(userSeq, shelter.shelterName, user.role, shelter.profileImageSeq?.let(imageUseCase::findSummary))
            }
        }
    }

    private fun load(userSeq: Long): User = userRepository.findById(Math.toIntExact(userSeq)) ?: throw notFound()

    private fun notFound() = BusinessException(UserErrorCode.USER_NOT_FOUND)

    private fun myProfile(user: User) = MyProfile(user, user.profileImageSeq?.let(imageUseCase::findSummary))

    private fun <T : Any> Optional<T>?.orKeep(current: T?): T? = if (this == null) current else orElse(null)
}
