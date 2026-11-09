package com.ssafy.animal.simulation.application.port.out

import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType

interface SimulationSessionRepository {
    /** 요청자의 진행 중 세션을 행 잠금(FOR UPDATE)으로 조회한다. 없으면 null */
    fun findActiveForUpdate(userSeq: Int): SimulationSession?

    /**
     * 세션을 저장한다. 사전 조회를 지나 부분 UNIQUE(USER당 진행 중 1개)에 걸리면(경쟁 상태)
     * 사전 조회와 같은 409 SIMULATION_ACTIVE_ALREADY_EXISTS로 바꿔 던진다.
     */
    fun save(session: SimulationSession): SimulationSession
}

interface SimulationActionLogRepository {
    fun save(log: SimulationActionLog): SimulationActionLog
}

/** core 스키마 품종 기준정보 읽기 전용 조회(Q420·Q482). 잠그지 않는다 */
interface BreedReader {
    fun findBreed(breedSeq: Int): BreedInfo?
}

/** core.animal_species.species_code. ETC는 모의 입양을 시작할 수 없다(M8) */
enum class CatalogSpeciesCode {
    DOG, CAT, ETC;

    fun toSpeciesType(): SpeciesType? = when (this) {
        DOG -> SpeciesType.DOG
        CAT -> SpeciesType.CAT
        ETC -> null
    }
}

/** 품종 + 종 + 대표 이미지(URL로 변환된 값, 없으면 null) */
data class BreedInfo(
    val breedSeq: Int,
    val breedName: String,
    val speciesCode: CatalogSpeciesCode,
    val personality: Personality,
    val representativeImageUrl: String?,
)
