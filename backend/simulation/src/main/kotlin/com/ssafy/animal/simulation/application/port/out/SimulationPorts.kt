package com.ssafy.animal.simulation.application.port.out

import com.ssafy.animal.simulation.domain.Equipment
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

    /** 목록용 일괄 조회. 없는 품종은 결과 맵에서 빠진다 */
    fun findBreeds(breedSeqs: Collection<Int>): Map<Int, BreedInfo>
}

/** 세션 상세·목록에 붙는 파생 정보. 인벤토리·산책·리포트 엔티티가 생기기 전까지 읽기 전용 조회로 둔다 */
interface SessionDetailReader {
    /** 수량 1 이상인 기구 4종 보유 여부 */
    fun findEquipment(sessionSeq: Long): Equipment

    /** 진행 중 산책(세션당 최대 1건, 부분 UNIQUE). 없으면 null */
    fun findActiveWalkSeq(sessionSeq: Long): Long?

    /** 종료 리포트가 있는 세션 번호(18.3 hasReport) */
    fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>): Set<Long>
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
