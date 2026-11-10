package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.adapter.out.summary.OpenAiReportSummaryGenerator
import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.CareReport
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.seed.SeedGame.WalkStep
import tools.jackson.databind.json.JsonMapper
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * dev 회원 1번의 모의 입양 완료 세션 시드(S15P31D106-254) 시각표. 개 말티즈 "콩이", 예상 비용 120,000원.
 * 세션 일차는 시작(20:00) 기준 24시간이라 n일차 = n-1일째 날짜 20:00 ~ 다음 날 20:00이다. 23시~7시에는 행동하지 않는다.
 *
 * 기획 표에서 바꾼 점(규칙상 표 그대로는 3일차에 건강 0으로 FAILED): 안정감이 시간당 2.6(FRIENDLY ×1.3) 줄어 하루 약 62가 필요해
 * 권장을 채운 날은 산책 2번·놀이·간식을 표보다 많이 했다. 이야기의 아쉬운 점(권장 미달)은 3일차(밥 2·물 1·배변 1·놀이 0),
 * 5일차(물 2), 6일차(배변 1)로 표와 같고, 4일차는 표(놀이 1·산책 0)와 달리 회복하는 날이다. 건강 감소는 3일차 수분 부족에서만 난다.
 */
object User1Seed {
    const val USER_SEQ = 1L
    const val ANIMAL_NAME = "콩이"
    const val EXPECTED_COST = 120_000

    val START: OffsetDateTime = OffsetDateTime.of(2026, 11, 3, 20, 0, 0, 0, ZoneOffset.ofHours(9))
    val END: OffsetDateTime = START.plusDays(7)

    // 시드 물품 번호(V20261109_1110)
    private const val FOOD_BOWL = 1L
    private const val WATER_BOWL = 2L
    private const val PAD_BOARD = 4L
    private const val LEASH = 9L
    private const val GROOMING_KIT = 10L
    private const val TOY = 17L // 매듭 터그 로프
    private const val FOOD = 22L
    private const val SNACK = 26L // 씹는 간식
    private const val PAD = 30L
    private const val POOP_BAG = 32L

    /** n일차의 그 시각. 20시 이후는 그 일차의 첫 저녁, 나머지는 다음 날 */
    fun t(day: Int, hhmm: String): OffsetDateTime {
        val time = LocalTime.parse(hhmm)
        val date = START.toLocalDate().plusDays((day - 1).toLong() + if (time.hour >= 20) 0 else 1)
        return OffsetDateTime.of(date, time, START.offset)
    }

    fun newGame() = SeedGame(SeedCatalog.properties, START, USER_SEQ)

    /**
     * 일차별 시각표. "시각 행동"을 쉼표로 잇는다. 20시 이후는 그 일차의 첫 저녁, 나머지는 다음 날.
     * F 밥, W 물, C 배변 청소, T 놀이(장난감), S 간식, B 목욕, VET 병원, E<n> 집 돌발 이벤트 발생,
     * WALK<분>[/e<n>@<분>][/w@<분>] 산책(산책 이벤트 발생·산책 중 물은 시작 뒤 분), BUY<n> 구매 묶음
     */
    val PLAN: Map<Int, String> = mapOf(
        1 to "20:00 BUY1, 20:10 F, 21:00 WALK30, 21:40 W, 21:50 T, 22:30 C, 22:40 S, 07:30 W, 07:40 F, 09:00 WALK60, 10:10 E1, 10:50 W, 13:00 F, 13:10 T, 16:00 W, 17:00 T, 18:30 C, 19:40 F",
        2 to "20:30 T, 21:00 WALK30, 21:40 W, 21:50 T, 22:40 S, 07:30 W, 07:40 F, 08:00 C, 09:00 WALK60, 10:10 W, 13:00 F, 13:10 T, 13:30 W, 17:00 F, 17:10 T, 18:00 C, 18:30 WALK60, 19:35 S, 19:40 T, 19:45 S, 19:50 F, 19:55 T",
        3 to "07:30 F, 07:40 C, 11:00 E5, 13:00 F, 15:00 WALK60, 19:30 W",
        4 to "20:05 BUY4, 20:06 W, 20:07 S, 20:10 T, 20:20 C, 21:00 WALK60, 22:10 W, 22:20 F, 22:30 B, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 07:50 C, 09:00 WALK60, 10:10 W, 13:00 F, 13:10 T, 16:00 W, 17:00 S, 18:30 C, 19:40 F, 19:50 S, 19:55 W",
        5 to "21:00 WALK20, 21:50 T, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 07:50 T, 08:00 C, 09:00 WALK60/e13@20/w@23, 10:10 S, 13:00 F, 13:10 S, 17:00 T, 18:30 C, 19:40 F, 19:50 T",
        6 to "20:10 W, 21:00 WALK20, 21:50 T, 22:30 W, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 07:45 S, 07:50 C, 08:10 E8, 09:10 VET, 09:20 S, 10:00 WALK40, 11:00 W, 11:10 T, 13:00 F, 13:10 S, 15:00 B, 16:00 W, 16:10 S, 17:00 T, 18:30 S, 19:30 F, 19:40 S",
        7 to "21:00 WALK20, 21:40 W, 21:50 T, 22:40 S, 22:50 S, 07:30 W, 07:40 F, 08:00 C, 09:40 W, 10:20 T, 12:30 F, 13:10 T, 15:00 T, 16:00 W, 17:00 F, 17:10 S, 17:50 C, 18:00 WALK60, 19:10 B, 19:20 F, 19:30 W, 19:40 T",
    )

