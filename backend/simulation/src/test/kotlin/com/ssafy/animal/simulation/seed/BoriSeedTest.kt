package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.config.SimulationConfig
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.ReportWikiSelector
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.Stat
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 회원 1번 두 번째 시드(보리, 리포트 위키 Q521·Q522 확인용)의 이야기 핵심을 실제 규칙으로 돌린 결과에 단언한다.
 * 규칙을 바꿔 이 테스트가 깨지면 이미 적용된 시드 파일은 다시 만들지 말고(Flyway 체크섬) 기대값이나 시각표를 고친다.
 */
class BoriSeedTest {

    private val game = BoriSeed.play()
    private val care = BoriSeed.care(game)
    private val report = game.store.reports.single()
    private val input = BoriSeed.summaryInput(game)

    @Test
    fun `7일을 끝까지 마쳐 완료 상태다`() {
        assertEquals(SessionStatus.COMPLETED, game.session.status)
        assertEquals(BoriSeed.end, game.session.endedDttm)
        assertTrue(Stat.entries.all { (report.finalStatsJsonb[it.key] ?: 0) > 0 })
    }

    @Test
    fun `돌발 이벤트 9건 중 5건 해결, 4건 놓침이고 산책 중 배변(#16)은 해결과 놓침이 한 번씩이다`() {
        val events = care.days.flatMap { day -> day.events.map { Triple(day.day, it.scenarioSeq, it.status) } }
        val r = OccurrenceStatus.RESOLVED
        val x = OccurrenceStatus.EXPIRED
        assertEquals(
            listOf(Triple(1, 16L, r), Triple(1, 6L, r), Triple(2, 1L, x), Triple(3, 14L, r), Triple(3, 4L, r), Triple(4, 3L, x), Triple(5, 15L, x), Triple(6, 16L, x), Triple(6, 5L, r)),
            events,
        )
        assertEquals(9 to 5, report.eventCount to report.eventResolvedCount)
    }

    @Test
    fun `아쉬운 점 후보에 놓친 이벤트 4건이 모두 실리고 위험 구간은 4일차 청결도뿐이다`() {
        val lacking = BoriSeed.llmPayload(input)["lacking"] as List<*>
        assertEquals(mapOf(Stat.CLEANLINESS.key to listOf(4)), care.dangerZoneDays)
        listOf("2일차: '물그릇을 엎었어요' 미해결", "4일차: '화장실 밖에 실수했어요' 미해결", "5일차: '길에 앉아 버텨요' 미해결", "6일차: '길에서 똥을 쌌어요' 미해결")
            .forEach { prefix -> assertTrue(lacking.any { it.toString().startsWith(prefix) }, "$prefix 없음: $lacking") }
    }

    @Test
    fun `위키 맥락은 놓친 이벤트 4개를 모두 담고 #16 페이지에는 해결·놓침 줄이 함께 붙는다`() {
        val context = ReportWikiSelector.select(input, care, SimulationConfig.loadReportWiki())
        val keys = context.map { it.key }
        assertTrue(keys.containsAll(listOf("status:COMPLETED", "event:1", "event:3", "event:15", "event:16", "stat:CLEANLINESS")), "$keys")
        val poop = context.single { it.key == "event:16" }.text
        assertTrue("해결했을 때:" in poop && "놓쳤을 때:" in poop, poop)
        assertTrue("해결했을 때:" !in context.single { it.key == "event:1" }.text)
        assertTrue("stat:HEALTH" !in keys, "괄호 속 건강을 위험 구간으로 읽음: $keys")
    }

    @Test
    fun `예상 비용 110,000원보다 조금 더 썼다`() {
        assertTrue(report.totalSpent > BoriSeed.expectedCost, "총지출 ${report.totalSpent}")
        assertEquals(BoriSeed.expectedCost - report.totalSpent, game.session.balance)
    }
}

/**
 * 보리 시드 SQL을 다시 만들고 미리보기를 출력한다. 평소 빌드에서는 돌지 않는다.
 * 실행: SEED_GEN=true ./gradlew :simulation:test --tests '*BoriSeedGeneratorTest*' --rerun -i
 */
@EnabledIfEnvironmentVariable(named = "SEED_GEN", matches = "true")
class BoriSeedGeneratorTest {

    @Test
    fun `시드 SQL을 쓰고 미리보기를 출력한다`() {
        println(SeedGenerator.generate(BoriSeed))
    }
}
