package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.domain.CareConfig
import com.ssafy.animal.simulation.domain.ShopRules
import com.ssafy.animal.simulation.domain.StatRules
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** 모의 입양 코드 설정값(API 명세 18.0 [가안], Q429). 값은 application.yml의 simulation.* 에 두고 플레이 테스트 후 조정한다 */
@ConfigurationProperties("simulation")
data class SimulationProperties(
    /** 스탯 지연 계산 단위(M2) */
    val tick: Duration,
    /** 세션 길이. 예정 종료 = 시작 + 이 값(M2) */
    val sessionDuration: Duration,
    val stats: StatRules,
    val shop: ShopRules = ShopRules(),
    val care: CareConfig = CareConfig(),
)
