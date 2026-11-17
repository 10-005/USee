package com.ssafy.animal.core.found.application.port.out

import com.ssafy.animal.core.found.application.port.`in`.AssignedFilter
import com.ssafy.animal.core.found.application.port.`in`.FoundSearchFilter
import com.ssafy.animal.core.found.domain.FoundReport
import com.ssafy.animal.core.found.domain.FoundReportImage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/** 발견 신고 저장소. 다른 도메인 테이블(보호소·회원·이미지·구조·댓글·북마크)은 엔티티 없이 네이티브로 읽는다 */
interface FoundReportRepository {
    /** IDENTITY라 바로 INSERT해 foundReportSeq를 채운다 */
    fun saveAndFlush(report: FoundReport): FoundReport

    /** 삭제되지 않은 신고 */
    fun findActive(foundReportSeq: Int): FoundReport?

    /** 일반 노출 대상: 삭제되지 않았고 작성자도 탈퇴하지 않은 신고 */
    fun findVisible(foundReportSeq: Int): FoundReport?

    fun search(filter: FoundSearchFilter, pageable: Pageable): Page<FoundReport>

    fun findMine(userSeq: Int): List<FoundReport>

    fun findAssigned(shelterSeq: Int, filter: AssignedFilter, pageable: Pageable): Page<FoundReport>

    /** 신고별 사진(display_order 순) */
    fun findImages(foundReportSeqs: Collection<Int>): Map<Int, List<FoundReportImage>>

    /** 사진 연결을 최종 배열로 바꾸고 flush한다(UNIQUE(found_report_seq, display_order) 때문에 지우고 다시 넣는다) */
    fun replaceImages(foundReportSeq: Int, imageSeqs: List<Long>)

    /** AI 매칭 이벤트용 S3 키(입력 순서 유지) */
    fun findImageKeys(imageSeqs: List<Long>): List<String>

    /** shelter_district_assignments에 지정된 비삭제 보호소 */
    fun findDistrictShelterSeq(districtCode: String): Int?

    /** 하버사인 최근접 비삭제 보호소. 보호소가 없으면 null */
    fun findNearestShelterSeq(latitude: Double, longitude: Double): Int?

    /** 보호소 담당 SHELTER 계정 */
    fun findShelterUserSeq(shelterSeq: Int): Int?

    fun findShelterName(shelterSeq: Int): String?

    fun findReporters(userSeqs: Collection<Int>): Map<Int, ReporterRow>

    fun isBookmarked(userSeq: Int, foundReportSeq: Int): Boolean

    /** 삭제되지 않은 댓글·답글 수 */
    fun countComments(foundReportSeq: Int): Long

    /** 신고별 최신 비삭제 구조 기록 */
    fun findLatestRescues(foundReportSeqs: Collection<Int>): Map<Int, RescueRow>
}

data class ReporterRow(
    val userSeq: Int,
    val nickname: String?,
    val name: String?,
    val profileImageSeq: Long?,
    val isDeleted: Boolean,
)

data class RescueRow(val rescueSeq: Int, val status: String)
