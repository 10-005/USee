package com.ssafy.animal.simulation.application.service

import com.ssafy.animal.simulation.application.port.out.ActionLogEntry
import com.ssafy.animal.simulation.application.port.out.BreedInfo
import com.ssafy.animal.simulation.application.port.out.BreedReader
import com.ssafy.animal.simulation.application.port.out.CatalogSpeciesCode
import com.ssafy.animal.simulation.application.port.out.EventCounts
import com.ssafy.animal.simulation.application.port.out.EventOccurrenceRepository
import com.ssafy.animal.simulation.application.port.out.EventScenarioRepository
import com.ssafy.animal.simulation.domain.EventOccurrence
import com.ssafy.animal.simulation.domain.EventScenario
import com.ssafy.animal.simulation.domain.OccurrenceStatus
import com.ssafy.animal.simulation.domain.TriggerContext
import com.ssafy.animal.simulation.domain.TriggerType
import com.ssafy.animal.simulation.application.port.out.ReportListEntry
import com.ssafy.animal.simulation.application.port.out.ReportRepository
import com.ssafy.animal.simulation.application.port.out.UserInfo
import com.ssafy.animal.simulation.application.port.out.UserReader
import com.ssafy.animal.simulation.application.port.out.SessionClosingRepository
import com.ssafy.animal.simulation.application.port.out.InventoryEntry
import com.ssafy.animal.simulation.application.port.out.InventoryRepository
import com.ssafy.animal.simulation.application.port.out.SessionDetailReader
import com.ssafy.animal.simulation.application.port.out.SimulationActionLogRepository
import com.ssafy.animal.simulation.application.port.out.ShopItemRepository
import com.ssafy.animal.simulation.application.port.out.SimulationSessionRepository
import com.ssafy.animal.simulation.application.port.out.TransactionEntry
import com.ssafy.animal.simulation.application.port.out.TransactionRepository
import com.ssafy.animal.simulation.domain.ActionCode
import com.ssafy.animal.simulation.domain.ActionType
import com.ssafy.animal.simulation.domain.Equipment
import com.ssafy.animal.simulation.domain.ItemCategory
import com.ssafy.animal.simulation.domain.Personality
import com.ssafy.animal.simulation.domain.ReportAggregator
import com.ssafy.animal.simulation.domain.SimulationReport
import com.ssafy.animal.simulation.domain.SpendRecord
import com.ssafy.animal.simulation.domain.SessionStatus
import com.ssafy.animal.simulation.domain.SimulationActionLog
import com.ssafy.animal.simulation.domain.SimulationInventoryItem
import com.ssafy.animal.simulation.domain.SimulationSession
import com.ssafy.animal.simulation.domain.SimulationShopItem
import com.ssafy.animal.simulation.domain.SimulationTransaction
import com.ssafy.animal.simulation.domain.SpeciesScope
import com.ssafy.animal.simulation.domain.SpeciesType
import com.ssafy.animal.simulation.domain.TransactionType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import com.ssafy.animal.simulation.config.SimulationProperties
import com.ssafy.animal.simulation.domain.StatCalculator
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.random.RandomGenerator

