package com.ssafy.animal.simulation.config

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class SimulationConfigTest {

    @Test
    fun `기준 시계는 서울 시간대이고 DB와 같은 마이크로초 단위로 잘린다`() {
        val clock = SimulationConfig().clock()

        assertEquals(ZoneId.of("Asia/Seoul"), clock.zone)
        repeat(100) { assertEquals(0, clock.instant().nano % 1_000, "마이크로초 아래 자리가 남음") }
    }
}
