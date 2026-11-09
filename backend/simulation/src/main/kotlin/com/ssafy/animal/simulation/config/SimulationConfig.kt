package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SimulationConfig {

    @Bean
    fun statCalculator(properties: SimulationProperties) = StatCalculator(properties.stats, properties.tick)
}
