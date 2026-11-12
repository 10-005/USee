package com.ssafy.animal.core.match.adapter.`in`.startup

import com.ssafy.animal.core.match.application.port.`in`.MatchRepublishUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * match.republish-on-startup=true일 때만 기동하면서 유효한 대상 전체를 MatchRequested로 다시 보낸다(Q523).
 * 켜 둔 채로 재시작하면 매번 다시 보내므로 한 번 배포한 뒤 끈다. 실제 AI_MATCH 알림이 갈 수 있다
 */
@Component
@ConditionalOnProperty(name = ["match.republish-on-startup"], havingValue = "true")
class MatchRepublishRunner(
    private val republishUseCase: MatchRepublishUseCase,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        log.warn("[match] match.republish-on-startup=true: 일괄 재발행을 시작합니다. 끝나면 설정을 끄세요")
        log.warn("[match] 일괄 재발행 끝 {}건", republishUseCase.republishAll())
    }
}
