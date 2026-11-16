package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.Stat
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 시연용 시바견 시드(모찌)의 핵심을 실제 규칙으로 돌린 결과에 단언한다.
 * 규칙을 바꿔 이 테스트가 깨지면 이미 적용된 시드 파일은 다시 만들지 말고(Flyway 체크섬) 기대값이나 시각표를 고친다.
 */
class ShibaSeedTest {

    private val game = ShibaSeed.play()
    private val report = game.store.reports.single()

    @Test
    fun `7일을 끝까지 마쳐 완료 상태다`() {
        assertEquals(SessionStatus.COMPLETED, game.session.status)
        assertEquals(ShibaSeed.end, game.session.endedDttm)
        assertTrue(Stat.entries.all { (report.finalStatsJsonb[it.key] ?: 0) > 0 })
    }

    @Test
    fun `7일 중 5일 이상에 지출이 있다`() {
        val spentDays = report.dailySpentJsonb.filterValues { it > 0 }.keys
        assertTrue(spentDays.size >= 5, "지출이 있는 일차: $spentDays")
        assertEquals(ShibaSeed.expectedCost - report.totalSpent, game.session.balance)
    }
}

/**
 * 시바견 시드 SQL을 다시 만들고 미리보기를 출력한다. 평소 빌드에서는 돌지 않는다.
 * 실행: SEED_GEN=true ./gradlew :simulation:test --tests '*ShibaSeedGeneratorTest*' --rerun -i
 */
@EnabledIfEnvironmentVariable(named = "SEED_GEN", matches = "true")
class ShibaSeedGeneratorTest {

    @Test
    fun `시드 SQL을 쓰고 미리보기를 출력한다`() {
        println(SeedGenerator.generate(ShibaSeed))
    }
}
