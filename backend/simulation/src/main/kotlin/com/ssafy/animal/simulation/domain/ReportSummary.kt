package com.ssafy.animal.simulation.domain

import java.time.OffsetDateTime
import java.util.Locale

/** 리포트 요약 재료(규칙 문구·LLM 공통). 저장된 종료 리포트와 세션 스냅샷에서 만든다 */
data class ReportSummaryInput(
    val status: SessionStatus,
    val speciesType: SpeciesType,
    val animalName: String,
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
) {
    val difference: Int get() = expectedCost - totalSpent

    companion object {
        fun of(session: SimulationSession, report: SimulationReport, sessionDays: Int): ReportSummaryInput {
            val endedAt: OffsetDateTime = checkNotNull(session.endedDttm) { "끝난 세션만 요약합니다: sessionSeq=${session.sessionSeq}" }
            return ReportSummaryInput(
                status = session.status,
                speciesType = session.speciesType,
                animalName = session.animalName,
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

    private val CATEGORY_LABELS = mapOf(
        "FOOD_BOWL" to "밥그릇",
        "WATER_BOWL" to "물그릇",
        "LITTER_BOX" to "화장실",
        "TOY" to "장난감",
        "FURNITURE" to "생활 용품",
        "LEASH" to "하네스와 리드줄",
        "GROOMING_KIT" to "위생 관리 키트",
        "FOOD" to "사료",
        "SNACK" to "간식",
        "LITTER" to "배변 용품",
        "SHAMPOO" to "샴푸",
        "MEDICINE" to "상비약",
        "POOP_BAG" to "배변봉투",
        "CATNIP" to "캣닢",
        ReportAggregator.EVENT_KEY to "병원 진료비",
    )

    private val STAT_LABELS = mapOf(
        Stat.SATIETY to "포만감",
        Stat.HYDRATION to "수분",
        Stat.CLEANLINESS to "청결도",
        Stat.BOND to "친밀도",
        Stat.CALMNESS to "안정감",
        Stat.HEALTH to "건강",
        Stat.ENERGY to "활력",
    )

    fun write(input: ReportSummaryInput): String = listOfNotNull(
        ending(input),
        care(input),
        cost(input),
        topCategory(input),
        events(input),
        condition(input),
    ).joinToString(" ")

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
            difference < 0 -> "예상보다 ${won(-difference)}을 더 썼어요."
            difference > 0 -> "예상보다 ${won(difference)} 덜 썼어요."
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
        return "${CATEGORY_LABELS[top.first] ?: top.first}에 가장 많이 썼어요(${won(top.second)})."
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
        return "마지막에 ${low.joinToString("·") { STAT_LABELS.getValue(it.first) }} 수치가 낮았어요."
    }

    private fun won(amount: Int) = String.format(Locale.KOREA, "%,d원", amount)
}
