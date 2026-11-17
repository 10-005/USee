package com.ssafy.animal.core.found.application.port.`in`

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.common.exception.CommonErrorCode
import com.ssafy.animal.common.response.ErrorDetail
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundReportContent
import com.ssafy.animal.core.found.domain.FoundStatus
import com.ssafy.animal.core.found.domain.Gender
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.OrderedImage
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional

interface FoundReportUseCase {
    /** 8.1 검색. 삭제·탈퇴 작성자 신고는 뺀다 */
    fun search(filter: FoundSearchFilter, pageable: Pageable): Page<FoundReportItem>

    /** 8.2 상세. 없음·삭제·탈퇴 작성자는 404 FOUND_REPORT_NOT_FOUND */
    fun get(viewer: AuthUser, foundReportSeq: Long): FoundReportDetail

    /** 8.3 내 신고 전량(regDttm desc 고정) */
    fun getMine(userSeq: Long): List<FoundReportItem>

    /** 8.4 등록 + 담당 보호소 자동 배정 */
    fun register(userSeq: Long, command: RegisterFoundReportCommand): RegisteredFoundReport

    /** 8.5 작성자 부분 수정. CLOSED면 409 FOUND_REPORT_CLOSED */
    fun update(viewer: AuthUser, foundReportSeq: Long, command: UpdateFoundReportCommand): FoundReportDetail

    /** 8.6 작성자는 CLOSED·ACTIVE, 담당 SHELTER는 CLOSED만 */
    fun changeStatus(viewer: AuthUser, foundReportSeq: Long, status: FoundStatus, closedReason: String?): FoundReport

    /** 8.7 작성자 논리 삭제 */
    fun delete(userSeq: Long, foundReportSeq: Long)

    /** 8.8 담당 보호소 목록 */
    fun getAssigned(shelterSeq: Long?, filter: AssignedFilter, pageable: Pageable): Page<AssignedFoundReportItem>
}

data class FoundSearchFilter(
    val status: FoundStatus? = null,
    val occurredFrom: LocalDate? = null,
    val occurredTo: LocalDate? = null,
    val districtCode: String? = null,
    val speciesSeq: Int? = null,
    val gender: Gender? = null,
    val isNeutered: Boolean? = null,
    val keyword: String? = null,
)

/** 8.8·10.1 기간 칩: 요청 시각 기준 최근 N일 */
enum class RecentPeriod(val days: Long) { RECENT_7D(7), RECENT_30D(30), RECENT_90D(90) }

/** 8.8 구조 상태: 신고의 최신 비삭제 구조 기록 기준. ABORTED = FAILED·CANCELED */
enum class RescueProgress { NONE, PROCESSING, DONE, ABORTED }

data class AssignedFilter(
    val status: FoundStatus? = null,
    val period: RecentPeriod? = null,
    val districtCode: String? = null,
    val speciesSeq: Int? = null,
    val rescueStatus: RescueProgress? = null,
)

data class RegisterFoundReportCommand(
    val content: FoundReportContent,
    val imageSeqs: List<Long>,
)

/**
 * 8.5 부분 수정. 필드별 null = 미전달(유지), Optional.empty() = 명시적 null, Optional.of(v) = 변경.
 * imageSeqs는 보내면 전체 최종 배열(Q424)
 */
