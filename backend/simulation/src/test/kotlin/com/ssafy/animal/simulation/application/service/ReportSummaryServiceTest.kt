package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.ReportSummaryGenerator
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportSummaryInput
import com.ssafy.animal.simulation.domain.ReportSummaryWriter
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationReportSummarized
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportSummaryServiceTest {

    private val store = FakeSimulationStore().apply {
        stockShop()
        breeds[21] = FakeSimulationStore.MALTESE
    }
    private val properties = SimulationProperties(Duration.ofHours(1), Duration.ofDays(7), StatCalculatorTest.RULES)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val summarized = mutableListOf<Any>()

    private fun endedSession(userSeq: Int, endedAt: OffsetDateTime): SimulationSession {
        val session = store.save(
            SimulationSession.start(
                userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
                expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
                initialStats = StatCalculator(properties.stats, properties.tick).initialStats(),
            ),
        )
        store.now = endedAt
        store.progress(properties, endedAt).abandon(session)
        return session
    }

    private fun service(generator: ReportSummaryGenerator, now: OffsetDateTime = start.plusDays(3)) = ReportSummaryService(
        reportRepository = store,
        sessionRepository = store,
        generator = generator,
        reportCalculator = store.reportCalculator(properties, now),
        breedReader = store,
        eventPublisher = ApplicationEventPublisher { summarized += it },
        properties = properties,
        clock = Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul")),
    )

    /** 정해진 순서로 실패(null)·문구를 돌려주는 생성기 */
    private class ScriptedGenerator(vararg results: String?) : ReportSummaryGenerator {
        private val queue = ArrayDeque(results.toList())
        var calls = 0

        override fun generate(input: ReportSummaryInput): String {
            calls++
            return queue.removeFirstOrNull() ?: error("생성 실패")
        }
    }

    @Test
    fun `요약을 만들어 저장하고 요약 완료 이벤트를 한 번 발행한다`() {
        val session = endedSession(17, start.plusHours(5))
        val generator = ScriptedGenerator("  짧은 요약입니다.  ")

        assertTrue(service(generator).summarize(session.sessionSeq!!))

        assertEquals("짧은 요약입니다.", store.reports.single().summary)
        assertEquals(listOf<Any>(SimulationReportSummarized(session.sessionSeq!!, 17, SessionStatus.ABANDONED, "초코")), summarized)
    }

    @Test
    fun `생성기에는 일차별 집계·품종·최소 예산을 실어 보낸다`() {
        val session = endedSession(17, start.plusDays(2).plusHours(5))
        var received: ReportSummaryInput? = null

        val capturing = object : ReportSummaryGenerator {
            override fun generate(input: ReportSummaryInput): String {
                received = input
                return "LLM 요약"
            }
        }

        service(capturing).summarize(session.sessionSeq!!)

        val input = received!!
        assertEquals(3, input.care!!.evaluatedDays)
        assertEquals(7, input.care!!.days.size)
        assertEquals("말티즈", input.breedName)
        assertEquals(93_300, input.referenceCost)
        assertEquals(Personality.FRIENDLY, input.personality)
        assertEquals("LLM 요약", store.reports.single().summary)
    }

    @Test
    fun `이미 요약이 있거나 리포트가 없으면 아무것도 하지 않는다`() {
        val session = endedSession(17, start.plusHours(5))
        val service = service(ScriptedGenerator("첫 요약", "둘째 요약"))

        assertTrue(service.summarize(session.sessionSeq!!))
        assertFalse(service.summarize(session.sessionSeq!!))
        assertFalse(service.summarize(9999L))

        assertEquals("첫 요약", store.reports.single().summary)
        assertEquals(1, summarized.size)
    }

    @Test
    fun `생성기가 한 번 실패하면 한 번 더 시도한다`() {
        val session = endedSession(17, start.plusHours(5))
        val generator = ScriptedGenerator(null, "재시도 요약")

        service(generator).summarize(session.sessionSeq!!)

        assertEquals(2, generator.calls)
        assertEquals("재시도 요약", store.reports.single().summary)
    }

    @Test
    fun `생성기가 두 번 실패하거나 빈 문구면 규칙 문구로 저장하고 이벤트도 발행한다`() {
        val failed = endedSession(17, start.plusHours(5))
        val blank = endedSession(18, start.plusHours(6))
        val failing = ScriptedGenerator(null, null)

        service(failing).summarize(failed.sessionSeq!!)
        service(ScriptedGenerator("   ", "")).summarize(blank.sessionSeq!!)

        assertEquals(2, failing.calls)
        val failedReport = store.findBySessionSeq(failed.sessionSeq!!)!!
        assertEquals(ReportSummaryWriter.write(ReportSummaryInput.of(failed, failedReport, 7)), failedReport.summary)
        assertTrue(store.findBySessionSeq(blank.sessionSeq!!)!!.summary!!.startsWith("1일째에 초코의 돌봄을 그만뒀어요."))
        assertEquals(2, summarized.size)
    }

    @Test
    fun `복구 대상은 요약이 없고 종료 처리 후 복구 대기 시간이 지난 리포트다`() {
        val old = endedSession(17, start.plusHours(1))
        val recent = endedSession(18, start.plusHours(2))
        val done = endedSession(19, start.plusMinutes(30))
        service(ScriptedGenerator("요약")).summarize(done.sessionSeq!!)

        // 지금 = 두 번째 종료 + 5분 → 첫 종료(1시간 5분 전)만 10분이 지났다
        val missing = service(ScriptedGenerator(), now = start.plusHours(2).plusMinutes(5)).summaryMissingSessionSeqs()

        assertEquals(listOf(old.sessionSeq), missing)
        assertFalse(recent.sessionSeq in missing)
    }
}
