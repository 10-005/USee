package com.ssafy.animal.simulation.seed

import com.ssafy.animal.simulation.adapter.out.summary.OpenAiReportSummaryGenerator
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.config.LlmConfig
import com.ssafy.animal.simulation.config.SimulationConfig
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.CareReport
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.seed.SeedGame.WalkStep
import tools.jackson.databind.json.JsonMapper
import java.time.LocalTime
import java.time.OffsetDateTime

/** 시드 시각표에서 쓰는 펫샵 물품 번호(V20261109_1110) */
object SeedItems {
    const val FOOD_BOWL = 1L
    const val WATER_BOWL = 2L
    const val PAD_BOARD = 4L
    const val LEASH = 9L
    const val GROOMING_KIT = 10L
    const val TOY = 17L // 매듭 터그 로프
    const val FOOD = 22L
    const val SNACK = 26L // 씹는 간식
    const val PAD = 30L
    const val POOP_BAG = 32L
}

/**
 * dev 시드 이야기 한 편(개 말티즈 7일 세션). 시각표를 실제 서비스로 고정 시계에서 플레이하고(SeedGame) SQL로 옮긴다(SeedSql).
 * 세션 일차는 시작(20:00) 기준 24시간이라 n일차 = n-1일째 날짜 20:00 ~ 다음 날 20:00이다.
 *
 * 시각표 문법: "시각 행동"을 쉼표로 잇는다. 20시 이후는 그 일차의 첫 저녁, 나머지는 다음 날.
 * F 밥, W 물, C 배변 청소, T 놀이(장난감), S 간식, B 목욕, VET 병원, E<n> 집 돌발 이벤트 발생, BUY<n> 구매 묶음,
 * WALK<분>[/단계@<분>]… 산책. 단계: e<n> 산책 이벤트 발생, w 물, s 간식(GIVE_SNACK), p 배변 치우기(CLEAN_UP_POOP). 분은 산책 시작 뒤
 */
abstract class SeedStory {
    abstract val userSeq: Long
    abstract val animalName: String
    abstract val expectedCost: Int
    abstract val start: OffsetDateTime
    abstract val plan: Map<Int, String>
    abstract val purchases: Map<Int, List<Pair<Long, Int>>>

    /** 품종. 기본은 말티즈 */
    open val breed: BreedInfo get() = SeedGame.MALTESE

    /** 같은 세션을 넣을 회원들. 기본은 userSeq 한 명 */
    open val userSeqs: List<Long> get() = listOf(userSeq)

    /** SQL 머리말과 파일 위치 */
    abstract val sql: SeedSql.Header

    val end: OffsetDateTime get() = start.plusDays(7)

    /** n일차의 그 시각. 20시 이후는 그 일차의 첫 저녁, 나머지는 다음 날 */
    fun t(day: Int, hhmm: String): OffsetDateTime {
        val time = LocalTime.parse(hhmm)
        val date = start.toLocalDate().plusDays((day - 1).toLong() + if (time.hour >= 20) 0 else 1)
        return OffsetDateTime.of(date, time, start.offset)
    }

    fun newGame() = SeedGame(SeedCatalog.properties, start, userSeq, breed)

    fun play(game: SeedGame = newGame()): SeedGame = game.apply {
        begin(animalName, expectedCost)
        plan.forEach { (day, script) ->
            script.split(",").map { it.trim() }.forEach { entry ->
                val (hhmm, action) = entry.split(" ")
                runCatching {
                    at(t(day, hhmm))
                    act(action)
                }.onFailure { throw IllegalStateException("${day}일차 $hhmm $action 실패: ${it.message} (스탯=${session.stats()})", it) }
            }
        }
        at(end)
    }

    /** 18.18·요약이 쓰는 일차별 돌봄 집계(ReportCalculator.care와 같은 경로) */
    fun care(game: SeedGame): CareReport = game.store.reportCalculator(game.properties).care(game.session, game.store.reports.single())

    /** 요약 복구 스케줄러(ReportSummaryService)가 만들 입력과 같은 값 */
    fun summaryInput(game: SeedGame): ReportSummaryInput = ReportSummaryInput.of(
        game.session, game.store.reports.single(), game.properties.sessionDuration.toDays().toInt(),
        breedName = breed.breedName,
        referenceCost = game.properties.report.referenceCost[game.session.speciesType],
        care = care(game),
    )

    /** LLM에 보낼 JSON(호출하지 않음) */
    fun llmPayload(input: ReportSummaryInput, generator: OpenAiReportSummaryGenerator = OpenAiReportSummaryGenerator(LlmConfig(apiKey = "unused"), JsonMapper.builder().build(), SimulationConfig.loadReportWiki())) =
        generator.payload(input)

    private fun SeedGame.act(action: String) {
        when {
            action == "F" -> care(ActionCode.FEED, SeedItems.FOOD)
            action == "W" -> care(ActionCode.WATER)
            action == "C" -> care(ActionCode.CLEAN_LITTER, SeedItems.PAD)
            action == "T" -> care(ActionCode.PLAY, SeedItems.TOY)
            action == "S" -> care(ActionCode.SNACK, SeedItems.SNACK)
            action == "B" -> care(ActionCode.BATHE)
            action == "VET" -> care(ActionCode.VET_VISIT)
            action.startsWith("BUY") -> buy(*purchases.getValue(action.removePrefix("BUY").toInt()).toTypedArray())
            action.startsWith("E") -> homeEvent(action.removePrefix("E").toLong())
            action.startsWith("WALK") -> {
                val parts = action.removePrefix("WALK").split("/")
                val steps = parts.drop(1).associate { step ->
                    val (what, minute) = step.split("@")
                    minute.toInt() to when {
                        what == "w" -> WalkStep.Water
                        what == "s" -> WalkStep.Act(ActionCode.GIVE_SNACK, SeedItems.SNACK)
                        what == "p" -> WalkStep.Act(ActionCode.CLEAN_UP_POOP, SeedItems.POOP_BAG)
                        what.startsWith("e") -> WalkStep.Event(what.removePrefix("e").toLong())
                        else -> error("알 수 없는 산책 단계: $step")
                    }
                }
                walk(parts[0].toInt(), steps)
            }
            else -> error("알 수 없는 행동: $action")
        }
    }
}
