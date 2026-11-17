package com.ssafy.animal.core.match.adapter.`in`.startup

import com.ssafy.animal.core.match.application.port.`in`.MatchRepublishUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** 기동 때 match.republish-on-startup(실행 이름)으로 일괄 재발행을 한 번 시도한다(Q523·Q524). 비어 있거나 false면 아무것도 안 한다 */
@Component
class MatchRepublishRunner(
    private val republishUseCase: MatchRepublishUseCase,
    @Value("\${match.republish-on-startup:}") private val runName: String,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        republishUseCase.republishOnce(runName)
    }
}
