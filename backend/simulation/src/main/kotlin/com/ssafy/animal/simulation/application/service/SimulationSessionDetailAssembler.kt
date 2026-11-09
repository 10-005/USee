package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.`in`.EventView
import com.ssafy.animal.simulation.application.port.`in`.SimulationSessionDetail
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.domain.SimulationSession
import org.springframework.stereotype.Component

/** 세션 상세(18.1·18.2·18.4)에 품종 블록·보유 기구·진행 중 산책·대기 중 돌발 이벤트를 붙인다 */
@Component
class SimulationSessionDetailAssembler(
    private val breedReader: BreedReader,
    private val inventoryRepository: InventoryRepository,
    private val detailReader: SessionDetailReader,
    private val occurrenceRepository: EventOccurrenceRepository,
    private val scenarioRepository: EventScenarioRepository,
) {

    /** breed를 이미 읽었으면 넘겨서 다시 조회하지 않는다 */
    fun assemble(session: SimulationSession, breed: BreedInfo? = null): SimulationSessionDetail {
        val sessionSeq = checkNotNull(session.sessionSeq) { "저장 전 세션입니다." }
        // 품종은 시드 마스터라 지워지지 않는다(Q412). 없으면 데이터 오류이므로 500으로 드러낸다
        val resolved = breed ?: checkNotNull(breedReader.findBreed(session.breedSeq)) {
            "세션의 품종 기준정보가 없습니다: sessionSeq=$sessionSeq, breedSeq=${session.breedSeq}"
        }
        return SimulationSessionDetail(
            session = session,
            breed = resolved,
            equipment = inventoryRepository.findEquipment(sessionSeq),
            activeWalkSeq = detailReader.findActiveWalkSeq(sessionSeq),
            pendingEvent = pendingEvent(sessionSeq),
        )
    }

    private fun pendingEvent(sessionSeq: Long): EventView? {
        val occurrence = occurrenceRepository.findPending(sessionSeq) ?: return null
        val scenario = checkNotNull(scenarioRepository.findByScenarioSeq(occurrence.scenarioSeq)) {
            "이벤트의 시나리오가 없습니다: scenarioSeq=${occurrence.scenarioSeq}"
        }
        return EventView(occurrence, scenario)
    }
}
