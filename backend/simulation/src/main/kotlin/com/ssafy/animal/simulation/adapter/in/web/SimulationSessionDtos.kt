package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.common.docs.SuccessResponseSchema
import com.ssafy.animal.common.response.PageResponse
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionSummary
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stats
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

internal const val SPECIES_DESC = "세션 종(품종에서 고정). DOG: 개, CAT: 고양이"
internal const val PERSONALITY_DESC = "성격(품종에서 고정). ACTIVE: 활동적, TIMID: 소심함, AGGRESSIVE: 공격적, FRIENDLY: 사람 친화적"
internal const val SESSION_STATUS_DESC = "세션 상태. IN_PROGRESS: 진행 중, COMPLETED: 7일 완료, ABANDONED: 포기, FAILED: 돌봄 부족(건강 0)"

/** 18.1 요청. 누락도 400 COMMON_INVALID_INPUT이 되도록 필드를 nullable로 받아 검증한다(한글 전용 규칙은 프론트, Q484) */
data class StartSimulationRequest(
    @field:Schema(description = "품종 번호(품종 목록 API 값). 종이 기타(ETC)면 409", example = "21")
    @field:NotNull
    val breedSeq: Int?,
    @field:Schema(description = "가상 이름. 1~6자(한글만 허용은 앱에서 검사)", example = "초코")
    @field:NotBlank
    @field:Size(max = SimulationSession.ANIMAL_NAME_MAX_LENGTH)
    val animalName: String?,
    @field:Schema(description = "일주일 예상 비용(원). 0 이상 정수. 시작 잔액이 된다", example = "150000")
    @field:NotNull
    @field:PositiveOrZero
    val expectedCost: Int?,
) {
    fun toCommand() = StartSimulationCommand(breedSeq!!, animalName!!, expectedCost!!)
}