    private val PURCHASES: Map<Int, List<Pair<Long, Int>>> = mapOf(
        1 to listOf(FOOD_BOWL to 1, WATER_BOWL to 1, PAD_BOARD to 1, LEASH to 1, GROOMING_KIT to 1, TOY to 1, FOOD to 2, PAD to 2, SNACK to 1, POOP_BAG to 1),
        4 to listOf(SNACK to 5),
    )

    fun play(game: SeedGame = newGame()): SeedGame = game.apply {
        begin(ANIMAL_NAME, EXPECTED_COST)
        PLAN.forEach { (day, script) ->
            script.split(",").map { it.trim() }.forEach { entry ->
                val (hhmm, action) = entry.split(" ")
                at(t(day, hhmm))
                act(action)
            }
        }
        at(END)
    }

    /** 18.18·요약이 쓰는 일차별 돌봄 집계(ReportCalculator.care와 같은 경로) */
    fun care(game: SeedGame): CareReport = game.store.reportCalculator(game.properties).care(game.session, game.store.reports.single())

    /** 요약 복구 스케줄러(ReportSummaryService)가 만들 입력과 같은 값 */
    fun summaryInput(game: SeedGame): ReportSummaryInput = ReportSummaryInput.of(
        game.session, game.store.reports.single(), game.properties.sessionDuration.toDays().toInt(),
        breedName = SeedGame.MALTESE.breedName,
        referenceCost = game.properties.report.referenceCost[game.session.speciesType],
        care = care(game),
    )

    /** LLM에 보낼 JSON(호출하지 않음) */
    fun llmPayload(input: ReportSummaryInput, generator: OpenAiReportSummaryGenerator = OpenAiReportSummaryGenerator(LlmConfig(apiKey = "unused"), JsonMapper.builder().build())) =
        generator.payload(input)

    private fun SeedGame.act(action: String) {
        when {
            action == "F" -> care(ActionCode.FEED, FOOD)
            action == "W" -> care(ActionCode.WATER)
            action == "C" -> care(ActionCode.CLEAN_LITTER, PAD)
            action == "T" -> care(ActionCode.PLAY, TOY)
            action == "S" -> care(ActionCode.SNACK, SNACK)
            action == "B" -> care(ActionCode.BATHE)
            action == "VET" -> care(ActionCode.VET_VISIT)
            action.startsWith("BUY") -> buy(*PURCHASES.getValue(action.removePrefix("BUY").toInt()).toTypedArray())
            action.startsWith("E") -> homeEvent(action.removePrefix("E").toLong())
            action.startsWith("WALK") -> {
                val parts = action.removePrefix("WALK").split("/")
                val steps = parts.drop(1).associate { step ->
                    val (what, minute) = step.split("@")
                    minute.toInt() to if (what == "w") WalkStep.Water else WalkStep.Event(what.removePrefix("e").toLong())
                }
                walk(parts[0].toInt(), steps)
            }
            else -> error("알 수 없는 행동: $action")
        }
    }
}
