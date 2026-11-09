package com.ssafy.animal.simulation.application.port.`in`

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
