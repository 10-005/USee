package com.ssafy.animal.simulation.domain

import com.ssafy.animal.simulation.config.SimulationProperties
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class WalkConfigTest {

    /** application.yml의 산책 가안 값 */
    private val walk = Binder(ConfigurationPropertySources.from(YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))))
        .bind("simulation", SimulationProperties::class.java)
        .get()
        .walk

    private fun minutes(m: Long) = walk.effectFor(Duration.ofMinutes(m)).changes

    @Test
    fun `10분 단위로 60분까지만 효과를 주고 10분 미만은 효과가 없다`() {
        assertEquals(emptyMap(), minutes(9))
        assertEquals(
            mapOf(Stat.ENERGY to 21, Stat.CALMNESS to 12, Stat.BOND to 3, Stat.SATIETY to -6, Stat.HYDRATION to -9, Stat.CLEANLINESS to -6),
            minutes(35),
        )
        assertEquals(minutes(60), minutes(95))
        assertEquals(42, minutes(60)[Stat.ENERGY])
    }

    @Test
    fun `가안 설정값을 읽는다`() {
        assertEquals(Duration.ofSeconds(60), walk.heartbeatInterval)
        assertEquals(Duration.ofMinutes(10), walk.autoEndAfter)
        assertEquals(mapOf(Stat.HYDRATION to 20), walk.waterEffect.changes)
        assertEquals(0.08, walk.eventChancePerHeartbeat)
        assertEquals(3, walk.eventMaxPerWalk)
        assertEquals(Duration.ofMinutes(5), walk.eventExpiry)
    }
}