/** 세션 상세(18.1·18.2·18.4) */
data class SimulationSessionResponse(
    @field:Schema(description = "세션 번호", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = "체험 회원 번호", example = "17")
    val userSeq: Int,
    @field:Schema(description = "가상 이름", example = "초코")
    val animalName: String,
    @field:Schema(description = "품종(조회 시 품종 기준정보에서 읽음)")
    val breed: BreedResponse,
    @field:Schema(description = SPECIES_DESC, example = "DOG")
    val speciesType: SpeciesType,
    @field:Schema(description = PERSONALITY_DESC, example = "FRIENDLY")
    val personality: Personality,
    @field:Schema(description = SESSION_STATUS_DESC, example = "IN_PROGRESS")
    val status: SessionStatus,
    @field:Schema(description = "시작 시 입력한 일주일 예상 비용(원)", example = "150000")
    val expectedCost: Int,
    @field:Schema(description = "현재 잔액(원). 음수가 될 수 있다", example = "118000")
    val balance: Int,
    @field:Schema(description = "시작 시각", example = "2026-11-01T14:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "예정 종료 시각(시작 + 7일)", example = "2026-11-08T14:00:00+09:00")
    val endsDttm: OffsetDateTime,
    @field:Schema(description = "실제 종료 시각. 진행 중이면 null", nullable = true, example = "null")
    val endedDttm: OffsetDateTime?,
    @field:Schema(description = "현재 스탯(시간 경과 반영 후)")
    val stats: StatsResponse,
    @field:Schema(description = "마지막 돌봄·산책 행동 시각. 아직 없으면 null", nullable = true, example = "2026-11-01T16:40:00+09:00")
    val lastActionDttm: OffsetDateTime?,
    @field:Schema(description = "보유 기구(메인 화면 오브젝트 활성 여부)")
    val equipment: Equipment,
    @field:Schema(description = "미해결 돌발 이벤트. 이벤트 기능 전까지 항상 null", nullable = true, example = "null")
    val pendingEvent: Any?,
    @field:Schema(description = "진행 중 산책 번호. 없으면 null", nullable = true, example = "null")
    val activeWalkSeq: Long?,
) {
    companion object {
        fun from(detail: SimulationSessionDetail) = with(detail.session) {
            SimulationSessionResponse(
                sessionSeq = checkNotNull(sessionSeq),
                userSeq = userSeq,
                animalName = animalName,
                breed = BreedResponse.from(detail.breed),
                speciesType = speciesType,
                personality = personality,
                status = status,
                expectedCost = expectedCost,
                balance = balance,
                startedDttm = startedDttm,
                endsDttm = endsDttm,
                endedDttm = endedDttm,
                stats = StatsResponse.of(stats(), statsCalculatedDttm),
                lastActionDttm = lastActionDttm,
                equipment = detail.equipment,
                pendingEvent = null,
                activeWalkSeq = detail.activeWalkSeq,
            )
        }
    }
}

/** 18.3 목록 행 */
data class SimulationSessionSummaryResponse(
    @field:Schema(description = "세션 번호", example = "3001")
    val sessionSeq: Long,
    @field:Schema(description = "가상 이름", example = "초코")
    val animalName: String,
    @field:Schema(description = "품종")
    val breed: BreedResponse,
    @field:Schema(description = SPECIES_DESC, example = "DOG")
    val speciesType: SpeciesType,
    @field:Schema(description = PERSONALITY_DESC, example = "FRIENDLY")
    val personality: Personality,
    @field:Schema(description = SESSION_STATUS_DESC, example = "COMPLETED")
    val status: SessionStatus,
    @field:Schema(description = "일주일 예상 비용(원)", example = "150000")
    val expectedCost: Int,
    @field:Schema(description = "잔액(원). 음수가 될 수 있다", example = "-32000")
    val balance: Int,
    @field:Schema(description = "시작 시각", example = "2026-11-01T14:00:00+09:00")
    val startedDttm: OffsetDateTime,
    @field:Schema(description = "예정 종료 시각", example = "2026-11-08T14:00:00+09:00")
    val endsDttm: OffsetDateTime,
    @field:Schema(description = "실제 종료 시각. 진행 중이면 null", nullable = true, example = "2026-11-08T14:00:00+09:00")
    val endedDttm: OffsetDateTime?,
    @field:Schema(description = "종료 리포트가 있는지", example = "true")
    val hasReport: Boolean,
) {
    companion object {
        fun from(summary: SimulationSessionSummary) = with(summary.session) {
            SimulationSessionSummaryResponse(
                sessionSeq = checkNotNull(sessionSeq),
                animalName = animalName,
                breed = BreedResponse.from(summary.breed),
                speciesType = speciesType,
                personality = personality,
                status = status,
                expectedCost = expectedCost,
                balance = balance,
                startedDttm = startedDttm,
                endsDttm = endsDttm,
                endedDttm = endedDttm,
                hasReport = summary.hasReport,
            )
        }
    }
}

/** 세션의 breed_seq로 core.animal_breeds를 JOIN한 파생 블록. speciesType은 품종의 종 코드 */
data class BreedResponse(
    @field:Schema(description = "품종 번호", example = "21")
    val breedSeq: Int,
    @field:Schema(description = "품종 이름", example = "말티즈")
    val breedName: String,
    @field:Schema(description = "품종의 종 코드. DOG·CAT·ETC", example = "DOG")
    val speciesType: CatalogSpeciesCode,
    @field:Schema(description = "품종 대표 이미지 URL. 없으면 null", nullable = true, example = "https://storage.example.com/simulation/breeds/maltese.png")
    val representativeImageUrl: String?,
) {
    companion object {
        fun from(breed: BreedInfo) = BreedResponse(breed.breedSeq, breed.breedName, breed.speciesCode, breed.representativeImageUrl)
    }
}

/** 명세 18.0 SimulationStats. 7종 모두 0~100, 높을수록 좋음(Q485) */
data class StatsResponse(
    @field:Schema(description = "포만감(100=배부름)", example = "64")
    val satiety: Int,
    @field:Schema(description = "수분(100=수분 충분)", example = "58")
    val hydration: Int,
    @field:Schema(description = "청결도(100=깨끗함)", example = "77")
    val cleanliness: Int,
    @field:Schema(description = "친밀도(100=매우 친밀함)", example = "31")
    val bond: Int,
    @field:Schema(description = "안정감(100=편안함)", example = "46")
    val calmness: Int,
    @field:Schema(description = "건강(100=매우 건강함). 0이면 돌봄 부족", example = "90")
    val health: Int,
    @field:Schema(description = "활력(100=활기참)", example = "55")
    val energy: Int,
    @field:Schema(description = "스탯을 마지막으로 계산한 시각(1시간 단위 경계)", example = "2026-11-01T17:00:00+09:00")
    val calculatedDttm: OffsetDateTime,
) {
    companion object {
        fun of(stats: Stats, calculatedDttm: OffsetDateTime) = StatsResponse(
            stats.satiety, stats.hydration, stats.cleanliness, stats.bond, stats.calmness, stats.health, stats.energy, calculatedDttm,
        )
    }
}

// Swagger 문서 전용 성공 응답 미러(런타임 미사용)
abstract class SimulationSessionSuccessSchema : SuccessResponseSchema<SimulationSessionResponse>()
abstract class SimulationSessionSummaryPageSuccessSchema : SuccessResponseSchema<PageResponse<SimulationSessionSummaryResponse>>()
