package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.common.event.SimulationPushRequested
import com.ssafy.animal.common.event.SimulationPushType
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationReportSummarized
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.Stats
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulationPushNotifierTest {

    private val published = mutableListOf<Any>()
    private val notifier = SimulationPushNotifier(object : EventPublisher { override fun publish(event: Any) { published += event } })
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))
    private val session = SimulationSession.start(
        userSeq = 17, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
        expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7),
        initialStats = Stats(satiety = 70, hydration = 70, cleanliness = 80, bond = 30, calmness = 50, health = 90, energy = 60),
    )

    @Test
    fun `집 이벤트는 EVENT 푸시를 보내고 산책 이벤트는 보내지 않는다`() {
        val home = EventScenario(1, "물그릇을 엎었어요", "목이 마를 텐데", TriggerContext.HOME, TriggerType.RANDOM, ActionCode.WATER)
        val walk = EventScenario(13, "헥헥거려요", "혀를 내밀어요", TriggerContext.WALK, TriggerType.RANDOM, ActionCode.GIVE_WATER, speciesScope = SpeciesScope.DOG)

        notifier.eventOccurred(session, home)
        notifier.eventOccurred(session, walk)

        assertEquals(
            listOf<Any>(SimulationPushRequested(17, SimulationPushType.EVENT, "초코에게 무슨 일이 생겼어요", "물그릇을 엎었어요. 들어와서 살펴봐 주세요.")),
            published,
        )
    }

    @Test
    fun `요약이 저장되면 완료·돌봄 부족은 SESSION_END 푸시, 포기는 보내지 않는다`() {
        notifier.onReportSummarized(SimulationReportSummarized(3001, 17, SessionStatus.COMPLETED, "초코"))
        notifier.onReportSummarized(SimulationReportSummarized(3002, 18, SessionStatus.FAILED, "보리"))
        notifier.onReportSummarized(SimulationReportSummarized(3003, 19, SessionStatus.ABANDONED, "나비"))

        assertEquals(
            listOf<Any>(
                SimulationPushRequested(17, SimulationPushType.SESSION_END, "모의 입양이 끝났어요", "초코와의 7일이 끝났어요. 리포트를 확인해 보세요."),
                SimulationPushRequested(18, SimulationPushType.SESSION_END, "모의 입양이 중단됐어요", "돌봄이 부족해 보리의 체험이 중단됐어요. 리포트를 확인해 보세요."),
            ),
            published,
        )
        assertTrue(published.none { (it as SimulationPushRequested).userSeq == 19L })
    }
}
