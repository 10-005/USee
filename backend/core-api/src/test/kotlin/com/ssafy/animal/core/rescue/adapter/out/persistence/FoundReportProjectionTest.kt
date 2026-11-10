package com.ssafy.animal.core.rescue.adapter.out.persistence

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.projection.SpelAwareProxyProjectionFactory
import java.time.Instant

class FoundReportProjectionTest {
    // 네이티브 쿼리는 timestamptz를 Instant로 돌려준다. 프로젝션 타입이 다르면 getter에서 예외(구조 상세 500)
    @Test
    fun `occurredDttm은 네이티브 쿼리가 주는 Instant를 그대로 받는다`() {
        val occurred = Instant.parse("2026-11-06T22:10:00Z")
        val projection = SpelAwareProxyProjectionFactory()
            .createProjection(FoundReportProjection::class.java, mapOf("occurredDttm" to occurred))

        assertThat(projection.occurredDttm).isEqualTo(occurred)
    }
}
