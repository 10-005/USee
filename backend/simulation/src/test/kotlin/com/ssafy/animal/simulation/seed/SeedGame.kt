package com.ssafy.animal.simulation.seed

import com.ssafy.animal.common.event.EventPublisher
import com.ssafy.animal.simulation.application.port.`in`.CareActionCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseCommand
import com.ssafy.animal.simulation.application.port.`in`.PurchaseLine
import com.ssafy.animal.simulation.application.port.`in`.StartSimulationCommand
import com.ssafy.animal.simulation.application.service.CareActionService
import com.ssafy.animal.simulation.application.service.FakeSimulationStore
import com.ssafy.animal.simulation.application.service.PurchaseService
import com.ssafy.animal.simulation.application.service.ScriptedRandom
import com.ssafy.animal.simulation.application.service.SessionMaintenanceService
import com.ssafy.animal.simulation.application.service.SimulationInventory
import com.ssafy.animal.simulation.application.service.SimulationPushNotifier
import com.ssafy.animal.simulation.application.service.SimulationSessionService
import com.ssafy.animal.simulation.application.service.WalkLifecycle
import com.ssafy.animal.simulation.application.service.WalkService
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.StatCalculator
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * 시드 생성용 실제 규칙 묶음: application.yml의 simulation.* 와 펫샵·시나리오 시드 SQL(뒤 마이그레이션의 UPDATE 반영).
 * FakeSimulationStore의 stockShop·stockScenarios는 효과값이 시드와 달라 쓰지 않는다.
 */
object SeedCatalog {

    val properties: SimulationProperties = Binder(ConfigurationPropertySources.from(YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))))
        .bind("simulation", SimulationProperties::class.java)
        .get()

    fun shopItems(): List<SimulationShopItem> {
        val overrides = listOf("V20261109_1316__bathe_with_grooming_kit.sql", "V20261109_1451__catnip_mixed_effects.sql")
            .flatMap { UPDATE.findAll(migration(it)).toList() }
            .associate { it.groupValues[2].toLong() to it.groupValues[1] }
        return migration("V20261109_1110__seed_simulation_shop_items.sql").lines().mapNotNull { ITEM.matchEntire(it.trim()) }.map { m ->
            val (seq, name, category, price, scope, effects, order) = m.destructured
            val set = overrides[seq.toLong()].orEmpty()
            SimulationShopItem(
                itemSeq = seq.toLong(),
                itemName = name,
                category = ItemCategory.valueOf(category),
                price = price.toInt(),
                speciesScope = SpeciesScope.valueOf(scope),
                effects = (SET_EFFECTS.find(set)?.groupValues?.get(1) ?: effects.ifEmpty { null })?.let(::effects),
                displayOrder = order.toInt(),
                isActive = "is_active = FALSE" !in set,
            )
        }
    }

    fun scenarios(): List<EventScenario> =
        listOf("V20261109_1800__seed_simulation_event_scenarios.sql", "V20261109_2000__walk_poop_scenario.sql")
            .flatMap { migration(it).lines() }
            .mapNotNull { SCENARIO.matchEntire(it.trim()) }
            .map { m ->
                val g = m.groupValues
                EventScenario(
                    scenarioSeq = g[1].toLong(),
                    title = g[2],
                    description = g[3],
                    triggerContext = TriggerContext.valueOf(g[4]),
                    triggerType = TriggerType.valueOf(g[5]),
                    triggerCondition = CONDITION.find(g[6])?.let { c -> mapOf("stat" to c.groupValues[1], "op" to c.groupValues[2], "value" to c.groupValues[3].toInt()) },
                    cooldownHours = g[7].toIntOrNull(),
                    triggerProbability = BigDecimal(g[8]),
                    speciesScope = SpeciesScope.valueOf(g[9]),
                    weight = g[10].toInt(),
                    resolveActionCode = ActionCode.valueOf(g[11]),
                    resolveCost = g[12].toInt(),
                    resolveEffects = effects(g[13]),
                    resultText = g[14],
                    neglectEffects = effects(g[15]),
                    neglectText = g[16],
                )
            }

    private fun migration(name: String) = ClassPathResource("db/migration/$name").getContentAsString(Charsets.UTF_8)

    private fun effects(json: String) = EFFECT.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toInt() }

    private val ITEM = Regex("""^\((\d+), '([^']*)', '([A-Z_]+)', (?:TRUE|FALSE), (\d+), '[^']*', NULL, '(ALL|DOG|CAT)', (?:NULL|'(\{[^']*})'), (\d+)\)[,;]$""")
    private val UPDATE = Regex("""UPDATE simulation_shop_items\s+SET ([^;]*?)WHERE item_seq = (\d+)""")
    private val SET_EFFECTS = Regex("""effects_jsonb = '(\{[^']*})'""")
    private const val TEXT = """'([^']*)'"""
    private val SCENARIO = Regex(
        """\((\d+), $TEXT, $TEXT, '(HOME|WALK)', '(RANDOM|CONDITION)', ('\{[^']*}'|NULL), (NULL|\d+), ([\d.]+), '(DOG|CAT|ALL)', NULL, (\d+), '(\w+)', (\d+), '(\{[^']*})', $TEXT, '(\{[^']*})', $TEXT\)[,;]""",
    )
    private val EFFECT = Regex(""""(\w+)": (-?\d+)""")
    private val CONDITION = Regex("""\{"stat": "(\w+)", "op": "([<>]=?)", "value": (\d+)}""")
}

