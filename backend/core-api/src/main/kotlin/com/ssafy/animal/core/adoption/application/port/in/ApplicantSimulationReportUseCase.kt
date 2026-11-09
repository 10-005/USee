package com.ssafy.animal.core.adoption.application.port.`in`

import com.ssafy.animal.common.auth.AuthUser
import com.ssafy.animal.core.catalog.domain.SpeciesCode
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

interface ApplicantSimulationReportUseCase {
    /**
     * 12.15(= 18.21) 입양 신청자의 모의 입양 종료 리포트 전체(Q483). 담당 SHELTER 전용.
     * 소속 보호소의 제출된 신청서가 아니면(타 보호소·DRAFT·CANCELED) 404 ADOPTION_APPLICATION_NOT_FOUND.
     * 신청자의 끝난 세션 리포트를 동물·품종과 상관없이 최근 종료 순으로 준다. 탈퇴한 신청자의 리포트도 준다.
     */
    fun getApplicantReports(shelter: AuthUser, applicationSeq: Long): List<ApplicantSimulationReport>
}

/** 목록 한 줄: 동물 정보·기간·총지출(사용자 결정 Q502). 상세는 simulation 18.18 */
@Schema(name = "ApplicantSimulationReport", description = "신청자의 모의 입양 종료 리포트(목록 항목)")
data class ApplicantSimulationReport(
    @field:Schema(description = "모의 입양 세션 번호. 상세는 모의 입양 리포트 조회(18.18)로 본다", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = "신청자가 붙인 가상 이름", example = "초코")
    val animalName: String,
    @field:Schema(description = "품종")
    val breed: SimulationBreed,
    @field:Schema(description = "시작 시각", example = "2026-11-01T14:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "종료 시각(7일 완료·포기·돌봄 부족 중단)", example = "2026-11-08T14:00:00+09:00")
    val endedDttm: OffsetDateTime,
    @field:Schema(description = "총지출(원) = 펫샵 구매 + 병원 진료비", example = "182000")
    val totalSpent: Int,
)

@Schema(name = "ApplicantSimulationBreed", description = "모의 입양 품종")
data class SimulationBreed(
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "종. DOG: 개, CAT: 고양이", example = "DOG")
    val speciesType: SpeciesCode,
    @field:Schema(description = "품종 대표 이미지 URL. 없으면 null", nullable = true, example = "https://cdn.example.com/breeds/21.jpg")
    val representativeImageUrl: String?,
)
