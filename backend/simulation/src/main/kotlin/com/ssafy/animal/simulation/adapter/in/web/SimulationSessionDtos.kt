package com.ssafy.animal.simulation.adapter.`in`.web

import com.ssafy.animal.simulation.application.port.`in`.Equipment
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stats
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

/** 18.1 요청. 누락도 400 COMMON_INVALID_INPUT이 되도록 필드를 nullable로 받아 검증한다(한글 전용 규칙은 프론트, Q484) */
data class StartSimulationRequest(
    @field:NotNull
    val breedSeq: Int?,
    @field:NotBlank
    @field:Size(max = SimulationSession.ANIMAL_NAME_MAX_LENGTH)
    val animalName: String?,
    @field:NotNull
    @field:PositiveOrZero
    val expectedCost: Int?,
) {
    fun toCommand() = StartSimulationCommand(breedSeq!!, animalName!!, expectedCost!!)
}

/** 세션 상세(18.1·18.2·18.4) */
data class SimulationSessionResponse(
    val sessionSeq: Long,
    val userSeq: Int,
    val animalName: String,
    val breed: BreedResponse,
    val speciesType: SpeciesType,
    val personality: Personality,
    val status: SessionStatus,
    val expectedCost: Int,
    val balance: Int,
    val startedDttm: OffsetDateTime,
    val endsDttm: OffsetDateTime,
    val endedDttm: OffsetDateTime?,
    val stats: StatsResponse,
    val lastActionDttm: OffsetDateTime?,
    val equipment: Equipment,
    /** 18.12 SimulationEvent. 이벤트 작업 전까지는 항상 null */
    val pendingEvent: Any?,
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

/** 세션의 breed_seq로 core.animal_breeds를 JOIN한 파생 블록. speciesType은 품종의 종 코드 */
data class BreedResponse(
    val breedSeq: Int,
    val breedName: String,
    val speciesType: CatalogSpeciesCode,
    val representativeImageUrl: String?,
) {
    companion object {
        fun from(breed: BreedInfo) = BreedResponse(breed.breedSeq, breed.breedName, breed.speciesCode, breed.representativeImageUrl)
    }
}

/** 명세 18.0 SimulationStats. 7종 모두 높을수록 좋음(Q485) */
data class StatsResponse(
    val satiety: Int,
    val hydration: Int,
    val cleanliness: Int,
    val bond: Int,
    val calmness: Int,
    val health: Int,
    val energy: Int,
    val calculatedDttm: OffsetDateTime,
) {
    companion object {
        fun of(stats: Stats, calculatedDttm: OffsetDateTime) = StatsResponse(
            stats.satiety, stats.hydration, stats.cleanliness, stats.bond, stats.calmness, stats.health, stats.energy, calculatedDttm,
        )
    }
}
