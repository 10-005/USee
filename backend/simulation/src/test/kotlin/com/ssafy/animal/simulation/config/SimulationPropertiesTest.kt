package com.ssafy.animal.simulation.config

import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** application.yml의 simulation.* 가 명세 18.0 가안 값 그대로 바인딩되는지(enum 키 소문자 포함) */
class SimulationPropertiesTest {

    @Test
    fun `application yml의 코드 설정값을 바인딩한다`() {
        val sources = YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
        val properties = Binder(ConfigurationPropertySources.from(sources))
            .bind("simulation", SimulationProperties::class.java)
            .get()

        assertEquals(Duration.ofHours(1), properties.tick)
        assertEquals(Duration.ofDays(7), properties.sessionDuration)
        assertEquals(StatCalculatorTest.RULES, properties.stats)
        assertEquals(com.ssafy.animal.simulation.domain.EventConfig(), properties.event)
        // Q517 [가안]. 키 값은 환경 변수 자리표시자라 여기서는 보지 않는다
        assertEquals(ReportConfig().careGuideline, properties.report.careGuideline)
        assertEquals(mapOf(SpeciesType.DOG to 93_300, SpeciesType.CAT to 88_800), properties.report.referenceCost)
        assertEquals("gpt-6-luna", properties.report.llm.model)
        assertEquals(Duration.ofSeconds(20), properties.report.llm.timeout)
        assertEquals("https://api.openai.com", properties.report.llm.baseUrl)
    }
}
