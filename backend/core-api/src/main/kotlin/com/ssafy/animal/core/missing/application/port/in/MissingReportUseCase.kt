package com.ssafy.animal.core.missing.application.port.`in`

import com.ssafy.animal.common.auth.Role
import com.ssafy.animal.core.catalog.application.port.`in`.AnimalClassification
import com.ssafy.animal.core.image.application.port.`in`.ImageSummary
import com.ssafy.animal.core.image.application.port.`in`.OrderedImage
import com.ssafy.animal.core.missing.domain.MissingGender
import com.ssafy.animal.core.missing.domain.MissingReport
import com.ssafy.animal.core.missing.domain.MissingStatus
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Optional

/** 7장 실종 신고. 없음·삭제·비노출·타인 신고는 404 MISSING_REPORT_NOT_FOUND(Q434) */
interface MissingReportUseCase {
    /** 7.1 */
    fun search(filter: MissingSearchFilter, pageable: Pageable): Page<MissingSummary>

    /** 7.2. viewer는 연락처 가시성·북마크 판단용 */
    fun get(viewerUserSeq: Long, missingReportSeq: Long): MissingDetail

    /** 7.3 비삭제 전량, regDttm desc */
    fun getMine(userSeq: Long): List<MissingSummary>

    /** 7.4 */
    fun create(userSeq: Long, command: CreateMissingCommand): CreatedMissing

    /** 7.5. 결과는 7.2 상세 */
    fun update(userSeq: Long, missingReportSeq: Long, command: UpdateMissingCommand): MissingDetail

    /** 7.6 */
    fun changeStatus(userSeq: Long, missingReportSeq: Long, status: MissingStatus): MissingReport

    /** 7.7 */
    fun delete(userSeq: Long, missingReportSeq: Long)
}

/** 7.1 필터. 모두 생략 가능(Q463) */
data class MissingSearchFilter(
    val status: MissingStatus? = null,
    val occurredFrom: LocalDate? = null,
    val occurredTo: LocalDate? = null,
    val districtCode: String? = null,
    val speciesSeq: Int? = null,
    val gender: MissingGender? = null,
    val isNeutered: Boolean? = null,
    val keyword: String? = null,
)

data class CreateMissingCommand(
    val combinationSeq: Int,
    val gender: MissingGender,
    val age: String? = null,
    val weight: String? = null,
    val isNeutered: Boolean? = null,
    val microchipNumber: String? = null,
    val description: String? = null,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val occurredDttm: OffsetDateTime,
    val contactPhone: String,
    val isPhonePublic: Boolean = false,
    val imageSeqs: List<Long>,
)

/** null = 미전달(유지). 선택 필드는 Optional.empty()가 명시적 null. 좌표는 latitude·longitude를 함께 보낸다 */
data class UpdateMissingCommand(
    val combinationSeq: Int? = null,
    val gender: MissingGender? = null,
    val age: Optional<String>? = null,
    val weight: Optional<String>? = null,
    val isNeutered: Optional<Boolean>? = null,
    val microchipNumber: Optional<String>? = null,
    val description: Optional<String>? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val occurredDttm: OffsetDateTime? = null,
    val contactPhone: String? = null,
    val isPhonePublic: Boolean? = null,
    val imageSeqs: List<Long>? = null,
)

data class CreatedMissing(val missingReportSeq: Int, val status: MissingStatus, val districtCode: String?, val aiMatchingRequested: Boolean)

data class MissingSummary(val report: MissingReport, val classification: AnimalClassification?, val representativeImageUrl: String?)

data class MissingDetail(
    val report: MissingReport,
    val classification: AnimalClassification?,
    val images: List<MissingImageRef>,
    val author: MissingAuthor?,
    /** 공개이거나 작성자 본인일 때만 값, 아니면 null */
    val contactPhone: String?,
    val isBookmarked: Boolean,
    val commentCount: Long,
)

/** 명세 1.4 ImageRef */
data class MissingImageRef(
    @field:Schema(description = "이미지 번호", example = "1001")
    val imageSeq: Long,
    @field:Schema(description = "공개 고정 URL. 이미지 행이 없으면 null", nullable = true, example = "https://storage.example.com/images/1001.jpg")
    val imageUrl: String?,
    @field:Schema(description = "표시 순서. 1부터, 1이 대표 사진", example = "1")
    val displayOrder: Int,
)

/** 명세 1.4 ActorSummary */
data class MissingAuthor(
    @field:Schema(description = "작성자 회원 번호", example = "17")
    val userSeq: Long,
    @field:Schema(description = "표시 이름(닉네임)", nullable = true, example = "반려가족")
    val displayName: String?,
    @field:Schema(description = "역할. 실종 신고 작성자는 USER", example = "USER")
    val role: Role,
    @field:Schema(description = "프로필 사진. 없으면 null", nullable = true)
    val profileImage: OrderedImage?,
)
