package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration(proxyBeanMethods = false)
class SimulationConfig {

    @Bean
    fun statCalculator(properties: SimulationProperties) = StatCalculator(properties.stats, properties.tick)

    /** 실시간 7일 세션(M2)의 기준 시계. 테스트는 고정 시계로 바꾼다 */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))
}
