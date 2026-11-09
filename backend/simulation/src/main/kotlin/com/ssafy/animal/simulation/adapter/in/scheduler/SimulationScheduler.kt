package com.ssafy.animal.simulation.adapter.`in`.scheduler

import com.ssafy.animal.simulation.application.port.`in`.SessionMaintenanceUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** simulation.scheduler.enabled=false면 스케줄러를 끈다(기본 켜짐) */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = ["simulation.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class SimulationSchedulingConfig

/**
 * 모의 입양 스케줄러(S8, 명세 21.5의 7). 10분 주기 [가안]로 진행 중 세션을 한 건씩 진행시켜
 * 스탯을 계산하고 건강 0·예정 종료면 끝낸다. 인스턴스 1개 전제이며 2개 이상이 되면 ShedLock을 붙인다.
 * 이벤트 발생·만료(256·259), 산책 자동 종료(252), 요약 복구(263)는 각 작업에서 이 주기에 붙인다.
 */
@Component
@ConditionalOnProperty(name = ["simulation.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class SimulationScheduler(
    private val maintenance: SessionMaintenanceUseCase,
) {

    @Scheduled(fixedDelayString = "\${simulation.scheduler.interval:PT10M}", initialDelayString = "\${simulation.scheduler.initial-delay:PT1M}")
    fun advanceSessions() {
        val sessionSeqs = maintenance.inProgressSessionSeqs()
        var failed = 0
        // 세션마다 별도 트랜잭션: 한 건이 실패해도 나머지는 진행하고 다음 주기에 다시 시도한다
        sessionSeqs.forEach { sessionSeq ->
            runCatching { maintenance.advance(sessionSeq) }.onFailure {
                failed++
                log.error("[simulation-scheduler] 세션 진행 실패 sessionSeq={}", sessionSeq, it)
            }
        }
        log.info("[simulation-scheduler] 진행 중 세션 {}건 처리, 실패 {}건", sessionSeqs.size, failed)
    }

    companion object {
        private val log = LoggerFactory.getLogger(SimulationScheduler::class.java)
    }
}