/**
 * 실제 서비스(세션 시작·구매·돌봄·산책·스케줄러 진행)를 고정 시계로 차례로 불러 한 세션을 "플레이"한다.
 * 스케줄러(SessionMaintenanceService.advance)는 시작부터 10분마다 부르고, 그때 난수는 항상 "발생 안 함"이다.
 * 돌발 이벤트는 정한 시각에 정한 시나리오가 뽑히도록 난수만 정해 실제 판정 메서드(triggerRandom·triggerCondition·하트비트)로 만든다.
 */
class SeedGame(
    val properties: SimulationProperties,
    val start: OffsetDateTime,
    private val userSeq: Long,
) {
    val store = FakeSimulationStore().apply {
        SeedCatalog.shopItems().forEach { shopItems[it.itemSeq] = it }
        SeedCatalog.scenarios().forEach { scenarios[it.scenarioSeq] = it }
        breeds[MALTESE.breedSeq] = MALTESE
    }
    private val inventory = SimulationInventory(store, properties)
    private val noPush = SimulationPushNotifier(object : EventPublisher { override fun publish(event: Any) = Unit })

    var now: OffsetDateTime = start
        private set
    private var nextCheck: OffsetDateTime = start.plus(SCHEDULER_INTERVAL)

    /** 인벤토리 행(item_seq)별 처음 생긴 시각과 마지막으로 바뀐 시각(SQL의 reg_dttm·mod_dttm) */
    val inventoryCreated = mutableMapOf<Long, OffsetDateTime>()
    val inventoryModified = mutableMapOf<Long, OffsetDateTime>()
    private val quantities = mutableMapOf<Long, Int>()

    val session: SimulationSession by lazy { store.sessions.single() }
    private val sessionSeq get() = checkNotNull(session.sessionSeq)

    fun begin(animalName: String, expectedCost: Int) {
        moveTo(start)
        SimulationSessionService(store, store, store, store.assembler(), StatCalculator(properties.stats, properties.tick), properties, clock())
            .start(userSeq, StartSimulationCommand(MALTESE.breedSeq, animalName, expectedCost))
    }

    /** t까지 시간을 보낸다. 그 사이 스케줄러 판정(10분마다)을 모두 돌린다 */
    fun at(t: OffsetDateTime) {
        require(!t.isBefore(now)) { "시각은 뒤로 갈 수 없습니다: $now → $t" }
        while (!nextCheck.isAfter(t)) {
            moveTo(nextCheck)
            maintenance(ScriptedRandom()).advance(sessionSeq)
            track()
            tallyConditions()
            nextCheck = nextCheck.plus(SCHEDULER_INTERVAL)
        }
        moveTo(t)
    }

    fun buy(vararg lines: Pair<Long, Int>) {
        PurchaseService(store, store, store, inventory, store.progress(properties, now))
            .purchase(userSeq, sessionSeq, PurchaseCommand(lines.map { (item, qty) -> PurchaseLine(item, qty) }))
        track()
    }

    fun care(code: ActionCode, itemSeq: Long? = null) {
        CareActionService(store, store, store, store, inventory, store.refresher(properties, now), store.progress(properties, now), store.events(properties), properties)
            .act(userSeq, sessionSeq, CareActionCommand(code, itemSeq))
        track()
    }

    /** 이 시각의 스케줄러 판정에서 이 집 시나리오가 뽑히게 한다(RANDOM은 발생 확률·추첨, CONDITION은 추첨·발생 확률을 통과) */
    fun homeEvent(scenarioSeq: Long) {
        val target = store.scenarios.getValue(scenarioSeq)
        check(target.triggerContext == TriggerContext.HOME)
        val stats = session.stats()
        val candidates = store.findActive(TriggerContext.HOME, target.triggerType)
            .filter { it.appliesTo(session.speciesType, session.personality) }
            .filter { target.triggerType == TriggerType.RANDOM || it.conditionMatches(stats) }
        val events = store.events(properties, pick(candidates, target))
        val created = if (target.triggerType == TriggerType.RANDOM) events.triggerRandom(session, now) else events.triggerCondition(session, now)
        check(created?.scenario?.scenarioSeq == scenarioSeq) { "$now: 시나리오 $scenarioSeq 이(가) 발생하지 않았습니다(조건·쿨다운·조용한 시간·대기 이벤트 확인). 스탯=$stats" }
    }

    /** 산책 minutes분. 하트비트는 1분마다, steps는 그 분의 하트비트 뒤에 한다(산책 이벤트 발생 또는 산책 중 물) */
    fun walk(minutes: Int, steps: Map<Int, WalkStep> = emptyMap()) {
        val begin = now
        val walkSeq = checkNotNull(walkService(ScriptedRandom()).start(userSeq, sessionSeq).walk.walkSeq)
        track()
        for (minute in 1..minutes) {
            at(begin.plusMinutes(minute.toLong()))
            val step = steps[minute]
            val random = if (step is WalkStep.Event) {
                val candidates = store.findActive(TriggerContext.WALK, TriggerType.RANDOM).filter { it.appliesTo(session.speciesType, session.personality) }
                pick(candidates, store.scenarios.getValue(step.scenarioSeq))
            } else {
                ScriptedRandom()
            }
            val heartbeat = walkService(random).heartbeat(userSeq, sessionSeq, walkSeq)
            if (step is WalkStep.Event) check(heartbeat.newEvent?.scenario?.scenarioSeq == step.scenarioSeq) { "$now: 산책 이벤트 ${step.scenarioSeq} 발생 실패" }
            if (step == WalkStep.Water) walkService(ScriptedRandom()).act(userSeq, sessionSeq, walkSeq, CareActionCommand(ActionCode.GIVE_WATER, null))
            track()
        }
        walkService(ScriptedRandom()).end(userSeq, sessionSeq, walkSeq)
        track()
    }

    sealed interface WalkStep {
        data class Event(val scenarioSeq: Long) : WalkStep
        data object Water : WalkStep
    }

    /** 후보 중 target이 뽑히는 추첨값과 "발생"하는 확률값 */
    private fun pick(candidates: List<EventScenario>, target: EventScenario): ScriptedRandom {
        val index = candidates.indexOfFirst { it.scenarioSeq == target.scenarioSeq }
        check(index >= 0) { "$now: 시나리오 ${target.scenarioSeq}이(가) 후보에 없습니다(발생 조건 불성립)" }
        return ScriptedRandom(doubles = listOf(0.0), ints = listOf(candidates.take(index).sumOf { it.weight.toInt() }))
    }

    private fun maintenance(random: ScriptedRandom) = SessionMaintenanceService(
        store, store.progress(properties, now), store.events(properties, random), store.refresher(properties, now), noPush, store, lifecycle(random),
    )

    private fun walkService(random: ScriptedRandom) = WalkService(
        store, store, store, store, store, inventory, store.refresher(properties, now), store.progress(properties, now), store.events(properties, random),
        lifecycle(random), properties,
    )

    private fun lifecycle(random: ScriptedRandom) = WalkLifecycle(store, store, store.events(properties, random), store.terminator(properties), properties)

    /**
     * 스케줄러 판정 시각마다 집 CONDITION 시나리오의 조건이 맞았던 횟수(조용한 시간·대기 이벤트·산책 중 제외).
     * 이 시드는 정한 이벤트만 만들지만, 실제 플레이였다면 확률로 생겼을 수 있는 이벤트를 보고서에 적기 위해 센다
     */
    val conditionChecks = sortedMapOf<Long, MutableList<OffsetDateTime>>()

    private fun tallyConditions() {
        if (!session.isInProgress || properties.event.isQuiet(now) || store.findPending(sessionSeq) != null || store.findActive(sessionSeq) != null) return
        store.findActive(TriggerContext.HOME, TriggerType.CONDITION)
            .filter { it.appliesTo(session.speciesType, session.personality) && it.conditionMatches(session.stats()) }
            .forEach { conditionChecks.getOrPut(it.scenarioSeq) { mutableListOf() } += now }
    }

    private fun moveTo(t: OffsetDateTime) {
        now = t
        store.now = t
    }

    private fun clock() = Clock.fixed(now.toInstant(), SEOUL)

    private fun track() {
        store.inventory.forEach { row ->
            val before = quantities[row.itemSeq]
            if (before == null) inventoryCreated[row.itemSeq] = now else if (before != row.quantity) inventoryModified[row.itemSeq] = now
            quantities[row.itemSeq] = row.quantity
        }
    }

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        val SCHEDULER_INTERVAL: Duration = Duration.ofMinutes(10)
        val MALTESE = FakeSimulationStore.MALTESE
    }
}