data class UpdateFoundReportCommand(
    val combinationSeq: Optional<Int>? = null,
    val gender: Optional<Gender>? = null,
    val age: Optional<String>? = null,
    val weight: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val microchipNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val reporterPhone: Optional<String>? = null,
    val isPhonePublic: Optional<Boolean>? = null,
    val landmarkDescription: Optional<String>? = null,
    val address: Optional<String>? = null,
    val latitude: Optional<Double>? = null,
    val longitude: Optional<Double>? = null,
    val occurredDttm: Optional<OffsetDateTime>? = null,
    val imageSeqs: Optional<List<Long>>? = null,
) {
    /** 현재 값에 덮어쓴 결과. 필수 필드를 비우거나 길이·범위를 넘으면 400 COMMON_INVALID_INPUT */
    fun applyTo(c: FoundReportContent): FoundReportContent {
        val errors = mutableListOf<ErrorDetail>()
        fun <T : Any> required(name: String, value: Optional<T>?, current: T): T = when {
            value == null -> current
            value.isEmpty || (value.get() as? String)?.isBlank() == true -> current.also { errors += ErrorDetail(name, "필수 입력값입니다.") }
            else -> value.get()
        }
        fun <T : Any> optional(value: Optional<T>?, current: T?): T? = if (value == null) current else value.orElse(null)

        val result = FoundReportContent(
            combinationSeq = required("combinationSeq", combinationSeq, c.combinationSeq),
            gender = required("gender", gender, c.gender),
            age = optional(age, c.age),
            weight = optional(weight, c.weight),
            isNeutered = optional(isNeutered, c.isNeutered),
            microchipNumber = optional(microchipNumber, c.microchipNumber),
            description = optional(description, c.description),
            reporterPhone = required("reporterPhone", reporterPhone, c.reporterPhone),
            isPhonePublic = required("isPhonePublic", isPhonePublic, c.isPhonePublic),
            landmarkDescription = optional(landmarkDescription, c.landmarkDescription),
            address = required("address", address, c.address),
            latitude = required("latitude", latitude, c.latitude),
            longitude = required("longitude", longitude, c.longitude),
            occurredDttm = required("occurredDttm", occurredDttm, c.occurredDttm),
        )
        if (imageSeqs != null && imageSeqs.isEmpty) errors += ErrorDetail("imageSeqs", "필수 입력값입니다.")
        // 등록 요청 DTO의 Bean Validation과 같은 한도
        listOf("age" to result.age, "weight" to result.weight, "microchipNumber" to result.microchipNumber, "reporterPhone" to result.reporterPhone)
            .filter { (it.second?.length ?: 0) > 30 }.forEach { errors += ErrorDetail(it.first, "30자 이하입니다.") }
        if (result.address.length > 255) errors += ErrorDetail("address", "255자 이하입니다.")
        if (result.latitude !in -90.0..90.0) errors += ErrorDetail("latitude", "위도는 -90~90입니다.")
        if (result.longitude !in -180.0..180.0) errors += ErrorDetail("longitude", "경도는 -180~180입니다.")
        if (errors.isNotEmpty()) throw BusinessException(CommonErrorCode.COMMON_INVALID_INPUT, errors = errors)
        return result
    }
}

enum class AssignmentMethod { DISTRICT, NEAREST }

data class RegisteredFoundReport(
    val foundReportSeq: Int,
    val status: FoundStatus,
    val districtCode: String?,
    val assignedShelterSeq: Int,
    val assignmentMethod: AssignmentMethod,
)

/** 8.1·8.3 목록 항목 */
data class FoundReportItem(
    val report: FoundReport,
    val classification: AnimalClassification?,
    val representativeImageUrl: String?,
)

/** 1.4 ImageRef. 문서 스키마 이름은 animal 패키지 ImageRef와 겹치지 않게 따로 둔다 */
@Schema(name = "FoundImageRef", description = "발견 신고 사진")
data class ImageRef(
    @field:Schema(description = "이미지 번호", example = "1201")
    val imageSeq: Long,
    @field:Schema(description = "공개 고정 URL. 이미지 행이 없으면 null", nullable = true, example = "https://storage.example.com/images/1201.jpg")
    val imageUrl: String?,
    @field:Schema(description = "표시 순서. 0부터, 0이 대표 사진", example = "0")
    val displayOrder: Int,
)

/** 1.4 ActorSummary. 발견 신고 작성자는 USER뿐이라 표시 이름은 닉네임 */
data class ActorSummary(
    @field:Schema(description = "작성자 회원 번호", example = "17")
    val userSeq: Long,
    @field:Schema(description = "표시 이름(닉네임). 탈퇴한 회원이면 (탈퇴한 사용자)", nullable = true, example = "반려가족")
    val displayName: String?,
    @field:Schema(description = "역할. 발견 신고 작성자는 항상 USER", example = "USER")
    val role: Role,
    @field:Schema(description = "프로필 사진. 없거나 탈퇴한 회원이면 null", nullable = true)
    val profileImage: OrderedImage?,
)

data class FoundReportDetail(
    val item: FoundReportItem,
    val reporter: ActorSummary,
    val shelterName: String?,
    /** 공개 또는 작성자·담당 보호소일 때만 값, 아니면 null */
    val reporterPhone: String?,
    val images: List<ImageRef>,
    val isBookmarked: Boolean,
    val commentCount: Long,
)

/** 8.8·10.2 보호소 화면용 신고자: users.name 실명 + 신고 연락처(Q470) */
data class ReporterContact(
    @field:Schema(description = "신고자 실명(users.name). 탈퇴한 회원이면 (탈퇴한 사용자)", nullable = true, example = "김가람")
    val name: String?,
    @field:Schema(description = "신고에 적은 연락처. 공개 여부와 관계없이 담당 보호소에 준다", example = "01012345678")
    val phone: String,
)

data class AssignedFoundReportItem(
    val item: FoundReportItem,
    val reporter: ReporterContact,
    val rescueStatus: RescueProgress,
    val activeRescueSeq: Int?,
)
