package com.ssafy.animal.simulation.domain

import java.time.OffsetDateTime

/**
 * 리포트 요약 재료(규칙 문구·LLM 공통). 저장된 종료 리포트와 세션 스냅샷에서 만든다.
 * breedName·referenceCost·care는 LLM 입력용(Q517)이고 규칙 문구는 쓰지 않는다. 읽지 못했으면 null
 */
data class ReportSummaryInput(
    val status: SessionStatus,
    val speciesType: SpeciesType,
    val animalName: String,
    val personality: Personality,
    /** 종료가 시작 기준 며칠째인지(1~세션 일수) */
    val endedDay: Int,
    val sessionDays: Int,
    val expectedCost: Int,
    val totalSpent: Int,
    val spentByCategory: Map<String, Int>,
    val finalStats: Map<String, Int>,
    val actionCount: Int,
    val eventCount: Int,
    val eventResolvedCount: Int,
    val walkCount: Int,
    val breedName: String? = null,
    /** 종별 최소 예산(원, [가안]) */
    val referenceCost: Int? = null,
    val care: CareReport? = null,
) {
    val difference: Int get() = expectedCost - totalSpent

    companion object {
        fun of(
            session: SimulationSession,
            report: SimulationReport,
            sessionDays: Int,
            breedName: String? = null,
            referenceCost: Int? = null,
            care: CareReport? = null,
        ): ReportSummaryInput {
            val endedAt: OffsetDateTime = checkNotNull(session.endedDttm) { "끝난 세션만 요약합니다: sessionSeq=${session.sessionSeq}" }
            return ReportSummaryInput(
                status = session.status,
                speciesType = session.speciesType,
                animalName = session.animalName,
                personality = session.personality,
                endedDay = ReportAggregator.dayOf(session.startedDttm, endedAt, sessionDays),
                sessionDays = sessionDays,
                expectedCost = report.expectedCost,
                totalSpent = report.totalSpent,
                spentByCategory = report.spentByCategoryJsonb,
                finalStats = report.finalStatsJsonb,
                actionCount = report.actionCount,
                eventCount = report.eventCount,
                eventResolvedCount = report.eventResolvedCount,
                walkCount = report.walkCount,
                breedName = breedName,
                referenceCost = referenceCost,
                care = care,
            )
        }
    }
}

/**
 * 규칙 기반 리포트 요약(Q444의 규칙 문구). LLM 요약을 붙이기 전까지 기본 생성기이고,
 * LLM을 붙인 뒤에도 실패 시 대체 문구로 쓴다. FAILED여도 죽음 표현은 쓰지 않는다(M6).
 */
object ReportSummaryWriter {

    /** 스탯이 이 값보다 낮으면 "낮았다"고 알려 준다 */
    const val LOW_STAT_BELOW = 30

    /** 낮은 스탯은 낮은 순서로 이만큼만 말한다 */
    private const val LOW_STAT_LIMIT = 2

    /**
     * LLM 요약과 같은 네 문단(결과 / 돌봄·돌발 상황 / 지출·컨디션 / 실제 입양 조언)을 빈 줄로 잇는다(Q521).
     * 빈 문단은 빼고, 각 문단 안의 문장은 공백으로 잇는다
     */
    fun write(input: ReportSummaryInput): String = listOf(
        listOfNotNull(ending(input)),
        listOfNotNull(care(input), events(input)),
        listOfNotNull(cost(input), topCategory(input), condition(input)),
        listOfNotNull(guide(input)),
    ).filter { it.isNotEmpty() }.joinToString("\n\n") { it.joinToString(" ") }

    /** 조언 근거(SummaryEvidence.adviceKeys) 첫 키의 권장 횟수. 일차별 집계가 없거나 모두 채웠으면 기본 조언 */
    private fun guide(input: ReportSummaryInput): String {
        val (key, times) = input.care?.let { SummaryEvidence.adviceKeys(input, it).firstOrNull() }
            ?: return "실제 입양에서는 밥, 물, 배변 관리 같은 기본 돌봄을 매일 같은 시간에 챙기는 습관이 가장 중요해요."
        return "실제 입양에서는 기본 돌봄을 매일 같은 시간에 챙기는 습관이 가장 중요해요. " +
            "${KoreanLabels.josa(KoreanLabels.care(key), "을", "를")} 하루 ${times}회 챙겨 보세요."
    }

    private fun ending(input: ReportSummaryInput) = when (input.status) {
        SessionStatus.COMPLETED -> "${input.animalName}의 ${input.sessionDays}일 돌봄을 끝까지 마쳤어요."
        SessionStatus.ABANDONED -> "${input.endedDay}일째에 ${input.animalName}의 돌봄을 그만뒀어요."
        SessionStatus.FAILED -> "돌봄이 부족해 ${input.endedDay}일째에 ${input.animalName}의 체험이 중단됐어요."
        SessionStatus.IN_PROGRESS -> error("진행 중 세션은 요약하지 않습니다.")
    }

    private fun care(input: ReportSummaryInput): String {
        if (input.actionCount == 0) return "돌봄 행동은 한 번도 하지 않았어요."
        val walk = if (input.speciesType == SpeciesType.DOG && input.walkCount > 0) " 산책은 ${input.walkCount}번 다녀왔어요." else ""
        return "돌봄은 모두 ${input.actionCount}번 했어요.$walk"
    }

    private fun cost(input: ReportSummaryInput): String {
        if (input.totalSpent == 0) return "아무것도 사지 않아 지출이 없었어요."
        val difference = input.difference
        return when {
            difference < 0 -> "예상보다 ${KoreanLabels.won(-difference)}을 더 썼어요."
            difference > 0 -> "예상보다 ${KoreanLabels.won(difference)} 덜 썼어요."
            else -> "예상한 비용과 똑같이 썼어요."
        }
    }

    /** 가장 많이 쓴 분류. 같으면 분류 순서(기구 → 소모품 → 진료비)가 앞선 것 */
    private fun topCategory(input: ReportSummaryInput): String? {
        val top = ReportAggregator.CATEGORY_KEYS
            .map { it to (input.spentByCategory[it] ?: 0) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?: return null
        return "${KoreanLabels.category(top.first)}에 가장 많이 썼어요(${KoreanLabels.won(top.second)})."
    }

    private fun events(input: ReportSummaryInput): String? = when {
        input.eventCount == 0 -> null
        input.eventResolvedCount >= input.eventCount -> "돌발 상황 ${input.eventCount}건을 모두 잘 해결했어요."
        input.eventResolvedCount == 0 -> "돌발 상황 ${input.eventCount}건을 해결하지 못했어요."
        else -> "돌발 상황 ${input.eventCount}건 중 ${input.eventResolvedCount}건을 해결했어요."
    }

    private fun condition(input: ReportSummaryInput): String? {
        val low = Stat.entries
            .mapNotNull { stat -> input.finalStats[stat.key]?.let { stat to it } }
            .filter { it.second < LOW_STAT_BELOW }
            .sortedBy { it.second }
            .take(LOW_STAT_LIMIT)
        if (low.isEmpty()) return if (input.status == SessionStatus.COMPLETED) "마지막까지 컨디션을 잘 지켰어요." else null
        return "마지막에 ${low.joinToString("·") { KoreanLabels.stat(it.first) }} 수치가 낮았어요."
    }

}
