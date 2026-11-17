package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.exception.BusinessException
import com.ssafy.animal.simulation.application.port.`in`.EventQueryUseCase
import com.ssafy.animal.simulation.application.port.`in`.EventView
import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.SimulationErrorCode
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventQueryService(
    private val sessionRepository: SimulationSessionRepository,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val scenarioRepository: EventScenarioRepository,
    private val progress: SessionProgress,
) : EventQueryUseCase {

    /** 만료·종료 처리를 저장하므로 쓰기 트랜잭션이고, 행동과 겹치지 않게 세션 행을 잠근다(18.4와 같은 원칙) */
    @Transactional
    override fun getEvents(userSeq: Long, sessionSeq: Long, status: OccurrenceStatus?, pageable: Pageable): Page<EventView> {
        val session = sessionRepository.findByIdForUpdate(sessionSeq)
            ?.takeIf { it.userSeq.toLong() == userSeq }
            ?: throw BusinessException(SimulationErrorCode.SIMULATION_NOT_FOUND)
        progress.catchUp(session)
        val page = occurrenceRepository.findPage(sessionSeq, status, pageable)
        val scenarios = scenarioRepository.findByScenarioSeqs(page.content.map { it.scenarioSeq })
        return page.map { occurrence ->
            EventView(occurrence, checkNotNull(scenarios[occurrence.scenarioSeq]) { "이벤트의 시나리오가 없습니다: scenarioSeq=${occurrence.scenarioSeq}" })
        }
    }
}