/** 서비스 테스트용 메모리 저장소. 포트 계약만 흉내 낸다 */
class FakeSimulationStore :
    SimulationSessionRepository,
    SimulationActionLogRepository,
    BreedReader,
    SessionDetailReader,
    InventoryRepository,
    ShopItemRepository,
    TransactionRepository,
    ReportRepository,
    UserReader,
    EventScenarioRepository,
    EventOccurrenceRepository,
    SessionClosingRepository {

    val reports = mutableListOf<SimulationReport>()
    val walkCounts = mutableMapOf<Long, Int>()

    override fun save(report: SimulationReport) = report.also {
        ReflectionTestUtils.setField(it, "reportSeq", 9000L + reports.size + 1)
        now?.let { at -> ReflectionTestUtils.setField(it, "regDttm", at) }
        reports += it
    }

    override fun updateSummaryIfNull(sessionSeq: Long, summary: String): Boolean {
        val report = findBySessionSeq(sessionSeq)?.takeIf { it.summary == null } ?: return false
        ReflectionTestUtils.setField(report, "summary", summary)
        return true
    }

    override fun findSessionSeqsWithoutSummary(createdBefore: OffsetDateTime) = reports
        .filter { it.summary == null && it.regDttm?.isAfter(createdBefore) == false }
        .sortedBy { it.reportSeq }
        .map { it.sessionSeq }

    override fun findBySessionSeq(sessionSeq: Long) = reports.firstOrNull { it.sessionSeq == sessionSeq }

    /** 정렬은 기본값(endedDttm desc, 세션 번호 desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPage(userSeq: Int?, pageable: Pageable): Page<ReportListEntry> {
        val all = reports.map { report -> ReportListEntry(sessions.first { it.sessionSeq == report.sessionSeq }, report) }
            .filter { userSeq == null || it.session.userSeq == userSeq }
            .sortedWith(compareByDescending<ReportListEntry> { it.session.endedDttm }.thenByDescending { it.session.sessionSeq })
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

    val users = mutableMapOf<Int, UserInfo>()

    override fun findUsers(userSeqs: Collection<Int>) = userSeqs.mapNotNull { users[it] }.associateBy { it.userSeq }

    override fun endActiveWalks(sessionSeq: Long, endedAt: OffsetDateTime) = if (activeWalks.remove(sessionSeq) != null) 1 else 0

    // ===== 돌발 이벤트 =====
    val scenarios = mutableMapOf<Long, EventScenario>()
    val occurrences = mutableListOf<EventOccurrence>()

    override fun findByScenarioSeq(scenarioSeq: Long) = scenarios[scenarioSeq]

    override fun findByScenarioSeqs(scenarioSeqs: Collection<Long>) = scenarioSeqs.mapNotNull { scenarios[it] }.associateBy { it.scenarioSeq }

    override fun findActive(context: TriggerContext, type: TriggerType) = scenarios.values
        .filter { it.isActive && it.triggerContext == context && it.triggerType == type }
        .sortedBy { it.scenarioSeq }

    override fun save(occurrence: EventOccurrence) = occurrence.also {
        if (it.occurrenceSeq == null) {
            ReflectionTestUtils.setField(it, "occurrenceSeq", 90000L + occurrences.size + 1)
            occurrences += it
        }
    }

    override fun findPending(sessionSeq: Long) = occurrences.firstOrNull { it.sessionSeq == sessionSeq && it.isPending }

    override fun countOccurredSince(sessionSeq: Long, context: TriggerContext, type: TriggerType, since: OffsetDateTime) =
        occurrences.count { o ->
            val scenario = scenarios.getValue(o.scenarioSeq)
            o.sessionSeq == sessionSeq && !o.occurredDttm.isBefore(since) && scenario.triggerContext == context && scenario.triggerType == type
        }

    override fun findLastOccurredAt(sessionSeq: Long, scenarioSeq: Long) =
        occurrences.filter { it.sessionSeq == sessionSeq && it.scenarioSeq == scenarioSeq }.maxOfOrNull { it.occurredDttm }

    /** 정렬은 기본값(최근 발생 먼저 = PK desc)만 흉내 낸다 */
    override fun findPage(sessionSeq: Long, status: OccurrenceStatus?, pageable: Pageable): Page<EventOccurrence> {
        val all = occurrences.filter { it.sessionSeq == sessionSeq && (status == null || it.status == status) }.sortedByDescending { it.occurrenceSeq }
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

    override fun countEvents(sessionSeq: Long): EventCounts {
        val closed = occurrences.filter { it.sessionSeq == sessionSeq && !it.isPending }
        return EventCounts(total = closed.size, resolved = closed.count { it.status == OccurrenceStatus.RESOLVED })
    }

    /** 시드와 같은 번호·정답의 시나리오 몇 개 */
    fun stockScenarios() {
        listOf(
            EventScenario(
                1, "물그릇을 엎었어요", "목이 마를 텐데", TriggerContext.HOME, TriggerType.RANDOM, ActionCode.WATER, weight = 2,
                resolveEffects = mapOf("hydration" to 10, "calmness" to 5), resultText = "시원하게 마셨어요.",
                neglectEffects = mapOf("hydration" to -20, "calmness" to -5), neglectText = "물을 채워 줬어야 해요.",
            ),
            EventScenario(
                5, "쿠션을 물어뜯어요", "심심해 보여요", TriggerContext.HOME, TriggerType.RANDOM, ActionCode.PLAY, speciesScope = SpeciesScope.DOG,
                resolveEffects = mapOf("bond" to 5, "calmness" to 10, "satiety" to -5, "energy" to -10), resultText = "신나게 놀았어요.",
                neglectEffects = mapOf("calmness" to -15, "bond" to -5), neglectText = "놀아 줬어야 해요.",
            ),
            EventScenario(
                7, "헤어볼을 토했어요", "털이 많이 빠져요", TriggerContext.HOME, TriggerType.RANDOM, ActionCode.BATHE, speciesScope = SpeciesScope.CAT,
                resolveEffects = mapOf("cleanliness" to 5, "health" to 5, "calmness" to -5), neglectEffects = mapOf("health" to -10),
            ),
            EventScenario(
                8, "축 처져 있어요", "평소와 달라요", TriggerContext.HOME, TriggerType.CONDITION, ActionCode.VET_VISIT,
                triggerCondition = mapOf("stat" to "health", "op" to "<", "value" to 40), cooldownHours = 24, resolveCost = 30_000, triggerProbability = java.math.BigDecimal("0.50"),
                resolveEffects = mapOf("health" to 30, "calmness" to -10), resultText = "진단서: 가벼운 장염", neglectEffects = mapOf("health" to -15),
            ),
            EventScenario(
                12, "불안해서 낑낑대요", "안절부절못해요", TriggerContext.HOME, TriggerType.CONDITION, ActionCode.PLAY,
                triggerCondition = mapOf("stat" to "calmness", "op" to "<", "value" to 30), cooldownHours = 12,
                triggerProbability = java.math.BigDecimal("0.50"),
                resolveEffects = mapOf("calmness" to 15, "bond" to 3, "energy" to -5), neglectEffects = mapOf("calmness" to -10, "health" to -3),
            ),
            EventScenario(
                13, "헥헥거려요", "혀를 내밀어요", TriggerContext.WALK, TriggerType.RANDOM, ActionCode.GIVE_WATER, speciesScope = SpeciesScope.DOG,
                resolveEffects = mapOf("hydration" to 10), neglectEffects = mapOf("hydration" to -15),
            ),
        ).forEach { scenarios[it.scenarioSeq] = it }
    }

    override fun countWalks(sessionSeq: Long) = walkCounts[sessionSeq] ?: 0

    override fun countByTypes(sessionSeq: Long, types: Collection<ActionType>) =
        logs.count { it.sessionSeq == sessionSeq && it.actionType in types }

    override fun findSpends(sessionSeq: Long) = transactions.filter { it.sessionSeq == sessionSeq }.map {
        val key = if (it.transactionType == TransactionType.EVENT) ReportAggregator.EVENT_KEY else shopItems.getValue(it.itemSeq!!).category.name
        SpendRecord(key, it.amount, checkNotNull(it.regDttm) { "테스트 지출에 regDttm이 없습니다" })
    }

    val sessions = mutableListOf<SimulationSession>()
    val logs = mutableListOf<SimulationActionLog>()
    val breeds = mutableMapOf<Int, BreedInfo>()
    val breedLookups = mutableListOf<Int>()
    val activeWalks = mutableMapOf<Long, Long>()
    val reported = mutableSetOf<Long>()
    val shopItems = mutableMapOf<Long, SimulationShopItem>()
    val inventory = mutableListOf<SimulationInventoryItem>()
    val transactions = mutableListOf<SimulationTransaction>()

    override fun findActive(speciesType: SpeciesType?) = shopItems.values
        .filter { it.isActive && (speciesType == null || it.speciesScope.includes(speciesType)) }
        .sortedWith(compareBy<SimulationShopItem> { it.displayOrder }.thenBy { it.itemSeq })

    override fun findByItemSeq(itemSeq: Long) = shopItems[itemSeq]

    override fun save(transaction: SimulationTransaction) = transaction.also {
        ReflectionTestUtils.setField(it, "transactionSeq", 7000L + transactions.size + 1)
        now?.let { at -> ReflectionTestUtils.setField(it, "regDttm", at) }
        transactions += it
    }

    /** 정렬은 기본값(최근 먼저 = PK desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPage(sessionSeq: Long, type: TransactionType?, pageable: Pageable): Page<TransactionEntry> {
        val all = transactions.filter { it.sessionSeq == sessionSeq && (type == null || it.transactionType == type) }
            .sortedByDescending { it.transactionSeq }
            .map { TransactionEntry(it, it.itemSeq?.let(shopItems::getValue)) }
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

    override fun findActiveForUpdate(userSeq: Int) =
        sessions.firstOrNull { it.userSeq == userSeq && it.status == SessionStatus.IN_PROGRESS }

    override fun findByIdForUpdate(sessionSeq: Long) = sessions.firstOrNull { it.sessionSeq == sessionSeq }

    override fun findById(sessionSeq: Long) = findByIdForUpdate(sessionSeq)

    override fun findInProgressSessionSeqs() = sessions.filter { it.status == SessionStatus.IN_PROGRESS }.mapNotNull { it.sessionSeq }

    /** 정렬은 기본값(startedDttm desc, PK desc)만 흉내 낸다. 정렬 화이트리스트는 어댑터 테스트에서 본다 */
    override fun findPageByUser(userSeq: Int, status: SessionStatus?, pageable: Pageable): Page<SimulationSession> {
        val all = sessions.filter { it.userSeq == userSeq && (status == null || it.status == status) }
            .sortedWith(compareByDescending<SimulationSession> { it.startedDttm }.thenByDescending { it.sessionSeq })
        val content = all.drop(pageable.offset.toInt()).take(pageable.pageSize)
        return PageImpl(content, pageable, all.size.toLong())
    }

    override fun save(session: SimulationSession) = session.also {
        if (it.sessionSeq == null) {
            ReflectionTestUtils.setField(it, "sessionSeq", 3000L + sessions.size + 1)
            sessions += it
        }
    }

    /** 저장할 로그에 찍을 시각(실제로는 @CreationTimestamp). 테스트가 행동 시각에 맞춰 바꾼다 */
    var now: OffsetDateTime? = null

    override fun save(log: SimulationActionLog) = log.also {
        ReflectionTestUtils.setField(it, "actionLogSeq", 50000L + logs.size + 1)
        now?.let { at -> ReflectionTestUtils.setField(it, "regDttm", at) }
        logs += it
    }

    override fun findLastActionDttm(sessionSeq: Long, actionCode: ActionCode) =
        logs.filter { it.sessionSeq == sessionSeq && it.actionCode == actionCode }.mapNotNull { it.regDttm }.maxOrNull()

    /** 정렬은 기본값(최근 먼저 = PK desc)만 흉내 낸다 */
    override fun findPage(sessionSeq: Long, type: ActionType?, pageable: Pageable): Page<ActionLogEntry> {
        val all = logs.filter { it.sessionSeq == sessionSeq && (type == null || it.actionType == type) }
            .sortedByDescending { it.actionLogSeq }
            .map { ActionLogEntry(it, it.itemSeq?.let(shopItems::getValue)) }
        return PageImpl(all.drop(pageable.offset.toInt()).take(pageable.pageSize), pageable, all.size.toLong())
    }

    override fun findBreed(breedSeq: Int): BreedInfo? {
        breedLookups += breedSeq
        return breeds[breedSeq]
    }

    override fun findBreeds(breedSeqs: Collection<Int>) = breedSeqs.mapNotNull { breeds[it] }.associateBy { it.breedSeq }

    override fun findActiveWalkSeq(sessionSeq: Long) = activeWalks[sessionSeq]

    override fun findSessionSeqsWithReport(sessionSeqs: Collection<Long>) = sessionSeqs.filterTo(mutableSetOf()) { it in reported }

    override fun find(sessionSeq: Long, itemSeq: Long) = inventory.firstOrNull { it.sessionSeq == sessionSeq && it.itemSeq == itemSeq }

    override fun save(inventoryItem: SimulationInventoryItem) = inventoryItem.also {
        if (it.inventoryItemSeq == null) {
            ReflectionTestUtils.setField(it, "inventoryItemSeq", 5000L + inventory.size + 1)
            inventory += it
        }
    }

    override fun findAllWithItems(sessionSeq: Long) = inventory.filter { it.sessionSeq == sessionSeq }
        .map { InventoryEntry(it, shopItems.getValue(it.itemSeq)) }
        .sortedWith(compareBy<InventoryEntry> { it.shopItem.displayOrder }.thenBy { it.shopItem.itemSeq })

    override fun findOwnedByCategory(sessionSeq: Long, category: ItemCategory) =
        findAllWithItems(sessionSeq).filter { it.inventoryItem.quantity > 0 && it.shopItem.category == category }

    override fun findEquipment(sessionSeq: Long) = Equipment.of(
        findAllWithItems(sessionSeq).filter { it.inventoryItem.quantity > 0 && !it.shopItem.isConsumable }.mapTo(mutableSetOf()) { it.shopItem.category },
    )

    /** 시드와 같은 번호·분류의 물품 몇 개 */
    fun stockShop() {
        listOf(
            SimulationShopItem(1, "밥그릇", ItemCategory.FOOD_BOWL, 10_000, SpeciesScope.ALL, displayOrder = 1),
            SimulationShopItem(2, "물그릇", ItemCategory.WATER_BOWL, 10_000, SpeciesScope.ALL, displayOrder = 2),
            SimulationShopItem(4, "강아지 배변판", ItemCategory.LITTER_BOX, 15_000, SpeciesScope.DOG, displayOrder = 4),
            SimulationShopItem(
                10, "위생 관리 키트", ItemCategory.GROOMING_KIT, 13_800, SpeciesScope.ALL,
                effects = mapOf("cleanliness" to 40, "calmness" to -5), displayOrder = 10,
            ),
            SimulationShopItem(11, "탐색 장난감", ItemCategory.TOY, 4_100, SpeciesScope.ALL, effects = mapOf("bond" to 3), displayOrder = 11),
            SimulationShopItem(12, "통통 놀이공", ItemCategory.TOY, 8_900, SpeciesScope.ALL, effects = mapOf("bond" to 4, "calmness" to 3), displayOrder = 12),
            SimulationShopItem(20, "꼬마 쥐 장난감", ItemCategory.TOY, 2_000, SpeciesScope.CAT, effects = mapOf("bond" to 3, "calmness" to 2), displayOrder = 20),
            SimulationShopItem(22, "균형 잡힌 사료", ItemCategory.FOOD, 10_600, SpeciesScope.ALL, effects = mapOf("satiety" to 30), displayOrder = 22),
            SimulationShopItem(
                23, "바삭한 미니 비스킷", ItemCategory.SNACK, 4_500, SpeciesScope.ALL,
                effects = mapOf("satiety" to 5, "bond" to 3, "calmness" to 3), displayOrder = 23,
            ),
            SimulationShopItem(
                27, "동결건조 흰살생선", ItemCategory.SNACK, 13_000, SpeciesScope.CAT,
                effects = mapOf("satiety" to 10, "bond" to 4, "calmness" to 5), displayOrder = 27,
            ),
            SimulationShopItem(30, "배변패드", ItemCategory.LITTER, 2_600, SpeciesScope.DOG, effects = mapOf("cleanliness" to 30), displayOrder = 30),
            SimulationShopItem(
                33, "향긋한 캣닢", ItemCategory.CATNIP, 9_900, SpeciesScope.CAT,
                effects = mapOf("energy" to 15, "bond" to 3, "calmness" to -5), displayOrder = 33,
            ),
            SimulationShopItem(35, "기본 상비약", ItemCategory.MEDICINE, 15_000, SpeciesScope.ALL, effects = mapOf("health" to 20), displayOrder = 35),
            SimulationShopItem(90, "단종된 간식", ItemCategory.SNACK, 1_000, SpeciesScope.ALL, displayOrder = 90, isActive = false),
            SimulationShopItem(
                31, "화장실 모래", ItemCategory.LITTER, 7_200, SpeciesScope.CAT,
                effects = mapOf("cleanliness" to 30), imageKey = "simulation/items/litter.png", displayOrder = 31,
            ),
        ).forEach { shopItems[it.itemSeq] = it }
    }

    fun assembler() = SimulationSessionDetailAssembler(this, this, this, this, this)

    /** 종료 시 발행된 내부 이벤트 */
    val published = mutableListOf<Any>()

    fun reportCalculator(properties: SimulationProperties) = ReportCalculator(this, this, this, this, properties)

    fun terminator(properties: SimulationProperties) =
        SessionTerminator(this, this, this, this, reportCalculator(properties), ApplicationEventPublisher { published += it })

    /** 고정 시각 now 기준 진행(지연 계산·종료 판정) */
    fun progress(properties: SimulationProperties, now: OffsetDateTime) = SessionProgress(
        refresher(properties, now),
        terminator(properties),
        events(properties),
    )

    fun refresher(properties: SimulationProperties, now: OffsetDateTime) =
        SessionStatRefresher(StatCalculator(properties.stats, properties.tick), Clock.fixed(now.toInstant(), ZoneId.of("Asia/Seoul")))

    fun events(properties: SimulationProperties = DEFAULT_PROPERTIES, random: RandomGenerator = ScriptedRandom()) =
        SessionEvents(this, this, this, this, properties, random)

    companion object {
        val MALTESE = BreedInfo(21, "말티즈", CatalogSpeciesCode.DOG, Personality.FRIENDLY, "https://cdn/breeds/maltese.png")
        val KOREAN_SHORTHAIR = BreedInfo(31, "코리안 숏헤어", CatalogSpeciesCode.CAT, Personality.TIMID, null)
        val ETC = BreedInfo(90, "기타", CatalogSpeciesCode.ETC, Personality.TIMID, null)

        /** 이벤트 테스트용 기본 설정(1시간 tick, 7일, 이벤트 설정 기본값) */
        val DEFAULT_PROPERTIES = SimulationProperties(java.time.Duration.ofHours(1), java.time.Duration.ofDays(7), com.ssafy.animal.simulation.domain.StatCalculatorTest.RULES)
    }
}

/**
 * 정해진 값을 차례로 돌려주는 난수(RANDOM 이벤트 테스트용). 값이 떨어지면 nextDouble은 0.999(발생 안 함), nextInt는 0.
 * 기본 생성자는 언제나 발생하지 않는다.
 */
class ScriptedRandom(doubles: List<Double> = emptyList(), ints: List<Int> = emptyList()) : RandomGenerator {
    private val doubles = ArrayDeque(doubles)
    private val ints = ArrayDeque(ints)

    override fun nextLong(): Long = 0L

    override fun nextDouble(): Double = doubles.removeFirstOrNull() ?: 0.999

    override fun nextInt(bound: Int): Int = (ints.removeFirstOrNull() ?: 0).also { require(it in 0 until bound) { "nextInt($bound) 범위 밖: $it" } }
}
