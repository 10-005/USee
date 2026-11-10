package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.CareKey
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.ReportSummaryWriter
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.Stat
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 회원 1번 시드(S15P31D106-254)의 이야기 핵심을 실제 규칙으로 돌린 결과에 단언한다.
 * 규칙(application.yml simulation.*·물품·시나리오 시드)을 바꿔 이 테스트가 깨지면, 이미 적용된 시드 파일을 다시 만들지 말고
 * (Flyway 체크섬이 바뀐다) 이 테스트의 기대값이나 시각표를 고친다. 시드 파일은 적용 시점 규칙의 결과로 남는다.
 */
class User1SeedTest {

    private val game = User1Seed.play()
    private val care = User1Seed.care(game)
    private val report = game.store.reports.single()

    @Test
    fun `7일을 끝까지 마쳐 완료 상태다`() {
        assertEquals(SessionStatus.COMPLETED, game.session.status)
        assertEquals(User1Seed.END, game.session.endedDttm)
        assertTrue(Stat.entries.all { (report.finalStatsJsonb[it.key] ?: 0) > 0 })
    }

    @Test
    fun `일차별 돌봄 횟수가 보고한 시각표와 같다`() {
        // 밥, 물, 배변 청소, 놀이·간식, 산책. 권장(3·3·2·2·1)에 못 미친 칸(3일차 전부·5일차 물·6일차 배변)이 이야기의 아쉬운 점이다
        val expected = mapOf(
            1 to listOf(4, 4, 2, 4, 2),
            2 to listOf(4, 4, 2, 9, 3),
            3 to listOf(2, 1, 1, 0, 1),
            4 to listOf(4, 6, 3, 7, 2),
            5 to listOf(3, 2, 2, 8, 2),
            6 to listOf(3, 5, 1, 11, 2),
            7 to listOf(4, 5, 2, 8, 2),
        )
        assertEquals(expected, care.days.associate { day -> day.day to CareKey.entries.map { day.counts.getValue(it) } })
    }

    @Test
    fun `3일차는 물을 한 번만 줘서 수분이 위험 구간까지 떨어진다`() {
        assertEquals(1, care.days[2].counts.getValue(CareKey.WATER))
        assertTrue(3 in care.dangerZoneDays.getValue(Stat.HYDRATION.key), "${care.dangerZoneDays}")
        assertTrue(care.days[2].lowestStats!!.getValue(Stat.HYDRATION.key) < care.penalty.basicNeedBelow)
    }

    @Test
    fun `돌발 이벤트 4건 중 3건 해결, 1건(3일차 쿠션) 만료`() {
        val events = care.days.flatMap { day -> day.events.map { day.day to it } }
        assertEquals(
            listOf(
                Triple(1, "물그릇을 엎었어요", OccurrenceStatus.RESOLVED),
                Triple(3, "쿠션을 물어뜯어요", OccurrenceStatus.EXPIRED),
                Triple(5, "헥헥거려요", OccurrenceStatus.RESOLVED),
                Triple(6, "축 처져 있어요", OccurrenceStatus.RESOLVED),
            ),
            events.map { (day, event) -> Triple(day, event.title, event.status) },
        )
        assertEquals(4 to 3, report.eventCount to report.eventResolvedCount)
        // #8은 건강 40 미만에서만 생긴다: 그 판정 시각 직전 스탯
        val vet = game.store.logs.single { it.actionCode == ActionCode.VET_VISIT }
        assertTrue(vet.statsAfterJsonb.getValue("health") - vet.statDeltaJsonb!!.getValue("health") < 40)
    }

    @Test
    fun `총지출은 예상 비용 120,000원을 조금 넘고 가장 많이 쓴 분류는 병원 진료비다`() {
        assertTrue(report.totalSpent > User1Seed.EXPECTED_COST, "총지출 ${report.totalSpent}")
        assertEquals(ReportAggregator.EVENT_KEY, report.spentByCategoryJsonb.maxBy { it.value }.key)
        assertEquals(User1Seed.EXPECTED_COST - report.totalSpent, game.session.balance)
    }
}

/**
 * 시드 SQL을 다시 만들고 보고서용 미리보기를 출력한다. 평소 빌드에서는 돌지 않는다.
 * 실행: SEED_GEN=true ./gradlew :simulation:test --tests '*User1SeedGeneratorTest*' --rerun -i
 * 커밋된 파일과 같은지: 실행 뒤 git diff --exit-code -- simulation/src/main/resources/db/dev
 */
@EnabledIfEnvironmentVariable(named = "SEED_GEN", matches = "true")
class User1SeedGeneratorTest {

    @Test
    fun `시드 SQL을 쓰고 미리보기를 출력한다`() {
        val game = User1Seed.play()
        File(SeedSql.PATH).writeText(SeedSql.render(game))

        val care = User1Seed.care(game)
        val input = User1Seed.summaryInput(game)
        val report = game.store.reports.single()
        val mapper = JsonMapper.builder().build().writerWithDefaultPrettyPrinter()
        val days = game.properties.sessionDuration.toDays().toInt()

        println("=== 시각표(일차별 CARE·WALK 행동, EVENT_EXPIRE) ===")
        game.store.logs.filter { it.actionType != ActionType.SYSTEM }.groupBy { ReportAggregator.dayOf(User1Seed.START, it.regDttm!!, days) }.forEach { (day, logs) ->
            println("$day: " + logs.joinToString(", ") { "${it.regDttm!!.toLocalTime()} ${it.actionCode}" })
        }
        println("=== 이벤트 ===")
        game.store.occurrences.forEach { println("#${it.scenarioSeq} ${it.status} occurred=${it.occurredDttm} expires=${it.expiresDttm} resolved=${it.resolvedDttm}") }
        println("=== 지출 ===")
        game.store.transactions.forEach { println("${it.regDttm} ${it.transactionType} ${it.description} ${it.amount} → ${it.balanceAfter}") }
        println("total=${report.totalSpent} balance=${game.session.balance}")
        println("byCategory=${report.spentByCategoryJsonb.filterValues { it > 0 }}")
        println("daily=${report.dailySpentJsonb}")
        println("finalStats=${report.finalStatsJsonb}")
        println("actionCount=${report.actionCount} eventCount=${report.eventCount} resolved=${report.eventResolvedCount} walkCount=${report.walkCount}")
        println("=== 일차별 최저 스탯 ===")
        care.days.forEach { println("${it.day}: ${it.lowestStats}") }
        println("dangerZoneDays=${care.dangerZoneDays} belowGuidelineDays=${care.belowGuidelineDays}")
        println("=== 보유 물품 ===")
        game.store.inventory.forEach { println("${game.store.shopItems.getValue(it.itemSeq).itemName}(${it.itemSeq}) ${it.quantity}") }
        println("=== 다른 CONDITION 시나리오 조건 성립 판정 수(실제였다면 확률로 생길 수 있음) ===")
        game.conditionChecks.forEach { (seq, times) -> println("#$seq ${times.size}회 처음=${times.first()}") }
        println("=== 18.18 새 필드 ===")
        println("evaluatedDays=${care.evaluatedDays}")
        println("careGuideline=${mapper.writeValueAsString(care.guideline.mapKeys { it.key.name })}")
        println("dailyCare=${mapper.writeValueAsString(care.days.associate { d -> d.day.toString() to d.counts.mapKeys { it.key.name } })}")
        println("=== LLM 입력 JSON ===")
        println(mapper.writeValueAsString(User1Seed.llmPayload(input)))
        println("=== 규칙 문구 ===")
        println(ReportSummaryWriter.write(input))
    }
}
