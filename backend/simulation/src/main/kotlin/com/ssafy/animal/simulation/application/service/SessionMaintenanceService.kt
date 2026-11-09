package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.`in`.SessionMaintenanceUseCase
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SessionMaintenanceService(
    private val sessionRepository: SimulationSessionRepository,
    private val progress: SessionProgress,
    private val events: SessionEvents,
    private val statRefresher: SessionStatRefresher,
    private val pushNotifier: SimulationPushNotifier,
) : SessionMaintenanceUseCase {

    @Transactional(readOnly = true)
    override fun inProgressSessionSeqs(): List<Long> = sessionRepository.findInProgressSessionSeqs()

    /**
     * 목록을 읽은 뒤 사이에 끝났을 수 있어 잠근 뒤 다시 본다(catchUp이 끝난 세션은 건드리지 않음).
     * 진행시킨 뒤에도 진행 중이면 돌발 이벤트를 판정한다: 스탯 조건에 맞는 CONDITION(259)을 먼저 보고,
     * 안 생겼을 때만 RANDOM(256)을 확률로 만든다(Q499). 새로 생긴 집 이벤트는 커밋 후 EVENT 푸시를 보낸다(260).
     */
    @Transactional
    override fun advance(sessionSeq: Long) {
        val session = sessionRepository.findByIdForUpdate(sessionSeq) ?: return
        progress.catchUp(session)
        if (!session.isInProgress) return
        val now = statRefresher.now()
        val created = events.triggerCondition(session, now) ?: events.triggerRandom(session, now) ?: return
        pushNotifier.eventOccurred(session, created.scenario)
    }

    /** 명세 21.6 UserDeleted: 진행 중 세션 ABANDONED + 리포트. 그 사이 예정 종료·건강 0이었으면 그 상태로 끝낸다 */
    @Transactional
    override fun abandonForWithdrawal(userSeq: Long) {
        val session = sessionRepository.findActiveForUpdate(Math.toIntExact(userSeq)) ?: return
        progress.catchUp(session)
        if (session.isInProgress) progress.abandon(session)
    }
}
