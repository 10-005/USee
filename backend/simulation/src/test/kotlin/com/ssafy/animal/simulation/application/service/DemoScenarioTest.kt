package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.simulation.application.port.`in`.WalkStartResult
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.DemoConfig
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.StatCalculatorTest
import com.ssafy.animal.simulation.domain.Stats
import com.ssafy.animal.simulation.domain.WalkConfig
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 시연 회원 시나리오(명세 밖): 집 돌발 이벤트 없음, 산책마다 시작 40초 뒤 배변 1회, 하트비트 10초 */
class DemoScenarioTest {

    private val store = FakeSimulationStore().apply {
        stockShop()
        stockScenarios()
    }
    private val properties = SimulationProperties(
        tick = Duration.ofHours(1),
        sessionDuration = Duration.ofDays(7),
        stats = StatCalculatorTest.RULES,
        walk = WalkConfig(effectsPerUnit = mapOf("energy" to 7), waterEffects = mapOf("hydration" to 20)),
        demo = DemoConfig(userSeqs = setOf(DEMO.toLong())),
    )
    private val inventory = SimulationInventory(store, properties)
    private val start = OffsetDateTime.of(2026, 11, 1, 14, 0, 0, 0, ZoneOffset.ofHours(9))

    /** 확률은 모두 걸리게(0.0) 둔다. 시연 회원이면 그래도 만들지 않아야 한다 */
    private fun alwaysHit() = ScriptedRandom(doubles = List(20) { 0.0 })

    // 건강 35: CONDITION 8번(건강 < 40)도 조건이 맞는 상태
    private fun session(userSeq: Int, stats: Stats = Stats(satiety = 90, hydration = 90, cleanliness = 90, bond = 50, calmness = 90, health = 35, energy = 90))= store.save(
        SimulationSession.start(
            userSeq = userSeq, breedSeq = 21, animalName = "초코", speciesType = SpeciesType.DOG, personality = Personality.FRIENDLY,
            expectedCost = 150_000, startedAt = start, endsAt = start.plusDays(7), initialStats = stats,
        ),
    )

    private fun walkService(now: OffsetDateTime): WalkService {
        val events = store.events(properties, alwaysHit())
        return WalkService(
            sessionRepository = store, walkRepository = store, inventoryRepository = store, shopItemRepository = store,
            actionLogRepository = store, inventory = inventory,
            statRefresher = store.refresher(properties, now), progress = store.progress(properties, now), events = events,
            lifecycle = WalkLifecycle(store, store, events, store.terminator(properties), properties), properties = properties,
        )
    }

    private fun maintenance(now: OffsetDateTime) = SessionMaintenanceService(
        store, store.progress(properties, now), store.events(properties, alwaysHit()), store.refresher(properties, now),
        SimulationPushNotifier(object : EventPublisher { override fun publish(event: Any) = Unit }),
        store, WalkLifecycle(store, store, store.events(properties, alwaysHit()), store.terminator(properties), properties),
    )

    private fun startWalk(session: SimulationSession, at: OffsetDateTime = start): WalkStartResult {
        // 리드줄은 기구라 세션당 한 번만 산다
        if (store.findOwnedByCategory(session.sessionSeq!!, ItemCategory.LEASH).isEmpty()) {
            inventory.receive(session.sessionSeq!!, store.shopItems.getValue(9), 1)
        }
        return walkService(at).start(session.userSeq.toLong(), session.sessionSeq!!)
    }

    @Test
    fun `시연 회원은 확률·조건이 맞아도 집 돌발 이벤트(RANDOM·CONDITION)가 생기지 않는다`() {
        val demo = session(DEMO)
        val normal = session(17)

        maintenance(start.plusHours(1)).advance(demo.sessionSeq!!)
        assertTrue(store.occurrences.isEmpty())

        maintenance(start.plusHours(1)).advance(normal.sessionSeq!!)
        assertEquals(normal.sessionSeq, store.occurrences.single().sessionSeq)
    }

    @Test
    fun `시연 회원은 하트비트 간격 10초, 일반 회원은 60초를 받는다`() {
        assertEquals(Duration.ofSeconds(10), startWalk(session(DEMO)).heartbeatInterval)
        assertEquals(Duration.ofSeconds(60), startWalk(session(17)).heartbeatInterval)
    }

    @Test
    fun `시연 회원 산책은 40초 전에는 이벤트가 없고, 40초가 지난 첫 하트비트에서 배변 이벤트가 생긴다`() {
        val demo = session(DEMO)
        val walkSeq = startWalk(demo).walk.walkSeq!!

        listOf(10L, 20L, 30L).forEach { assertNull(walkService(start.plusSeconds(it)).heartbeat(DEMO.toLong(), demo.sessionSeq!!, walkSeq).newEvent) }
        val created = assertNotNull(walkService(start.plusSeconds(40)).heartbeat(DEMO.toLong(), demo.sessionSeq!!, walkSeq).newEvent)

        assertEquals(16L, created.scenario.scenarioSeq)
        assertEquals(walkSeq, created.occurrence.walkSeq)
        assertEquals(start.plusSeconds(40).plusMinutes(5), created.occurrence.expiresDttm)
    }

    @Test
    fun `시연 회원 산책의 배변 이벤트는 산책당 한 번이고, 다음 산책에서 다시 생긴다`() {
        val demo = session(DEMO)
        val first = startWalk(demo).walk.walkSeq!!
        val created = assertNotNull(walkService(start.plusSeconds(40)).heartbeat(DEMO.toLong(), demo.sessionSeq!!, first).newEvent)
        created.occurrence.resolve(start.plusSeconds(50), 90_000L)

        listOf(60L, 70L, 120L).forEach { assertNull(walkService(start.plusSeconds(it)).heartbeat(DEMO.toLong(), demo.sessionSeq!!, first).newEvent) }
        assertEquals(1, store.occurrences.size)

        walkService(start.plusMinutes(3)).end(DEMO.toLong(), demo.sessionSeq!!, first)
        val second = startWalk(demo, start.plusMinutes(4)).walk.walkSeq!!
        val again = assertNotNull(walkService(start.plusMinutes(4).plusSeconds(40)).heartbeat(DEMO.toLong(), demo.sessionSeq!!, second).newEvent)
        assertEquals(16L, again.scenario.scenarioSeq)
        assertEquals(second, again.occurrence.walkSeq)
    }

    private companion object {
        const val DEMO = 146
    }
}
