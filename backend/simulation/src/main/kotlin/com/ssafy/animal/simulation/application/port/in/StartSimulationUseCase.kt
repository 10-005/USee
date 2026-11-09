package com.ssafy.animal.simulation.application.port.`in`

import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.domain.SimulationSession

interface StartSimulationUseCase {
    /**
     * 18.1 세션 시작(USER 전용). 진행 중 세션이 있으면 409 SIMULATION_ACTIVE_ALREADY_EXISTS,
     * 품종이 없으면 404 SIMULATION_BREED_NOT_FOUND, 품종의 종이 ETC면 409 SIMULATION_SPECIES_NOT_SUPPORTED
     */
    fun start(userSeq: Long, command: StartSimulationCommand): SimulationSessionDetail
}

data class StartSimulationCommand(
    val breedSeq: Int,
    val animalName: String,
    val expectedCost: Int,
)

/** 세션 상세(18.1·18.2·18.4 공통 응답의 원본). 기구·진행 중 산책은 구매·산책 작업에서 실제 값으로 채운다 */
data class SimulationSessionDetail(
    val session: SimulationSession,
    val breed: BreedInfo,
    val equipment: Equipment,
    val activeWalkSeq: Long?,
)

/** 보유 기구(메인 화면 오브젝트 활성 여부). 명세 18.0 SimulationEquipment */
data class Equipment(
    val foodBowl: Boolean,
    val waterBowl: Boolean,
    val litterBox: Boolean,
    val toy: Boolean,
) {
    companion object {
        /** 인벤토리가 빈 채로 시작한다(18.1, M5·M9) */
        val NONE = Equipment(foodBowl = false, waterBowl = false, litterBox = false, toy = false)
    }
}
