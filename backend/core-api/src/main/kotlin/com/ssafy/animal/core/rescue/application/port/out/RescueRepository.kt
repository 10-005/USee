package com.ssafy.animal.core.rescue.application.port.out

import com.ssafy.animal.core.rescue.application.port.`in`.RescueFilter
import com.ssafy.animal.core.rescue.domain.RescueRecord
import com.ssafy.animal.core.rescue.domain.RescueStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

/** 구조 기록 저장소. 발견 신고·보호동물·회원 테이블은 엔티티 없이 네이티브로 읽는다 */
interface RescueRepository {
    /** 바로 INSERT·UPDATE한다. 발견 신고당 PROCESSING/DONE 1건 부분 UNIQUE에 걸리면 409 RESCUE_ACTIVE_ALREADY_EXISTS */
    fun saveAndFlush(rescue: RescueRecord): RescueRecord

    /** 삭제되지 않은 구조 기록 */
    fun findActive(rescueSeq: Int): RescueRecord?

    /** 10.1 정렬 화이트리스트 regDttm·completedDttm(기본 regDttm desc) */
    fun findPage(shelterSeq: Int, filter: RescueFilter, pageable: Pageable): Page<RescueRecord>

    /** filter.status는 무시하고 상태별로 센다 */
    fun countByStatus(shelterSeq: Int, filter: RescueFilter): Map<RescueStatus, Long>

    fun existsActiveFoundReport(foundReportSeq: Int): Boolean

    /** 비삭제 PROCESSING/DONE 구조가 있는지 */
    fun existsOngoingRescue(foundReportSeq: Int): Boolean

    /** 비삭제 보호동물 수 */
    fun countLinkedAnimals(rescueSeq: Int): Long

    /** 발견 신고의 동물 정보(삭제된 신고 포함) */
    fun findFoundReportAnimals(foundReportSeqs: Collection<Int>): Map<Int, AnimalRow>

    /** 구조별 가장 먼저 등록된 비삭제 보호동물 */
    fun findFirstShelterAnimals(rescueSeqs: Collection<Int>): Map<Int, AnimalRow>

    /** 10.2 연결 발견 신고 요약(삭제된 신고 포함) */
    fun findFoundReportSummary(foundReportSeq: Int): FoundReportRow?
}

data class AnimalRow(
    val combinationSeq: Int,
    val gender: String,
    val age: String?,
    val isNeutered: Boolean?,
    val weight: String?,
    val imageSeq: Long?,
)

data class FoundReportRow(
    val foundReportSeq: Int,
    val animal: AnimalRow,
    val address: String,
    val occurredDttm: OffsetDateTime,
    val reporterName: String?,
    val reporterDeleted: Boolean,
    val reporterPhone: String,
    val status: String,
)
