package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.random.RandomGenerator

@Configuration(proxyBeanMethods = false)
class SimulationConfig {

    @Bean
    fun statCalculator(properties: SimulationProperties) = StatCalculator(properties.stats, properties.tick)

    /**
     * 실시간 7일 세션(M2)의 기준 시계. 테스트는 고정 시계로 바꾼다.
     * PostgreSQL TIMESTAMPTZ가 마이크로초까지라 시계도 마이크로초 단위로 맞춘다.
     * 나노초 시각을 쓰면 응답(예: 포기 응답 endedDttm)과 저장 후 다시 읽은 값이 어긋난다.
     */
    /** 돌발 이벤트 RANDOM 발생 판정·시나리오 추첨용 난수. 테스트는 정해진 값을 돌려주는 구현으로 바꾼다 */
    @Bean
    fun eventRandom(): RandomGenerator = RandomGenerator.getDefault()

    @Bean
    fun clock(): Clock = Clock.tick(Clock.system(ZoneId.of("Asia/Seoul")), Duration.of(1, ChronoUnit.MICROS))
}
