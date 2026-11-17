package com.ssafy.animal.core.user.application.service

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.exception.ErrorCode
import com.ssafy.animal.core.image.application.port.`in`.ConfirmedImage
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.ImageUseCase
import com.ssafy.animal.core.image.application.port.`in`.IssueUploadUrlCommand
import com.ssafy.animal.core.image.application.port.`in`.UploadUrl
import com.ssafy.animal.core.image.domain.ImageErrorCode
import com.ssafy.animal.core.user.application.port.`in`.UpdateMyProfileCommand
import com.ssafy.animal.core.user.application.port.out.ShelterDisplay
import com.ssafy.animal.core.user.application.port.out.UserRepository
import com.ssafy.animal.core.user.domain.User
import com.ssafy.animal.core.user.domain.UserErrorCode
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDate
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProfileServiceTest {

    private val users = mutableMapOf<Int, User>()
    private val shelters = mutableMapOf<Int, ShelterDisplay>()

    /** 이미지 소유자(imageSeq → regUsrSeq). 삭제 판단 자체는 ImageService가 하므로 여기서는 호출만 기록한다 */
    private val imageOwners = mutableMapOf<Long, Int?>()
    private val releaseCalls = mutableListOf<Long>()
    private var flushed = 0

    private val userRepository = object : UserRepository {
        override fun findById(userSeq: Int) = users[userSeq]
        override fun findActiveAccount(providerCode: String, loginIdentifier: String) = error("not used")
        override fun findActiveAccountByUserSeq(userSeq: Int) = error("not used")
        override fun existsActiveNickname(nickname: String, exceptUserSeq: Int?) =
            activeUsers(exceptUserSeq).any { it.nickname.equals(nickname, ignoreCase = true) }
        override fun existsActivePhone(phone: String, exceptUserSeq: Int?) = activeUsers(exceptUserSeq).any { it.phone == phone }
        override fun saveAndFlush(user: User) = user.also { flushed++ }
        override fun findShelterDisplay(userSeq: Int) = shelters[userSeq]
        override fun saveWithAccount(user: User, providerCode: String, loginIdentifier: String) = error("not used")

        private fun activeUsers(exceptUserSeq: Int?) =
            users.values.filter { it.role == Role.USER && !it.isDeleted && it.userSeq != exceptUserSeq }
    }

    private val imageUseCase = object : ImageUseCase {
        override fun requireOwned(userSeq: Long, imageSeq: Long): ImageSummary {
            if (imageSeq !in imageOwners) throw BusinessException(ImageErrorCode.IMAGE_NOT_FOUND)
            if (imageOwners[imageSeq]?.toLong() != userSeq) throw BusinessException(ImageErrorCode.IMAGE_NOT_OWNED)
            return summary(imageSeq)
        }
        override fun findSummary(imageSeq: Long) = summary(imageSeq).takeIf { imageSeq in imageOwners }
        override fun deleteIfUnreferenced(imageSeq: Long) {
            releaseCalls += imageSeq
        }
        override fun issueUploadUrl(command: IssueUploadUrlCommand): UploadUrl = error("not used")
        override fun confirm(userSeq: Long, imageKey: String): ConfirmedImage = error("not used")
        private fun summary(imageSeq: Long) = ImageSummary(imageSeq, "https://cdn/$imageSeq.jpg")
    }

    private val service = UserProfileService(userRepository, imageUseCase)

    private val me = user(1, "반려가족", "01011112222")
    private val other = user(2, "발견도우미", "01033334444")

    private fun user(seq: Int, nickname: String?, phone: String?, role: Role = Role.USER): User {
        val birthDate = if (role == Role.USER) LocalDate.of(1997, 5, 12) else null
        val name = if (role == Role.USER) "김가람" else null
        return User(role, nickname, name, birthDate, phone).also {
            ReflectionTestUtils.setField(it, "userSeq", seq)
            users[seq] = it
        }
    }

    // 3.1

    @Test
    fun `내 프로필은 이미지가 없으면 profileImage null, 있으면 imageSeq와 URL`() {
        assertNull(service.getMe(1).profileImage)

        imageOwners[10] = 1
        me.updateProfile(me.nickname, me.name, me.birthDate, me.phone, 10)
        assertEquals(ImageSummary(10, "https://cdn/10.jpg"), service.getMe(1).profileImage)
    }

    // 3.2

    @Test
    fun `보낸 필드만 바꾸고 나머지는 유지한다`() {
        val result = service.updateMe(1, UpdateMyProfileCommand(name = Optional.of("김바람")))

        assertEquals("김바람", result.user.name)
        assertEquals("반려가족", me.nickname)
        assertEquals("01011112222", me.phone)
        assertEquals(LocalDate.of(1997, 5, 12), me.birthDate)
        assertEquals(1, flushed)
    }

    @Test
    fun `자기 닉네임·전화를 그대로(대소문자만 바꿔도) 보내면 409가 아니다`() {
        val mine = user(3, "Happy", "01055556666")
        service.updateMe(3, UpdateMyProfileCommand(nickname = Optional.of("happy"), phone = Optional.of("01055556666")))
        assertEquals("happy", mine.nickname)
    }

    @Test
    fun `다른 활성 회원의 닉네임·전화는 409, 탈퇴 회원 것은 허용`() {
        assertCode(UserErrorCode.USER_DUPLICATE_NICKNAME) { service.updateMe(1, UpdateMyProfileCommand(nickname = Optional.of("발견도우미"))) }
        assertCode(UserErrorCode.USER_DUPLICATE_PHONE) { service.updateMe(1, UpdateMyProfileCommand(phone = Optional.of("01033334444"))) }

        ReflectionTestUtils.setField(other, "isDeleted", true)
        service.updateMe(1, UpdateMyProfileCommand(nickname = Optional.of("발견도우미")))
        assertEquals("발견도우미", me.nickname)
    }

    @Test
    fun `필수 4종을 null이나 공백으로 보내면 400 COMMON_INVALID_INPUT이고 바뀌지 않는다`() {
        val e = assertFailsWith<BusinessException> {
            service.updateMe(1, UpdateMyProfileCommand(nickname = Optional.empty(), birthDate = Optional.empty(), phone = Optional.of(" ")))
        }
        assertEquals(CommonErrorCode.COMMON_INVALID_INPUT, e.errorCode)
        assertEquals(listOf("nickname", "birthDate", "phone"), e.errors.map { it.field })
        assertEquals("반려가족", me.nickname)
        assertEquals(0, flushed)
    }

    // 닉네임 중복 확인(명세 외)

    @Test
    fun `닉네임 중복 확인은 다른 활성 USER가 쓰면 false, 본인·탈퇴 회원 것은 true, 대소문자 무시`() {
        assertEquals(false, service.isNicknameAvailable("발견도우미", null))
        assertEquals(true, service.isNicknameAvailable("새닉네임", null))
        assertEquals(false, service.isNicknameAvailable("반려가족", null)) // 비로그인은 본인 제외 없음
        assertEquals(true, service.isNicknameAvailable("반려가족", 1)) // 본인 현재 닉네임
        user(3, "Bori", "01055556666")
        assertEquals(false, service.isNicknameAvailable("bori", null))
        ReflectionTestUtils.setField(other, "isDeleted", true)
        assertEquals(true, service.isNicknameAvailable("발견도우미", null))
    }

    @Test
    fun `닉네임 중복 확인도 형식이 틀리거나 비어 있으면 400 COMMON_INVALID_INPUT`() {
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.isNicknameAvailable("a", null) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.isNicknameAvailable("", null) }
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.isNicknameAvailable("공백 있음", null) }
    }

    @Test
    fun `닉네임 형식 오류는 400 COMMON_INVALID_INPUT`() {
        assertCode(CommonErrorCode.COMMON_INVALID_INPUT) { service.updateMe(1, UpdateMyProfileCommand(nickname = Optional.of("a"))) }
    }

    @Test
    fun `남의 이미지는 403, 없는 이미지는 404이고 바뀌지 않는다`() {
        imageOwners[20] = 2
        assertCode(ImageErrorCode.IMAGE_NOT_OWNED) { service.updateMe(1, UpdateMyProfileCommand(profileImageSeq = Optional.of(20))) }
        assertCode(ImageErrorCode.IMAGE_NOT_FOUND) { service.updateMe(1, UpdateMyProfileCommand(profileImageSeq = Optional.of(99))) }
        assertNull(me.profileImageSeq)
        assertEquals(0, flushed)
    }

    @Test
    fun `이미지 교체·해제 시 이전 이미지를 정리 대상으로 넘기고, 유지·같은 값이면 넘기지 않는다`() {
        imageOwners[10] = 1
        imageOwners[11] = 1
        service.updateMe(1, UpdateMyProfileCommand(profileImageSeq = Optional.of(10)))
        assertTrue(releaseCalls.isEmpty()) // 이전 이미지 없음

        service.updateMe(1, UpdateMyProfileCommand(name = Optional.of("김바람"))) // 미전달 = 유지
        service.updateMe(1, UpdateMyProfileCommand(profileImageSeq = Optional.of(10))) // 같은 값
        assertTrue(releaseCalls.isEmpty())

        val replaced = service.updateMe(1, UpdateMyProfileCommand(profileImageSeq = Optional.of(11)))
        assertEquals(ImageSummary(11, "https://cdn/11.jpg"), replaced.profileImage)
        assertEquals(listOf(10L), releaseCalls)

        val cleared = service.updateMe(1, UpdateMyProfileCommand(profileImageSeq = Optional.empty()))
        assertNull(cleared.profileImage)
        assertNull(me.profileImageSeq)
        assertEquals(listOf(10L, 11L), releaseCalls)
    }

    // 3.5

    @Test
    fun `USER 공개 프로필은 닉네임을 displayName으로 준다`() {
        val profile = service.getPublicProfile(2)
        assertEquals("발견도우미", profile.displayName)
        assertEquals(Role.USER, profile.role)
        assertNull(profile.profileImage)
    }

    @Test
    fun `SHELTER 공개 프로필은 보호소명과 보호소 사진, 보호소가 없으면 404`() {
        user(5, null, null, Role.SHELTER)
        imageOwners[30] = 5
        shelters[5] = object : ShelterDisplay {
            override val shelterName = "구미시 동물보호센터"
            override val profileImageSeq = 30L
        }
        val profile = service.getPublicProfile(5)
        assertEquals("구미시 동물보호센터", profile.displayName)
        assertEquals(Role.SHELTER, profile.role)
        assertEquals(30L, profile.profileImage?.imageSeq)

        user(6, null, null, Role.SHELTER)
        assertCode(UserErrorCode.USER_NOT_FOUND) { service.getPublicProfile(6) }
    }

    @Test
    fun `탈퇴 회원과 없는 회원은 404 USER_NOT_FOUND`() {
        ReflectionTestUtils.setField(other, "isDeleted", true)
        assertCode(UserErrorCode.USER_NOT_FOUND) { service.getPublicProfile(2) }
        assertCode(UserErrorCode.USER_NOT_FOUND) { service.getPublicProfile(999) }
    }

    private fun assertCode(code: ErrorCode, block: () -> Unit) =
        assertEquals(code, assertFailsWith<BusinessException> { block() }.errorCode)
}
