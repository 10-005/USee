package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.Equipment
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationUseCase
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

@Service
class SimulationSessionService(
    private val sessionRepository: SimulationSessionRepository,
    private val actionLogRepository: SimulationActionLogRepository,
    private val breedReader: BreedReader,
    private val statCalculator: StatCalculator,
    private val properties: SimulationProperties,
    private val clock: Clock,
) : StartSimulationUseCase {

    /** 명세 21.5의 1: 활성 세션 검사(FOR UPDATE) → 품종·종 조회 → 세션 INSERT(스냅샷) → SESSION_START 로그 */
    @Transactional
    override fun start(userSeq: Long, command: StartSimulationCommand): SimulationSessionDetail {
        val seq = Math.toIntExact(userSeq)
        if (sessionRepository.findActiveForUpdate(seq) != null) {
            throw BusinessException(SimulationErrorCode.SIMULATION_ACTIVE_ALREADY_EXISTS)
        }
        val breed = breedReader.findBreed(command.breedSeq)
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_BREED_NOT_FOUND)
        val speciesType = breed.speciesCode.toSpeciesType()
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_SPECIES_NOT_SUPPORTED)

        // TIMESTAMPTZ는 마이크로초까지 저장하므로 응답 값과 저장 값이 같도록 맞춘다
        val now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS)
        val session = sessionRepository.save(
            SimulationSession.start(
                userSeq = seq,
                breedSeq = breed.breedSeq,
                animalName = command.animalName,
                speciesType = speciesType,
                personality = breed.personality,
                expectedCost = command.expectedCost,
                startedAt = now,
                endsAt = now.plus(properties.sessionDuration),
                initialStats = statCalculator.initialStats(),
            ),
        )
        actionLogRepository.save(SimulationActionLog.system(session, ActionCode.SESSION_START))
        return SimulationSessionDetail(session, breed, Equipment.NONE, activeWalkSeq = null)
    }
}
