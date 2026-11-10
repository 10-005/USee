package com.ssafy.animal.simulation.domain

import org.springframework.test.util.ReflectionTestUtils
import java.time.OffsetDateTime

/** 일차별 돌봄 집계 테스트용 조각(도메인·컨트롤러·생성기 테스트 공용) */
object CareFixtures {
    val STATS = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60)
    val GUIDELINE = mapOf(CareKey.FEED to 3, CareKey.WATER to 3, CareKey.CLEAN_LITTER to 2, CareKey.PLAY to 2, CareKey.WALK to 1)
    val PENALTY = HealthPenalty(basicNeedBelow = 20, basicNeedAmount = 3, calmnessAtOrBelow = 20, calmnessAmount = 2)

    /** at에 저장된(regDttm) 행동 로그 */
    fun log(code: ActionCode, at: OffsetDateTime, after: Stats = STATS, delta: Map<String, Int>? = null) =
        SimulationActionLog(3001L, code, after, delta).also { ReflectionTestUtils.setField(it, "regDttm", at) }

    fun care(
        startedAt: OffsetDateTime,
        species: SpeciesType = SpeciesType.DOG,
        evaluatedDays: Int = 7,
        logs: List<SimulationActionLog> = emptyList(),
        events: List<CareEventRecord> = emptyList(),
        dailySpent: Map<String, Int> = emptyMap(),
    ) = CareAggregator.aggregate(CareSource(startedAt, species, evaluatedDays, 7, logs, events, dailySpent), GUIDELINE, PENALTY)
}
