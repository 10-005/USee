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
    val report: ReportConfig = ReportConfig(),
)

/** 리포트 요약 설정(Q444·Q487 [가안]) */
data class ReportConfig(
    /** 종료 처리 후 이 시간이 지나도 요약이 없으면 스케줄러가 다시 만든다(고아 복구) */
    val summaryRecoveryDelay: Duration = Duration.ofMinutes(10),
)
