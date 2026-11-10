package com.ssafy.animal.simulation.domain

import java.util.Locale

/** 요약 문구에 쓰는 한국어 이름(규칙 문구·LLM 근거 문장 공용) */
object KoreanLabels {

    private val CATEGORIES = mapOf(
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

    fun category(key: String): String = CATEGORIES[key] ?: key

    fun stat(stat: Stat): String = when (stat) {
        Stat.SATIETY -> "포만감"
        Stat.HYDRATION -> "수분"
        Stat.CLEANLINESS -> "청결도"
        Stat.BOND -> "친밀도"
        Stat.CALMNESS -> "안정감"
        Stat.HEALTH -> "건강"
        Stat.ENERGY -> "활력"
    }

    fun care(key: CareKey): String = when (key) {
        CareKey.FEED -> "밥"
        CareKey.WATER -> "물"
        CareKey.CLEAN_LITTER -> "배변 청소"
        CareKey.PLAY -> "놀이·간식"
        CareKey.WALK -> "산책"
    }

    fun action(code: ActionCode): String = when (code) {
        ActionCode.FEED -> "밥 주기"
        ActionCode.WATER -> "물 주기"
        ActionCode.CLEAN_LITTER -> "배변 청소"
        ActionCode.BATHE -> "목욕"
        ActionCode.PLAY -> "놀이"
        ActionCode.SNACK -> "간식 주기"
        ActionCode.MEDICINE -> "약 먹이기"
        ActionCode.VET_VISIT -> "병원 진료"
        ActionCode.GIVE_CATNIP -> "캣닢 주기"
        ActionCode.WALK_START -> "산책"
        ActionCode.WALK_END -> "산책 마치기"
        ActionCode.GIVE_WATER -> "산책 중 물 주기"
        ActionCode.GIVE_SNACK -> "산책 중 간식 주기"
        ActionCode.CLEAN_UP_POOP -> "산책 중 배변 치우기"
        ActionCode.EVENT_EXPIRE, ActionCode.SESSION_START, ActionCode.SESSION_END -> code.name
    }

    fun species(species: SpeciesType): String = when (species) {
        SpeciesType.DOG -> "개"
        SpeciesType.CAT -> "고양이"
    }

    // 명세에 정해진 이름이 없어 임의로 정함
    fun personality(personality: Personality): String = when (personality) {
        Personality.ACTIVE -> "활발함"
        Personality.TIMID -> "소심함"
        Personality.AGGRESSIVE -> "예민함"
        Personality.FRIENDLY -> "다정함"
    }

    /** 받침 유무로 조사를 고른다(예: josa("수분", "이", "가") → "수분이") */
    fun josa(word: String, withFinal: String, withoutFinal: String): String {
        val last = word.lastOrNull { it in '가'..'힣' } ?: return word + withFinal
        return word + if ((last - '가') % 28 != 0) withFinal else withoutFinal
    }

    fun won(amount: Int): String = String.format(Locale.KOREA, "%,d원", amount)
}

/**
 * LLM 요약의 근거 문장 후보(2차 수정). 서버가 집계에서 근거를 골라 문장으로 만들고, LLM은 이 중에서 골라 잇기만 한다.
 * 숫자·일차는 집계 값 그대로다. good은 잘한 점, lacking은 아쉬운 점(심한 순서).
 */
data class SummaryEvidence(
    val good: List<String>,
    val lacking: List<String>,
    /** 조언 근거(3차 수정): 권장에 못 미친 날이 많은 돌봄 키의 권장 횟수, 최대 2개 */
    val advice: List<String>,
) {
    companion object {
        /** 목록마다 최대 개수 */
        const val LIMIT = 6

        /** 조언 근거 최대 개수 */
        const val ADVICE_LIMIT = 2

        fun build(input: ReportSummaryInput, care: CareReport): SummaryEvidence =
            SummaryEvidence(good(input, care), lacking(input, care), advice(input, care))

        /** 상태별 결과 한 줄 */
        fun statusText(input: ReportSummaryInput): String = when (input.status) {
            SessionStatus.COMPLETED -> "${input.sessionDays}일 돌봄을 끝까지 마침"
            SessionStatus.ABANDONED -> "${input.endedDay}일째에 그만둠"
            SessionStatus.FAILED -> "돌봄 부족으로 ${input.endedDay}일째에 중단됨"
            SessionStatus.IN_PROGRESS -> error("진행 중 세션은 요약하지 않습니다.")
        }

        private fun good(input: ReportSummaryInput, care: CareReport): List<String> {
            val evaluated = care.days.take(care.evaluatedDays)
            val resolved = evaluated.flatMap { day ->
                day.events.filter { it.status == OccurrenceStatus.RESOLVED && it.minutesToResolve != null }.map {
                    "${day.day}일차: ${KoreanLabels.josa("'${it.title}'", "을", "를")} ${it.minutesToResolve}분 만에 해결(${KoreanLabels.action(it.resolveActionCode)})"
                }
            }
            // 거의 매일 채움: 충족 일수 ≥ 평가 일수 - 1 (하루도 못 채웠으면 제외)
            val steady = care.guideline.filter { it.value > 0 }.mapNotNull { (key, times) ->
                val met = evaluated.count { it.counts.getValue(key) >= times }
                if (met >= 1 && met >= care.evaluatedDays - 1) {
                    "${KoreanLabels.care(key)}: ${care.evaluatedDays}일 중 ${met}일 권장 횟수(${times}회)를 채움"
                } else {
                    null
                }
            }
            val budget = when {
                input.totalSpent == 0 || input.difference < 0 -> null
                input.difference == 0 -> "예상 비용 ${KoreanLabels.won(input.expectedCost)}에 맞춰 씀"
                else -> "예상 비용 ${KoreanLabels.won(input.expectedCost)}보다 ${KoreanLabels.won(input.difference)} 덜 씀"
            }
            val all = resolved + steady + listOfNotNull(budget)
            if (all.isNotEmpty()) return all.take(LIMIT)
            return if (input.actionCount > 0) listOf("돌봄 행동을 모두 ${input.actionCount}번 함") else emptyList()
        }

        private fun lacking(input: ReportSummaryInput, care: CareReport): List<String> {
            val evaluated = care.days.take(care.evaluatedDays)
            val penalty = care.penalty
            val danger = evaluated.flatMap { day ->
                care.dangerZoneDays.filterValues { day.day in it }.keys.map { key ->
                    val stat = Stat.fromKey(key)
                    val rule = if (stat == Stat.CALMNESS) "${penalty.calmnessAtOrBelow} 이하면" else "${penalty.basicNeedBelow} 미만이면"
                    "${day.day}일차: ${KoreanLabels.josa(KoreanLabels.stat(stat), "이", "가")} ${day.lowestStats!!.getValue(key)}까지 떨어짐($rule 건강이 깎임)"
                }
            }
            // 미해결 이벤트 줄에 "그날 … 0회"로 이미 말한 (일차, 키)는 0회 줄에서 뺀다
            val saidZero = mutableSetOf<Pair<Int, CareKey>>()
            val unresolved = evaluated.flatMap { day ->
                day.events.filter { it.status == OccurrenceStatus.EXPIRED }.map { event ->
                    val key = CareKey.of(event.resolveActionCode)?.takeIf { day.counts[it] == 0 }
                    key?.let { saidZero += day.day to it }
                    val zero = key?.let { ", 그날 ${KoreanLabels.care(it)} 0회" }.orEmpty()
                    "${day.day}일차: '${event.title}' 미해결(정답: ${KoreanLabels.action(event.resolveActionCode)})$zero"
                }
            }
            val countDays = countDays(input, care)
            val keys = care.guideline.filter { it.value > 0 }
            // 같은 날의 0회는 한 줄로 묶는다(키 순서는 CareKey 선언 순)
            val zero = countDays.mapNotNull { day ->
                val parts = keys.filter { (key, _) -> day.counts.getValue(key) == 0 && (day.day to key) !in saidZero }
                    .map { (key, times) -> "${KoreanLabels.care(key)} 0회(권장 ${times}회)" }
                if (parts.isEmpty()) null else "${day.day}일차: ${parts.joinToString(", ")}"
            }
            val short = keys.mapNotNull { (key, times) ->
                val days = countDays.filter { it.counts.getValue(key) in 1 until times }.map { it.day }
                if (days.isEmpty()) null else "${KoreanLabels.care(key)} 부족: ${days.joinToString("·")}일차(권장 ${times}회)"
            }
            val overspent = if (input.difference < 0) {
                val top = ReportAggregator.CATEGORY_KEYS.map { it to (input.spentByCategory[it] ?: 0) }.filter { it.second > 0 }.maxByOrNull { it.second }
                "예상 비용보다 ${KoreanLabels.won(-input.difference)} 더 씀" +
                    top?.let { ", 가장 많이 쓴 분류는 ${KoreanLabels.category(it.first)}(${KoreanLabels.won(it.second)})" }.orEmpty()
            } else {
                null
            }
            val all = danger + unresolved + zero + short + listOfNotNull(overspent)
            return if (all.isEmpty()) listOf("권장 횟수에 못 미친 날과 미해결 돌발 상황이 없음") else all.take(LIMIT)
        }

        /** 미달 일수(0회 포함)가 많은 키부터, 같으면 CareKey 선언 순 */
        private fun advice(input: ReportSummaryInput, care: CareReport): List<String> {
            val countDays = countDays(input, care)
            return care.guideline.filter { it.value > 0 }
                .map { (key, times) -> Triple(key, times, countDays.count { it.counts.getValue(key) < times }) }
                .filter { it.third > 0 }
                .sortedWith(compareByDescending<Triple<CareKey, Int, Int>> { it.third }.thenBy { it.first.ordinal })
                .take(ADVICE_LIMIT)
                .map { (key, times) -> "${KoreanLabels.josa(KoreanLabels.care(key), "은", "는")} 하루 ${times}회가 권장" }
        }

        /** 횟수 판정 대상 일차. 포기·중단이면 마지막 평가 일차는 하루를 다 못 채웠을 수 있어 뺀다 */
        private fun countDays(input: ReportSummaryInput, care: CareReport): List<DayCare> {
            val evaluated = care.days.take(care.evaluatedDays)
            return if (input.status == SessionStatus.COMPLETED) evaluated else evaluated.dropLast(1)
        }
    }
}
