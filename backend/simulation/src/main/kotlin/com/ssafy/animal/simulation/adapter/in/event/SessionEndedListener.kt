package com.ssafy.animal.simulation.adapter.`in`.event

import com.ssafy.animal.simulation.application.port.`in`.ReportSummaryUseCase
import com.ssafy.animal.simulation.domain.SimulationSessionEnded
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 요약 생성 같은 커밋 후 작업을 요청 스레드 밖에서 돌린다(Boot 기본 applicationTaskExecutor 사용) */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class SimulationAsyncConfig

/**
 * 세션 종료 커밋 후 리포트 요약을 만든다(Q444·Q487). 종료 응답(18.5 등)은 요약을 기다리지 않는다.
 * 커밋이 롤백되면 받지 않는다. 실패는 로그만 남기고, 스케줄러가 요약 없는 리포트를 다시 처리한다.
 */
@Component
class SessionEndedListener(
    private val summaryUseCase: ReportSummaryUseCase,
) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSessionEnded(event: SimulationSessionEnded) {
        runCatching { summaryUseCase.summarize(event.sessionSeq) }
            .onFailure { log.error("[simulation-summary] 종료 후 요약 생성 실패 sessionSeq={}", event.sessionSeq, it) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SessionEndedListener::class.java)
    }
}
